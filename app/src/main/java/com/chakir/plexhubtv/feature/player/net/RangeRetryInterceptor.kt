package com.chakir.plexhubtv.feature.player.net

import okhttp3.Interceptor
import okhttp3.Response
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap

/**
 * OkHttp interceptor that handles HTTP 416 (Range Not Satisfiable) errors
 * from Xtream/IPTV servers that don't support byte-range requests.
 *
 * When ExoPlayer's buffer fills up, the loader pauses. If the server closes
 * the idle connection, ExoPlayer reopens with `Range: bytes=X-` to resume.
 * Servers that don't support Range return 416, which ExoPlayer treats as fatal.
 *
 * This interceptor:
 * 1. **Proactively strips Range** for hosts that previously returned 416
 *    (avoids the wasted round-trip on every subsequent request)
 * 2. **Reactively retries** the first 416 from a new host without Range
 *
 * ExoPlayer's OkHttpDataSource handles 200 responses (instead of 206) by
 * skipping bytes internally via `bytesToSkip`.
 */
class RangeRetryInterceptor : Interceptor {
    /** Hosts that returned 416 — never send Range to them again. */
    private val noRangeHosts: MutableSet<String> = ConcurrentHashMap.newKeySet()

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val host = request.url.host

        // Proactive: strip Range for known problematic hosts
        if (host in noRangeHosts && request.header("Range") != null) {
            Timber.d("[RangeRetry] Proactively stripping Range for $host (known no-Range host)")
            val stripped = request.newBuilder()
                .removeHeader("Range")
                .build()
            return chain.proceed(stripped)
        }

        val response = chain.proceed(request)

        // Reactive: first 416 from this host — remember and retry
        if (response.code == 416 && request.header("Range") != null) {
            noRangeHosts.add(host)
            Timber.d("[RangeRetry] Got 416 for $host, blacklisting and retrying without Range header")
            response.close()
            val retry = request.newBuilder()
                .removeHeader("Range")
                .build()
            return chain.proceed(retry)
        }

        return response
    }
}
