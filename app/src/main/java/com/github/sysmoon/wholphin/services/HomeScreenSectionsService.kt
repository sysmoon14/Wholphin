package com.github.sysmoon.wholphin.services

import android.content.Context
import com.github.sysmoon.wholphin.R
import com.github.sysmoon.wholphin.data.model.BaseItem
import com.github.sysmoon.wholphin.services.hilt.AuthOkHttpClient
import com.github.sysmoon.wholphin.ui.SlimItemFields
import com.github.sysmoon.wholphin.util.HomeRowLoadingState
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.api.client.extensions.libraryApi
import org.jellyfin.sdk.api.client.extensions.suggestionsApi
import org.jellyfin.sdk.api.client.extensions.userLibraryApi
import org.jellyfin.sdk.api.client.util.AuthorizationHeaderBuilder
import org.jellyfin.sdk.model.ClientInfo
import org.jellyfin.sdk.model.DeviceInfo
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.SortOrder
import org.jellyfin.sdk.model.api.request.GetItemsRequest
import org.jellyfin.sdk.model.api.request.GetSimilarItemsRequest
import org.jellyfin.sdk.model.api.request.GetSuggestionsRequest
import org.jellyfin.sdk.model.serializer.toUUIDOrNull
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Service for fetching custom home screen sections from the Wholphin Companion plugin.
 * Falls back gracefully if the plugin is not available.
 */
@Singleton
class HomeScreenSectionsService
    @Inject
    constructor(
        private val api: ApiClient,
        @param:AuthOkHttpClient private val okHttpClient: OkHttpClient,
        private val clientInfo: ClientInfo,
        private val deviceInfo: DeviceInfo,
        private val latestNextUpService: LatestNextUpService,
        @param:ApplicationContext private val context: Context,
    ) {
        private val json = Json { ignoreUnknownKeys = true }

        /**
         * Fetches only the row layout from the Wholphin Companion plugin (no content).
         * Returns null if the plugin is not available or not configured.
         * Used to cache layout on first load and refresh only content on subsequent visits.
         */
        suspend fun getLayoutRows(userId: UUID): List<WholphinRow>? =
            withContext(Dispatchers.IO) {
                try {
                    val baseUrl = api.baseUrl
                    val accessToken = api.accessToken
                    if (baseUrl.isNullOrBlank() || accessToken.isNullOrBlank()) return@withContext null
                    val layoutRows = fetchLayoutRows(baseUrl, accessToken, userId)
                    val effectiveLayoutRows =
                        if (layoutRows.isNullOrEmpty()) {
                            fetchLayoutRows(baseUrl, accessToken, null)
                        } else {
                            layoutRows
                        }
                    if (effectiveLayoutRows.isNullOrEmpty()) return@withContext null
                    Timber.d("HomeScreenSectionsService: getLayoutRows returning %s rows", effectiveLayoutRows.size)
                    effectiveLayoutRows
                } catch (ex: Exception) {
                    Timber.e(ex, "HomeScreenSectionsService: getLayoutRows failed")
                    null
                }
            }

        /** NativeRow values that should have their content refreshed on subsequent home visits; other rows keep existing content. */
        private val CONTENT_REFRESHABLE_NATIVE_ROWS = setOf("NextUp", "ContinueWatching", "ContinueWatchingCombined")

        private fun isRowContentRefreshable(row: WholphinRow): Boolean {
            if (row.type?.lowercase() != "system") return false
            val nativeRow = row.endpointParams?.get("NativeRow")?.jsonPrimitive?.contentOrNull ?: return false
            return nativeRow in CONTENT_REFRESHABLE_NATIVE_ROWS
        }

        /**
         * Builds home row content from an existing layout (e.g. cached from first load).
         * Use this on subsequent visits to refresh only content without changing row order or types.
         */
        suspend fun buildRowsFromLayout(
            layoutRows: List<WholphinRow>,
            userId: UUID,
            itemsPerRow: Int,
            enableRewatchingNextUp: Boolean,
        ): List<HomeRowLoadingState> =
            withContext(Dispatchers.IO) {
                layoutRows.mapNotNull { row ->
                    try {
                        buildRow(row, userId, itemsPerRow, enableRewatchingNextUp, isHideWatchedItems(row))
                    } catch (ex: Exception) {
                        Timber.e(ex, "Error building row for type=%s", row.type)
                        val title = resolveRowTitle(row)
                        HomeRowLoadingState.Error(title = title, exception = ex)
                    }
                }
            }

        /**
         * Like [buildRowsFromLayout] but only refreshes content for Next Up, Continue Watching, and Continue Watching (Combined).
         * Other rows keep the corresponding entry from [existingRows]; if none, the row is built once.
         */
        suspend fun buildRowsFromLayoutWithPartialRefresh(
            layoutRows: List<WholphinRow>,
            userId: UUID,
            itemsPerRow: Int,
            enableRewatchingNextUp: Boolean,
            existingRows: List<HomeRowLoadingState>,
        ): List<HomeRowLoadingState> =
            withContext(Dispatchers.IO) {
                layoutRows.mapIndexed { index, row ->
                    if (isRowContentRefreshable(row)) {
                        try {
                            buildRow(row, userId, itemsPerRow, enableRewatchingNextUp, isHideWatchedItems(row))
                                ?: existingRows.getOrNull(index)
                        } catch (ex: Exception) {
                            Timber.e(ex, "Error building row for type=%s", row.type)
                            val title = resolveRowTitle(row)
                            HomeRowLoadingState.Error(title = title, exception = ex)
                        }
                    } else {
                        existingRows.getOrNull(index)
                            ?: try {
                                buildRow(row, userId, itemsPerRow, enableRewatchingNextUp, isHideWatchedItems(row))
                            } catch (ex: Exception) {
                                Timber.e(ex, "Error building row for type=%s", row.type)
                                val title = resolveRowTitle(row)
                                HomeRowLoadingState.Error(title = title, exception = ex)
                            }
                    }
                }.filterNotNull()
            }

        /**
         * Fetches custom home rows from the Wholphin Companion plugin (layout + content).
         * Returns null if the plugin is not available or not configured.
         */
        suspend fun getCustomRows(
            userId: UUID,
            itemsPerRow: Int,
            enableRewatchingNextUp: Boolean,
        ): List<HomeRowLoadingState>? =
            withContext(Dispatchers.IO) {
                try {
                    Timber.d("HomeScreenSectionsService: getCustomRows called for userId=$userId")
                    val layoutRows = getLayoutRows(userId) ?: return@withContext null
                    val rows = buildRowsFromLayout(layoutRows, userId, itemsPerRow, enableRewatchingNextUp)
                    if (rows.isEmpty()) {
                        Timber.w("HomeScreenSectionsService: Layout rows parsed but none resolved to items")
                        return@withContext null
                    }
                    Timber.i("HomeScreenSectionsService: Returning %s custom rows", rows.size)
                    rows
                } catch (ex: Exception) {
                    Timber.e(ex, "HomeScreenSectionsService: Plugin not available or error occurred")
                    null
                }
            }

        private fun fetchLayoutRows(
            baseUrl: String,
            accessToken: String,
            userId: UUID?,
        ): List<WholphinRow>? {
            return try {
                val base = baseUrl.toHttpUrlOrNull()
                if (base == null) {
                    Timber.w("HomeScreenSectionsService: Invalid baseUrl=%s", baseUrl)
                    return null
                }

                val url =
                    base
                        .newBuilder()
                        .addPathSegment("Wholphin")
                        .addPathSegment("Config")
                        .apply {
                            if (userId != null) {
                                addQueryParameter("userId", formatUserId(userId))
                            }
                        }
                        .build()

                Timber.d("HomeScreenSectionsService: Fetching layout from: %s", url)
                val authHeader =
                    AuthorizationHeaderBuilder.buildHeader(
                        clientName = clientInfo.name,
                        clientVersion = clientInfo.version,
                        deviceId = deviceInfo.id,
                        deviceName = deviceInfo.name,
                        accessToken = accessToken,
                    )
                val request =
                    Request
                        .Builder()
                        .url(url)
                        .addHeader("Authorization", authHeader)
                        .addHeader("X-Emby-Token", accessToken)
                        .get()
                        .build()

                okHttpClient.newCall(request).execute().use { response ->
                    Timber.d("HomeScreenSectionsService: Layout response status: %s", response.code)
                    if (!response.isSuccessful) {
                        Timber.w(
                            "HomeScreenSectionsService: Failed to fetch layout, status=%s, message=%s",
                            response.code,
                            response.message,
                        )
                        return null
                    }

                    val jsonString = response.body.string()
                    if (jsonString.isBlank()) {
                        Timber.w("HomeScreenSectionsService: Empty layout response")
                        return null
                    }

                    parseLayoutResponse(jsonString)
                }
            } catch (ex: Exception) {
                Timber.e(ex, "HomeScreenSectionsService: Error fetching layout")
                null
            }
        }

        private fun parseLayoutResponse(jsonString: String): List<WholphinRow>? {
            return try {
                val config = json.decodeFromString(WholphinConfig.serializer(), jsonString)
                val rows = config.layout.flatMap { it.rows }
                if (rows.isNotEmpty()) return rows
                Timber.d("HomeScreenSectionsService: PascalCase parse returned no rows, trying camelCase")
                parseLayoutResponseCamelCase(jsonString)
            } catch (ex: Exception) {
                Timber.d(ex, "HomeScreenSectionsService: PascalCase parse failed, trying camelCase")
                parseLayoutResponseCamelCase(jsonString)
            }
        }

        /** Fallback when plugin returns camelCase (layout, type, title, rows) per integration doc. */
        private fun parseLayoutResponseCamelCase(jsonString: String): List<WholphinRow>? {
            return try {
                val root = json.parseToJsonElement(jsonString).jsonObject
                val layoutEl = root["layout"] ?: root["Layout"] ?: return null
                val sections = layoutEl.jsonArray
                val rows = mutableListOf<WholphinRow>()
                for (sectionEl in sections) {
                    val section = sectionEl.jsonObject
                    val type = (section["type"] ?: section["Type"])?.jsonPrimitive?.contentOrNull
                    val title = (section["title"] ?: section["Title"])?.jsonPrimitive?.contentOrNull
                    val rowsEl = section["rows"] ?: section["Rows"] ?: continue
                    val rowArray = rowsEl.jsonArray
                    for (rowEl in rowArray) {
                        val rowObj = rowEl.jsonObject
                        val rowType = (rowObj["type"] ?: rowObj["Type"])?.jsonPrimitive?.contentOrNull
                        val label = (rowObj["label"] ?: rowObj["Label"])?.jsonPrimitive?.contentOrNull
                        val pluginId = (rowObj["pluginId"] ?: rowObj["PluginId"])?.jsonPrimitive?.contentOrNull
                        val hideWatchedItems =
                            (rowObj["HideWatchedItems"] ?: rowObj["hideWatchedItems"])?.let { el ->
                                when (el) {
                                    is kotlinx.serialization.json.JsonPrimitive -> when (el.contentOrNull) {
                                        "true" -> true
                                        "false" -> false
                                        else -> null
                                    }
                                    else -> null
                                }
                            }
                        val endpointParams = (rowObj["endpointParams"] ?: rowObj["EndpointParams"])?.jsonObject
                        rows.add(
                            WholphinRow(
                                type = rowType,
                                label = label,
                                pluginId = pluginId,
                                hideWatchedItems = hideWatchedItems,
                                endpointParams = endpointParams,
                            ),
                        )
                    }
                }
                if (rows.isEmpty()) {
                    Timber.w("HomeScreenSectionsService: camelCase parse produced no rows")
                    null
                } else {
                    rows
                }
            } catch (ex: Exception) {
                Timber.e(ex, "HomeScreenSectionsService: Error parsing layout response (camelCase)")
                null
            }
        }

        /** Reads HideWatchedItems from row (top-level or inside EndpointParams). */
        private fun isHideWatchedItems(row: WholphinRow): Boolean {
            if (row.hideWatchedItems == true) return true
            val params = row.endpointParams ?: return false
            val v =
                params["HideWatchedItems"]?.jsonPrimitive?.contentOrNull
                    ?: params["hideWatchedItems"]?.jsonPrimitive?.contentOrNull
            return v == "true"
        }

        private suspend fun buildRow(
            row: WholphinRow,
            userId: UUID,
            itemsPerRow: Int,
            enableRewatchingNextUp: Boolean,
            hideWatchedItems: Boolean = false,
        ): HomeRowLoadingState? {
            return when (row.type?.lowercase()) {
                "system" -> buildSystemRow(row, userId, itemsPerRow, enableRewatchingNextUp, hideWatchedItems)
                "collection" -> buildCollectionRow(row, userId, itemsPerRow, hideWatchedItems)
                else -> {
                    Timber.w("HomeScreenSectionsService: Unsupported row type=%s", row.type)
                    null
                }
            }
        }

        private suspend fun buildSystemRow(
            row: WholphinRow,
            userId: UUID,
            itemsPerRow: Int,
            enableRewatchingNextUp: Boolean,
            hideWatchedItems: Boolean = false,
        ): HomeRowLoadingState? {
            val nativeRow = row.endpointParams?.get("NativeRow")?.jsonPrimitive?.contentOrNull
            if (nativeRow.isNullOrBlank()) {
                Timber.w("HomeScreenSectionsService: System row missing NativeRow")
                return null
            }

            val becauseYouWatchedTitle =
                if (nativeRow == "BecauseYouWatched") {
                    buildBecauseYouWatchedTitle(row)
                } else {
                    null
                }
            val title =
                becauseYouWatchedTitle
                    ?: row.label
                        ?.takeIf { it.isNotBlank() }
                        ?.let { label ->
                            if (label.equals("Continue Watching (Combined)", ignoreCase = true)) {
                                context.getString(R.string.continue_watching)
                            } else {
                                label
                            }
                        }
                    ?: defaultTitleForNativeRow(nativeRow)

            val items =
                when (nativeRow) {
                    "ContinueWatching" -> latestNextUpService.getResume(userId, itemsPerRow, true)
                    "NextUp" -> latestNextUpService.getNextUp(userId, itemsPerRow, enableRewatchingNextUp, false)
                    "ContinueWatchingCombined" -> {
                        val resume = latestNextUpService.getResume(userId, itemsPerRow, true)
                        val nextUp = latestNextUpService.getNextUp(userId, itemsPerRow, enableRewatchingNextUp, false)
                        latestNextUpService.buildCombined(resume, nextUp).take(itemsPerRow)
                    }
                    "RecentlyAddedMovies" ->
                        getItemsByType(
                            userId = userId,
                            includeItemTypes = listOf(BaseItemKind.MOVIE),
                            sortBy = ItemSortBy.DATE_CREATED,
                            sortOrder = SortOrder.DESCENDING,
                            limit = itemsPerRow,
                            excludeWatched = hideWatchedItems,
                        )
                    "RecentlyAddedShows" ->
                        getItemsByType(
                            userId = userId,
                            includeItemTypes = listOf(BaseItemKind.EPISODE),
                            sortBy = ItemSortBy.DATE_CREATED,
                            sortOrder = SortOrder.DESCENDING,
                            limit = itemsPerRow,
                            excludeWatched = hideWatchedItems,
                        )
                    "LatestMovies" ->
                        getItemsByType(
                            userId = userId,
                            includeItemTypes = listOf(BaseItemKind.MOVIE),
                            sortBy = ItemSortBy.PREMIERE_DATE,
                            sortOrder = SortOrder.DESCENDING,
                            limit = itemsPerRow,
                            excludeWatched = hideWatchedItems,
                        )
                    "LatestShows" ->
                        getItemsByType(
                            userId = userId,
                            includeItemTypes = listOf(BaseItemKind.SERIES),
                            sortBy = ItemSortBy.PREMIERE_DATE,
                            sortOrder = SortOrder.DESCENDING,
                            limit = itemsPerRow,
                            excludeWatched = hideWatchedItems,
                        )
                    "BecauseYouWatched" -> {
                        val baseItemId =
                            extractBasedOnId(row)
                                ?.let { parseAnyIdToUuidOrNull(it) }
                        if (baseItemId != null) {
                            getSimilarItems(
                                userId = userId,
                                itemId = baseItemId,
                                limit = itemsPerRow,
                                excludeWatched = hideWatchedItems,
                            )
                        } else {
                            getSuggestions(
                                userId = userId,
                                includeItemTypes = listOf(BaseItemKind.MOVIE, BaseItemKind.SERIES),
                                limit = itemsPerRow,
                                excludeWatched = hideWatchedItems,
                            )
                        }
                    }
                    "WatchItAgain" ->
                        getPlayedItems(
                            userId = userId,
                            includeItemTypes = listOf(BaseItemKind.MOVIE, BaseItemKind.SERIES),
                            limit = itemsPerRow,
                        )
                    else -> {
                        Timber.w("HomeScreenSectionsService: Unknown NativeRow=%s", nativeRow)
                        return null
                    }
                }

            if (items.isEmpty()) return null
            return HomeRowLoadingState.Success(title = title, items = items)
        }

        private suspend fun buildCollectionRow(
            row: WholphinRow,
            userId: UUID,
            itemsPerRow: Int,
            hideWatchedItems: Boolean = false,
        ): HomeRowLoadingState? {
            val collectionId = parseAnyIdToUuidOrNull(row.pluginId)
            if (collectionId == null) {
                Timber.w("HomeScreenSectionsService: Collection row missing pluginId")
                val title = row.label?.takeIf { it.isNotBlank() } ?: context.getString(R.string.collection)
                return HomeRowLoadingState.Error(
                    title = title,
                    message = context.getString(R.string.error_loading_collection, title),
                )
            }

            val items =
                getCollectionItems(
                    userId = userId,
                    limit = itemsPerRow,
                    parentId = collectionId,
                    excludeItemIds = listOf(collectionId),
                    excludeWatched = hideWatchedItems,
                )

            val (boxSetName, viewAllItem) = fetchBoxSetViewAll(collectionId)
            val title =
                row.label?.takeIf { it.isNotBlank() }
                    ?: boxSetName
                    ?: context.getString(R.string.collection)

            val finalItems =
                if (viewAllItem != null) {
                    items + viewAllItem
                } else {
                    items
                }

            if (finalItems.isEmpty()) return null
            return HomeRowLoadingState.Success(title = title, items = finalItems)
        }

        private suspend fun fetchBoxSetViewAll(collectionId: UUID): Pair<String?, BaseItem?> {
            return try {
                val boxSetDto = api.userLibraryApi.getItem(collectionId).content
                if (boxSetDto.type != BaseItemKind.BOX_SET) {
                    return null to null
                }
                val viewAllDto = boxSetDto.copy(name = "View All")
                val viewAllItem = BaseItem.from(viewAllDto, api, useSeriesForPrimary = false)
                boxSetDto.name to viewAllItem
            } catch (ex: Exception) {
                Timber.d(ex, "HomeScreenSectionsService: Failed fetching collection for id=%s", collectionId)
                null to null
            }
        }

        private suspend fun getCollectionItems(
            userId: UUID,
            limit: Int,
            parentId: UUID,
            excludeItemIds: List<UUID>? = null,
            excludeWatched: Boolean = false,
        ): List<BaseItem> {
            val request =
                GetItemsRequest(
                    userId = userId,
                    parentId = parentId,
                    excludeItemIds = excludeItemIds,
                    fields = SlimItemFields,
                    recursive = true,
                    enableUserData = true,
                    isPlayed = if (excludeWatched) false else null,
                    startIndex = 0,
                    limit = limit,
                )
            val result = api.itemsApi.getItems(request).content.items
            return result.mapNotNull { dto ->
                try {
                    BaseItem.from(dto, api, true)
                } catch (ex: Exception) {
                    Timber.e(ex, "Error creating BaseItem from collection item dto")
                    null
                }
            }
        }

        private suspend fun getItemsByType(
            userId: UUID,
            includeItemTypes: List<BaseItemKind>,
            sortBy: ItemSortBy,
            sortOrder: SortOrder,
            limit: Int,
            parentId: UUID? = null,
            excludeItemIds: List<UUID>? = null,
            recursive: Boolean = parentId == null,
            excludeWatched: Boolean = false,
        ): List<BaseItem> {
            val request =
                GetItemsRequest(
                    userId = userId,
                    parentId = parentId,
                    includeItemTypes = includeItemTypes.takeIf { it.isNotEmpty() },
                    excludeItemIds = excludeItemIds,
                    fields = SlimItemFields,
                    recursive = recursive,
                    enableUserData = true,
                    isPlayed = if (excludeWatched) false else null,
                    sortBy = listOf(sortBy),
                    sortOrder = listOf(sortOrder),
                    startIndex = 0,
                    limit = limit,
                )
            val result = api.itemsApi.getItems(request).content.items
            return result.mapNotNull { dto ->
                try {
                    BaseItem.from(dto, api, true)
                } catch (ex: Exception) {
                    Timber.e(ex, "Error creating BaseItem from item dto")
                    null
                }
            }
        }

        private suspend fun getSuggestions(
            userId: UUID,
            includeItemTypes: List<BaseItemKind>,
            limit: Int,
            excludeWatched: Boolean = false,
        ): List<BaseItem> {
            val request =
                GetSuggestionsRequest(
                    userId = userId,
                    type = includeItemTypes,
                    startIndex = 0,
                    limit = if (excludeWatched) limit * 2 else limit,
                    enableTotalRecordCount = false,
                )
            val result = api.suggestionsApi.getSuggestions(request).content.items
            var items =
                result.mapNotNull { dto ->
                    try {
                        BaseItem.from(dto, api, true)
                    } catch (ex: Exception) {
                        Timber.e(ex, "Error creating BaseItem from suggestion dto")
                        null
                    }
                }
            if (excludeWatched) {
                items = items.filter { !it.played }.take(limit)
            }
            return items
        }

        private suspend fun getSimilarItems(
            userId: UUID,
            itemId: UUID,
            limit: Int,
            excludeWatched: Boolean = false,
        ): List<BaseItem> {
            val requestLimit = if (excludeWatched) limit * 2 else limit
            val result =
                api.libraryApi
                    .getSimilarItems(
                        GetSimilarItemsRequest(
                            userId = userId,
                            itemId = itemId,
                            fields = SlimItemFields,
                            limit = requestLimit,
                        ),
                    ).content.items
            var items =
                result.mapNotNull { dto ->
                    try {
                        BaseItem.from(dto, api, true)
                    } catch (ex: Exception) {
                        Timber.e(ex, "Error creating BaseItem from similar item dto")
                        null
                    }
                }
            if (excludeWatched) {
                items = items.filter { !it.played }.take(limit)
            }
            return items
        }

        private suspend fun getPlayedItems(
            userId: UUID,
            includeItemTypes: List<BaseItemKind>,
            limit: Int,
        ): List<BaseItem> {
            val request =
                GetItemsRequest(
                    userId = userId,
                    includeItemTypes = includeItemTypes,
                    fields = SlimItemFields,
                    recursive = true,
                    enableUserData = true,
                    isPlayed = true,
                    sortBy = listOf(ItemSortBy.DATE_PLAYED),
                    sortOrder = listOf(SortOrder.DESCENDING),
                    startIndex = 0,
                    limit = limit,
                )
            val result = api.itemsApi.getItems(request).content.items
            return result.mapNotNull { dto ->
                try {
                    BaseItem.from(dto, api, true)
                } catch (ex: Exception) {
                    Timber.e(ex, "Error creating BaseItem from played item dto")
                    null
                }
            }
        }

        private suspend fun buildBecauseYouWatchedTitle(row: WholphinRow): String? {
            val basedOnName = extractBasedOnName(row)
            if (!basedOnName.isNullOrBlank()) {
                return "Because You Watched $basedOnName"
            }

            val basedOnId =
                extractBasedOnId(row)?.let { parseAnyIdToUuidOrNull(it) }
                    ?: return null
            return try {
                val item = api.userLibraryApi.getItem(basedOnId).content
                item.name?.let { "Because You Watched $it" }
            } catch (ex: Exception) {
                Timber.d(ex, "HomeScreenSectionsService: Failed fetching BecauseYouWatched item for id=%s", basedOnId)
                null
            }
        }

        private fun extractBasedOnName(row: WholphinRow): String? {
            val params = row.endpointParams ?: return null
            return listOf(
                "BasedOnName",
                "ItemName",
                "Name",
            ).firstNotNullOfOrNull { key ->
                params[key]?.jsonPrimitive?.contentOrNull?.trim()?.takeIf { it.isNotBlank() }
            }
        }

        private fun extractBasedOnId(row: WholphinRow): String? {
            val params = row.endpointParams ?: return null
            return listOf(
                "ItemId",
                "BasedOnId",
                "BecauseYouWatchedId",
                "BaseItemId",
                "Id",
            ).firstNotNullOfOrNull { key ->
                params[key]?.jsonPrimitive?.contentOrNull?.trim()?.takeIf { it.isNotBlank() }
            }
        }

        private fun resolveRowTitle(row: WholphinRow): String {
            row.label?.takeIf { it.isNotBlank() }?.let { return it }
            val rowType = row.type?.lowercase()
            if (rowType == "system") {
                val nativeRow = row.endpointParams?.get("NativeRow")?.jsonPrimitive?.contentOrNull
                if (!nativeRow.isNullOrBlank()) {
                    return defaultTitleForNativeRow(nativeRow)
                }
            }
            return context.getString(R.string.collection)
        }

        private fun defaultTitleForNativeRow(nativeRow: String): String {
            return when (nativeRow) {
                "ContinueWatching",
                "ContinueWatchingCombined",
                -> context.getString(R.string.continue_watching)
                "NextUp" -> context.getString(R.string.next_up)
                "RecentlyAddedMovies",
                "RecentlyAddedShows",
                "LatestMovies",
                "LatestShows",
                -> context.getString(R.string.recently_added)
                "BecauseYouWatched" -> context.getString(R.string.suggestions)
                else -> nativeRow
            }
        }

        private fun formatUserId(userId: UUID): String = userId.toString().replace("-", "")

        private fun parseAnyIdToUuidOrNull(raw: String?): UUID? {
            val v = raw?.trim().orEmpty()
            if (v.isBlank()) return null

            // First try Jellyfin SDK helper (handles UUID-ish strings).
            v.toUUIDOrNull()?.let { return it }

            // Jellyfin sometimes uses a 32-char hex string without dashes.
            if (v.length == 32 && v.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }) {
                val dashed =
                    buildString(36) {
                        append(v.take(8))
                        append('-')
                        append(v.substring(8, 12))
                        append('-')
                        append(v.substring(12, 16))
                        append('-')
                        append(v.substring(16, 20))
                        append('-')
                        append(v.substring(20, 32))
                    }
                return try {
                    UUID.fromString(dashed)
                } catch (_: IllegalArgumentException) {
                    null
                }
            }

            return null
        }
    }

/** Row definition from the Wholphin Companion plugin layout; used to cache layout and refresh only content on subsequent visits. */
@Serializable
data class WholphinRow(
    @kotlinx.serialization.SerialName("Type")
    val type: String? = null,
    @kotlinx.serialization.SerialName("Label")
    val label: String? = null,
    @kotlinx.serialization.SerialName("PluginId")
    val pluginId: String? = null,
    @kotlinx.serialization.SerialName("HideWatchedItems")
    val hideWatchedItems: Boolean? = null,
    @kotlinx.serialization.SerialName("EndpointParams")
    val endpointParams: JsonObject? = null,
)

@Serializable
private data class WholphinConfig(
    @kotlinx.serialization.SerialName("Layout")
    val layout: List<WholphinSection> = emptyList(),
)

@Serializable
private data class WholphinSection(
    @kotlinx.serialization.SerialName("Type")
    val type: String? = null,
    @kotlinx.serialization.SerialName("Title")
    val title: String? = null,
    @kotlinx.serialization.SerialName("Rows")
    val rows: List<WholphinRow> = emptyList(),
)
