package com.chakir.plexhubtv.data.iptv

import android.net.Uri
import com.chakir.plexhubtv.core.model.IptvChannel
import timber.log.Timber
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.util.UUID

object M3uParser {

    private val ALLOWED_STREAM_SCHEMES = setOf("http", "https", "rtsp", "rtp")

    fun parse(inputStream: InputStream): List<IptvChannel> {
        val channels = mutableListOf<IptvChannel>()
        val reader = BufferedReader(InputStreamReader(inputStream))

        var currentTvgId: String? = null
        var currentTvgLogo: String? = null
        var currentGroupTitle: String? = null
        var currentName: String? = null

        reader.forEachLine { line ->
            val trimmed = line.trim()
            if (trimmed.startsWith("#EXTINF:")) {
                // Parse attributes
                currentTvgId = extractAttribute(trimmed, "tvg-id")
                currentTvgLogo = extractAttribute(trimmed, "tvg-logo")
                currentGroupTitle = extractAttribute(trimmed, "group-title")

                // Extract Name (after the last comma)
                val lastCommaIndex = trimmed.lastIndexOf(',')
                if (lastCommaIndex != -1) {
                    currentName = trimmed.substring(lastCommaIndex + 1).trim()
                } else {
                    currentName = "Unknown Channel"
                }
            } else if (trimmed.isNotEmpty() && !trimmed.startsWith("#")) {
                // Assume URL line — validate scheme before accepting
                if (currentName != null && isAllowedStreamUrl(trimmed)) {
                    val channel =
                        IptvChannel(
                            id = currentTvgId ?: UUID.randomUUID().toString(),
                            name = currentName ?: "Unknown Channel",
                            logoUrl = currentTvgLogo,
                            group = currentGroupTitle,
                            streamUrl = trimmed,
                        )
                    channels.add(channel)
                }
                // Reset for next channel
                currentTvgId = null
                currentTvgLogo = null
                currentGroupTitle = null
                currentName = null
            }
        }

        return channels
    }

    private fun isAllowedStreamUrl(url: String): Boolean {
        val scheme = Uri.parse(url).scheme?.lowercase()
        if (scheme == null || scheme !in ALLOWED_STREAM_SCHEMES) {
            Timber.w("M3uParser: Rejected URL with disallowed scheme '$scheme': ${url.take(80)}")
            return false
        }
        return true
    }

    private fun extractAttribute(
        line: String,
        attribute: String,
    ): String? {
        val pattern = "$attribute=\"([^\"]*)\""
        val regex = Regex(pattern)
        val match = regex.find(line)
        return match?.groupValues?.get(1)
    }
}
