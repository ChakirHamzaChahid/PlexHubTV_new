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
    suspend operator fun invoke(accountId: String): Result<Unit> {
        val account = accountRepo.getAccount(accountId)
            ?: return Result.failure(IllegalArgumentException("Xtream account $accountId not found"))

        if (account.status != XtreamAccountStatus.Active) {
            return Result.failure(
                IllegalStateException("Xtream account ${account.label} is ${account.status.name}")
            )
        }

        return runCatching {
            val moviesResult = vodRepo.syncMovies(accountId)
            val seriesResult = seriesRepo.syncSeries(accountId)

            val movieCount = moviesResult.getOrDefault(0)
            val seriesCount = seriesResult.getOrDefault(0)
            Timber.i("XTREAM [Sync] account=${account.label}: $movieCount movies, $seriesCount series synced")

            // Propagate first failure if any
            moviesResult.getOrThrow()
            seriesResult.getOrThrow()
        }
    }
}
