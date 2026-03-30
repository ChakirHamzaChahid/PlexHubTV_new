package com.chakir.plexhubtv

import com.chakir.plexhubtv.core.model.FavoriteActor
import com.chakir.plexhubtv.core.model.Hub
import com.chakir.plexhubtv.core.model.MediaItem
import com.chakir.plexhubtv.core.model.MediaType
import kotlinx.collections.immutable.persistentListOf

/** Reusable mock data factories for UI tests. */
object TestData {

    fun createMovie(
        id: String = "1",
        title: String = "Test Movie",
        year: Int = 2024,
    ) = MediaItem(
        id = id,
        ratingKey = id,
        serverId = "server1",
        title = title,
        type = MediaType.Movie,
        year = year,
        thumbUrl = "",
    )

    fun createShow(
        id: String = "10",
        title: String = "Test Show",
        year: Int = 2023,
    ) = MediaItem(
        id = id,
        ratingKey = id,
        serverId = "server1",
        title = title,
        type = MediaType.Show,
        year = year,
        thumbUrl = "",
    )

    fun createEpisode(
        id: String = "100",
        title: String = "Pilot",
        grandparentTitle: String = "Test Show",
    ) = MediaItem(
        id = id,
        ratingKey = id,
        serverId = "server1",
        title = title,
        type = MediaType.Episode,
        grandparentTitle = grandparentTitle,
        thumbUrl = "",
    )

    fun createHub(
        key: String = "hub.trending",
        title: String = "Trending",
        items: List<MediaItem> = listOf(createMovie()),
    ) = Hub(
        key = key,
        title = title,
        type = "movie",
        hubIdentifier = key,
        items = persistentListOf(*items.toTypedArray()),
    )

    fun createActor(
        tmdbId: Int = 1,
        name: String = "Actor Name",
    ) = FavoriteActor(
        tmdbId = tmdbId,
        name = name,
        photoUrl = null,
        knownFor = "Known for Test Movie",
        addedAt = System.currentTimeMillis(),
    )
}
