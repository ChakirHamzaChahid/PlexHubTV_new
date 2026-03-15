package com.chakir.plexhubtv.feature.player.controller

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages trickplay (BIF) thumbnail data for seek preview.
 *
 * BIF (Base Index Frame) format:
 * - 8 bytes: magic (0x89 0x42 0x49 0x46 0x0d 0x0a 0x1a 0x0a)
 * - 4 bytes: version (uint32 LE)
 * - 4 bytes: image count (uint32 LE)
 * - 4 bytes: interval in ms (uint32 LE)
 * - 44 bytes: reserved
 * - N * 8 bytes: index entries (timestamp uint32 LE + offset uint32 LE)
 * - Sentinel: 0xFFFFFFFF + total file size
 * - JPEG image data at referenced offsets
 */
@Singleton
class TrickplayManager @Inject constructor(
    private val okHttpClient: OkHttpClient,
) {
    private var bifData: ByteArray? = null
    private var bifIndex: List<BifEntry> = emptyList()
    private var bifInterval: Long = 10000L

    private val _isLoaded = MutableStateFlow(false)
    val isLoaded: StateFlow<Boolean> = _isLoaded.asStateFlow()

    private data class BifEntry(val timestampMs: Long, val offset: Int, val size: Int)

    /**
     * Load BIF data for the given media part.
     * Call this after media metadata is loaded.
     */
    suspend fun loadBif(baseUrl: String, token: String, partId: String) {
        clear()
        val url = "${baseUrl.trimEnd('/')}/library/parts/$partId/indexes/sd?X-Plex-Token=$token"

        withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder().url(url).build()
                val response = okHttpClient.newCall(request).execute()

                if (!response.isSuccessful) {
                    Timber.d("TrickplayManager: BIF not available (HTTP ${response.code})")
                    return@withContext
                }

                val bytes = response.body?.bytes() ?: return@withContext
                if (bytes.size < 64) {
                    Timber.d("TrickplayManager: BIF too small (${bytes.size} bytes)")
                    return@withContext
                }

                // Validate magic bytes
                if (bytes[0] != 0x89.toByte() || bytes[1] != 0x42.toByte() ||
                    bytes[2] != 0x49.toByte() || bytes[3] != 0x46.toByte()
                ) {
                    Timber.d("TrickplayManager: Invalid BIF magic")
                    return@withContext
                }

                val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
                buffer.position(8) // Skip magic
                val version = buffer.int
                val imageCount = buffer.int
                val intervalMs = buffer.int.toLong()

                if (imageCount <= 0 || intervalMs <= 0) {
                    Timber.d("TrickplayManager: Invalid BIF header (count=$imageCount, interval=$intervalMs)")
                    return@withContext
                }

                bifInterval = intervalMs

                // Skip reserved (44 bytes) -> position is now at 20 + 44 = 64
                buffer.position(64)

                // Read index entries
                val entries = mutableListOf<Pair<Long, Int>>() // (timestampMs, offset)
                for (i in 0..imageCount) { // imageCount + 1 entries (includes sentinel)
                    if (buffer.remaining() < 8) break
                    val ts = buffer.int.toLong() and 0xFFFFFFFFL
                    val offset = buffer.int
                    entries.add(ts * 1000L to offset) // Convert seconds to ms
                }

                // Build index with sizes
                val index = mutableListOf<BifEntry>()
                for (i in 0 until entries.size - 1) {
                    val (ts, offset) = entries[i]
                    val nextOffset = entries[i + 1].second
                    if (ts == 0xFFFFFFFFL * 1000L) break // Sentinel
                    index.add(BifEntry(ts, offset, nextOffset - offset))
                }

                bifData = bytes
                bifIndex = index
                _isLoaded.value = true

                Timber.d("TrickplayManager: Loaded ${index.size} BIF frames (interval=${intervalMs}ms)")
            } catch (e: Exception) {
                Timber.d(e, "TrickplayManager: Failed to load BIF")
            }
        }
    }

    /**
     * Get a Bitmap thumbnail for the given playback position.
     * Returns null if BIF data is not loaded or position is out of range.
     */
    fun getFrameBitmap(positionMs: Long): Bitmap? {
        val data = bifData ?: return null
        val index = bifIndex.ifEmpty { return null }

        // Find the closest frame at or before the position
        val entry = index.lastOrNull { it.timestampMs <= positionMs } ?: index.firstOrNull() ?: return null

        return try {
            BitmapFactory.decodeByteArray(data, entry.offset, entry.size)
        } catch (e: Exception) {
            Timber.d(e, "TrickplayManager: Failed to decode frame at ${entry.timestampMs}ms")
            null
        }
    }

    /**
     * Check if trickplay data is available for the current media.
     */
    fun hasData(): Boolean = bifData != null && bifIndex.isNotEmpty()

    /**
     * Clear all trickplay data (call on media change or release).
     */
    fun clear() {
        bifData = null
        bifIndex = emptyList()
        _isLoaded.value = false
    }
}
