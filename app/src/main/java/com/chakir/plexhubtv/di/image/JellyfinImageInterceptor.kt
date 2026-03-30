package com.chakir.plexhubtv.di.image

import com.chakir.plexhubtv.data.repository.JellyfinClientResolver
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

/**
 * OkHttp interceptor that adds Jellyfin `Authorization` header to image requests.
 *
 * Follows the Wholphin pattern: image URLs contain NO authentication info.
 * Auth is added transparently at the HTTP layer via `Authorization: MediaBrowser Token="..."`.
 *
 * Detects Jellyfin URLs by matching against known server baseUrls
 * stored in [JellyfinClientResolver.findTokenForUrl].
 */
@Singleton
class JellyfinImageInterceptor @Inject constructor(
    private val clientResolver: JellyfinClientResolver,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val url = request.url.toString()

        val token = clientResolver.findTokenForUrl(url)
        if (token != null) {
            val newRequest = request.newBuilder()
                .header("Authorization", "MediaBrowser Token=\"$token\"")
                .build()
            return chain.proceed(newRequest)
        }

        return chain.proceed(request)
    }
}
