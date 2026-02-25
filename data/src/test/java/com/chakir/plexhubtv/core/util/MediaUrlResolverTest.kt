package com.chakir.plexhubtv.core.util

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MediaUrlResolverTest {
    private val resolver = DefaultMediaUrlResolver()

    @Test
    fun `resolveImageUrl - returns null for empty path`() {
        val result = resolver.resolveImageUrl(null, "http://base.url", "token")
        assertThat(result).isNull()
    }

    @Test
    fun `resolveImageUrl - constructs correct url with token`() {
        val result =
            resolver.resolveImageUrl(
                relativePath = "/library/metadata/1/thumb",
                baseUrl = "http://192.168.1.10:32400",
                token = "xyz123",
            )

        // Result is a transcode URL
        assertThat(result).startsWith("http://192.168.1.10:32400/photo/:/transcode")
        assertThat(result).contains("X-Plex-Token=xyz123")
        // Encoded path should be present
        // /library/metadata/1/thumb -> %2Flibrary%2Fmetadata%2F1%2Fthumb
        assertThat(result).contains("url=%2Flibrary%2Fmetadata%2F1%2Fthumb")
    }

    @Test
    fun `resolveImageUrl - handles absolute urls`() {
        val absolute = "https://image.tmdb.org/t/p/original/image.jpg"
        val result = resolver.resolveImageUrl(absolute, "http://base", "token")

        // No token in absolute URL -> cannot transcode -> return as is
        assertThat(result).isEqualTo(absolute)
    }

    @Test
    fun `resolveImageUrl - cleans double slashes`() {
        val result =
            resolver.resolveImageUrl(
                relativePath = "/path",
                baseUrl = "http://base/",
                token = "token",
            )
        // Should return optimized url
        assertThat(result).startsWith("http://base/photo/:/transcode")
        assertThat(result).contains("url=%2Fpath")
    }
}
