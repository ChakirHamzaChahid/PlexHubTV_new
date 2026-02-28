package com.chakir.plexhubtv.domain.usecase

import com.chakir.plexhubtv.core.model.XtreamAccountStatus
import com.chakir.plexhubtv.domain.repository.XtreamAccountRepository
import com.chakir.plexhubtv.domain.repository.XtreamSeriesRepository
import com.chakir.plexhubtv.domain.repository.XtreamVodRepository
import timber.log.Timber
import javax.inject.Inject

class SyncXtreamLibraryUseCase @Inject constructor(
    private val accountRepo: XtreamAccountRepository,
    private val vodRepo: XtreamVodRepository,
    private val seriesRepo: XtreamSeriesRepository,
) {
    /**
     * @param selectedCategoryIds Composite IDs in format "accountId:vod:catId" or "accountId:series:catId".
     *  Empty set = sync everything (backwards compatible).
     */
    suspend operator fun invoke(
        accountId: String,
        selectedCategoryIds: Set<String> = emptySet(),
    ): Result<Unit> {
        val account = accountRepo.getAccount(accountId)
            ?: return Result.failure(IllegalArgumentException("Xtream account $accountId not found"))

        if (account.status != XtreamAccountStatus.Active) {
            return Result.failure(
                IllegalStateException("Xtream account ${account.label} is ${account.status.name}")
            )
        }

        // Parse selected categories for this account
        val vodCatIds = selectedCategoryIds
            .filter { it.startsWith("$accountId:vod:") }
            .map { it.substringAfterLast(":").toInt() }
        val seriesCatIds = selectedCategoryIds
            .filter { it.startsWith("$accountId:series:") }
            .map { it.substringAfterLast(":").toInt() }

        val hasSelection = vodCatIds.isNotEmpty() || seriesCatIds.isNotEmpty()

        return runCatching {
            var totalMovies = 0
            var totalSeries = 0

            if (!hasSelection) {
                // No selection for this account → sync everything (backwards compatible)
                val moviesResult = vodRepo.syncMovies(accountId)
                val seriesResult = seriesRepo.syncSeries(accountId)
                totalMovies = moviesResult.getOrThrow()
                totalSeries = seriesResult.getOrThrow()
            } else {
                // Sync only selected VOD categories
                for (catId in vodCatIds) {
                    val result = vodRepo.syncMovies(accountId, categoryId = catId)
                    totalMovies += result.getOrThrow()
                }
                // Sync only selected series categories
                for (catId in seriesCatIds) {
                    val result = seriesRepo.syncSeries(accountId, categoryId = catId)
                    totalSeries += result.getOrThrow()
                }
            }

            Timber.i("XTREAM [Sync] account=${account.label}: $totalMovies movies, $totalSeries series synced (filtered=${hasSelection})")
        }
    }
}
