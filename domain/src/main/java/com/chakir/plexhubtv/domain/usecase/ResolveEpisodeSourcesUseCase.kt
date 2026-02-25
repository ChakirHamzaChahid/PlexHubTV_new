package com.chakir.plexhubtv.domain.usecase

import com.chakir.plexhubtv.core.model.MediaItem
import com.chakir.plexhubtv.core.model.MediaSource

interface ResolveEpisodeSourcesUseCase {
    suspend operator fun invoke(episode: MediaItem): List<MediaSource>
}
