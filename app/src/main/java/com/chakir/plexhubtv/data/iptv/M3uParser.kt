package com.chakir.plexhubtv.data.iptv

import com.chakir.plexhubtv.core.model.IptvChannel
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.util.UUID

object M3uParser {
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
                // Assume URL line
                if (currentName != null) {
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
