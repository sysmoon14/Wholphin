package com.github.sysmoon.wholphin.api.seerr

import com.github.sysmoon.wholphin.api.seerr.infrastructure.ApiClient
import com.github.sysmoon.wholphin.ui.isNotNullOrBlank
import okhttp3.Call
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import timber.log.Timber

class SeerrApiClient(
    val baseUrl: String,
    private val apiKey: String?,
    okHttpClient: OkHttpClient,
) {
    private val cookieJar = SeerrCookieJar()

    private val client =
        okHttpClient
            .newBuilder()
            .cookieJar(cookieJar)
            .addInterceptor { chain ->
                val request = chain.request()
                val authLog =
                    when {
                        apiKey.isNotNullOrBlank() -> "apiKey=$apiKey"
                        else -> "apiKey=null (using cookies)"
                    }
                Timber.d(
                    "SeerrApiClient: %s %s auth=%s",
                    request.method,
                    request.url,
                    authLog,
                )
                chain.proceed(
                    request
                        .newBuilder()
                        .apply {
                            if (apiKey.isNotNullOrBlank()) header("X-Api-Key", apiKey)
                        }.build(),
                )
            }.build()

    val hasValidCredentials: Boolean
        get() =
            apiKey.isNotNullOrBlank() ||
                cookieJar.hasValidCredentials(baseUrl)

    private fun <T : ApiClient> create(initializer: (String, Call.Factory) -> T): Lazy<T> =
        lazy {
            initializer.invoke(baseUrl, client)
        }

    val authApi by create(::AuthApi)
    val blacklistApi by create(::BlacklistApi)
    val collectionApi by create(::CollectionApi)
    val issueApi by create(::IssueApi)
    val mediaApi by create(::MediaApi)
    val moviesApi by create(::MoviesApi)
    val otherApi by create(::OtherApi)
    val overrideruleApi by create(::OverrideruleApi)
    val personApi by create(::PersonApi)
    val publicApi by create(::PublicApi)
    val requestApi by create(::RequestApi)
    val searchApi by create(::SearchApi)
    val serviceApi by create(::ServiceApi)
    val settingsApi by create(::SettingsApi)
    val tmdbApi by create(::TmdbApi)
    val tvApi by create(::TvApi)
    val usersApi by create(::UsersApi)
    val watchlistApi by create(::WatchlistApi)
}

private class SeerrCookieJar : CookieJar {
    private val cookies = mutableMapOf<String, List<Cookie>>()

    override fun saveFromResponse(
        url: HttpUrl,
        cookies: List<Cookie>,
    ) {
        val sessionCookies = cookies.filter { it.name == "connect.sid" }
        sessionCookies
            .groupBy { it.domain }
            .forEach { (domain, list) ->
                this.cookies[domain] = list
                Timber.d(
                    "SeerrCookieJar: saveFromResponse domain=%s connect.sid saved (count=%d)",
                    domain,
                    list.size,
                )
            }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val list = this.cookies[url.host].orEmpty()
        Timber.d(
            "SeerrCookieJar: loadForRequest host=%s cookiesCount=%d",
            url.host,
            list.size,
        )
        return list
    }

    fun hasValidCredentials(baseUrl: String): Boolean =
        baseUrl.toHttpUrlOrNull()?.host?.let { domain ->
            cookies[domain]?.any { it.expiresAt > System.currentTimeMillis() }
        } == true
}
