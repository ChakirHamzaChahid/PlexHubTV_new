package com.chakir.plexhubtv.core.common

import timber.log.Timber
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Performance tracker for monitoring latencies across the app.
 * Tracks operations with start/end timestamps and logs detailed metrics.
 *
 * Thread-safety:
 * - [activeOperations] uses ConcurrentHashMap for lock-free reads/writes.
 * - [completedMetrics] uses Collections.synchronizedList; iterations are
 *   guarded by synchronized(completedMetrics).
 * - Individual [OperationMetric] mutations (addCheckpoint / endOperation)
 *   are synchronized on the metric instance to prevent data races.
 */
@Singleton
class PerformanceTracker @Inject constructor() {

    private val activeOperations = ConcurrentHashMap<String, OperationMetric>()
    private val completedMetrics: MutableList<OperationMetric> =
        Collections.synchronizedList(mutableListOf())

    /**
     * Start tracking an operation
     * @param operationId Unique identifier for this operation (e.g., "playback_movie_12345")
     * @param category Category of operation (e.g., "PLAYBACK", "IMAGE_LOAD", "DB_QUERY")
     * @param description Human-readable description
     */
    fun startOperation(
        operationId: String,
        category: String,
        description: String,
        metadata: Map<String, Any> = emptyMap()
    ) {
        val metric = OperationMetric(
            id = operationId,
            category = category,
            description = description,
            startTime = System.currentTimeMillis(),
            metadata = metadata.toMutableMap()
        )
        activeOperations[operationId] = metric

        Timber.d("⏱️ [PERF][START][$category] $description | ID=$operationId | meta=$metadata")
    }

    /**
     * Add a checkpoint to an ongoing operation
     */
    fun addCheckpoint(
        operationId: String,
        checkpointName: String,
        additionalMeta: Map<String, Any> = emptyMap()
    ) {
        val metric = activeOperations[operationId] ?: run {
            Timber.w("⚠️ [PERF] Checkpoint '$checkpointName' for unknown operation $operationId")
            return
        }

        synchronized(metric) {
            val elapsed = System.currentTimeMillis() - metric.startTime
            val checkpoint = Checkpoint(checkpointName, elapsed, additionalMeta)
            metric.checkpoints.add(checkpoint)
        }

        Timber.d("🔹 [PERF][CHECKPOINT][${metric.category}] $checkpointName | ID=$operationId | meta=$additionalMeta")
    }

    /**
     * End tracking an operation and log final metrics
     */
    fun endOperation(
        operationId: String,
        success: Boolean = true,
        errorMessage: String? = null,
        additionalMeta: Map<String, Any> = emptyMap()
    ) {
        val metric = activeOperations.remove(operationId) ?: run {
            Timber.w("⚠️ [PERF] Attempted to end unknown operation $operationId")
            return
        }

        // Snapshot checkpoint data under lock, then log outside lock
        val duration: Long
        val checkpointSnapshot: List<Checkpoint>
        synchronized(metric) {
            metric.endTime = System.currentTimeMillis()
            metric.duration = metric.endTime - metric.startTime
            metric.success = success
            metric.errorMessage = errorMessage
            metric.metadata.putAll(additionalMeta)
            duration = metric.duration
            checkpointSnapshot = metric.checkpoints.toList()
        }

        synchronized(completedMetrics) {
            completedMetrics.add(metric)
        }

        // Log summary
        val status = if (success) "✅ SUCCESS" else "❌ FAILED"
        val error = if (errorMessage != null) " | error=$errorMessage" else ""

        Timber.i("⏱️ [PERF][END][${metric.category}] ${metric.description} | TOTAL=${duration}ms | $status$error")

        // Log detailed breakdown if checkpoints exist
        if (checkpointSnapshot.isNotEmpty()) {
            Timber.d("📊 [PERF][BREAKDOWN][${metric.category}] ID=$operationId:")
            checkpointSnapshot.forEachIndexed { index, checkpoint ->
                val delta = if (index == 0) {
                    checkpoint.elapsedMs
                } else {
                    checkpoint.elapsedMs - checkpointSnapshot[index - 1].elapsedMs
                }
                Timber.d("   ${index + 1}. ${checkpoint.name} @ ${checkpoint.elapsedMs}ms (+${delta}ms) ${checkpoint.metadata}")
            }
        }

        // Log full metadata if present
        if (additionalMeta.isNotEmpty()) {
            Timber.d("📋 [PERF][META][${metric.category}] ID=$operationId: ${metric.metadata}")
        }
    }

    /**
     * Quick helper for measuring a suspend block
     */
    suspend fun <T> measure(
        operationId: String,
        category: String,
        description: String,
        metadata: Map<String, Any> = emptyMap(),
        block: suspend () -> T
    ): T {
        startOperation(operationId, category, description, metadata)
        return try {
            val result = block()
            endOperation(operationId, success = true)
            result
        } catch (e: Exception) {
            endOperation(operationId, success = false, errorMessage = e.message)
            throw e
        }
    }

    /**
     * Get summary of recent operations (for debugging)
     */
    fun getSummary(category: String? = null, limit: Int = 20): List<OperationMetric> {
        synchronized(completedMetrics) {
            return completedMetrics
                .asReversed()
                .let { if (category != null) it.filter { m -> m.category == category } else it }
                .take(limit)
        }
    }

    /**
     * Clear old metrics (keep last 100)
     */
    fun cleanup() {
        synchronized(completedMetrics) {
            if (completedMetrics.size > 100) {
                val excess = completedMetrics.size - 100
                completedMetrics.subList(0, excess).clear()
            }
        }
    }
}

data class OperationMetric(
    val id: String,
    val category: String,
    val description: String,
    val startTime: Long,
    var endTime: Long = 0,
    var duration: Long = 0,
    var success: Boolean = true,
    var errorMessage: String? = null,
    val checkpoints: MutableList<Checkpoint> = mutableListOf(),
    val metadata: MutableMap<String, Any> = mutableMapOf()
)

data class Checkpoint(
    val name: String,
    val elapsedMs: Long,
    val metadata: Map<String, Any> = emptyMap()
)

// Common category constants
object PerfCategory {
    const val PLAYBACK = "PLAYBACK"
    const val IMAGE_LOAD = "IMAGE_LOAD"
    const val DB_QUERY = "DB_QUERY"
    const val NETWORK = "NETWORK"
    const val UI_RENDER = "UI_RENDER"
    const val ENRICHMENT = "ENRICHMENT"
    const val NAVIGATION = "NAVIGATION"
}
