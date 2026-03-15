package com.chakir.plexhubtv.core.model

/**
 * A single frame from a BIF (Base Index Frame) file.
 * Used for seek preview thumbnails in the player.
 */
data class TrickplayFrame(
    val timestampMs: Long,
    val imageBytes: ByteArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is TrickplayFrame) return false
        return timestampMs == other.timestampMs
    }

    override fun hashCode(): Int = timestampMs.hashCode()
}
