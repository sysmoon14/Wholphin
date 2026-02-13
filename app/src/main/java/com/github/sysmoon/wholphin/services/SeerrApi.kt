package com.github.sysmoon.wholphin.services

import com.github.sysmoon.wholphin.api.seerr.SeerrApiClient
import com.github.sysmoon.wholphin.ui.isNotNullOrBlank
import okhttp3.OkHttpClient
import timber.log.Timber

/**
 * Wrapper for [SeerrApiClient]. In most cases, you should use [SeerrService] instead.
 */
class SeerrApi(
    private val okHttpClient: OkHttpClient,
) {
    var api: SeerrApiClient =
        SeerrApiClient(
            baseUrl = "",
            apiKey = null,
            okHttpClient = okHttpClient,
        )
        private set

    val active: Boolean get() = api.baseUrl.isNotNullOrBlank()

    /**
     * Seerr API requires base URL to include /api/v1. Normalize so requests hit
     * e.g. /api/v1/auth/jellyfin instead of /auth/jellyfin (404).
     */
    fun update(
        baseUrl: String,
        apiKey: String?,
    ) {
        val normalized = baseUrl.trimEnd('/').let { u ->
            if (u.endsWith("/api/v1")) u else "$u/api/v1"
        }
        Timber.d(
            "SeerrApi.update: baseUrl=%s apiKey=%s (new client instance)",
            normalized,
            if (apiKey.isNotNullOrBlank()) apiKey else "null",
        )
        api = SeerrApiClient(normalized, apiKey, okHttpClient)
    }
}
