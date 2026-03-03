package com.chakir.plexhubtv.data.repository

import com.chakir.plexhubtv.data.repository.MediaLibraryQueryBuilder.QueryConfig
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MediaLibraryQueryBuilderTest {

    // ── Unified paged query ──

    @Test
    fun `unified paged query contains GROUP BY COALESCE`() {
        val result = MediaLibraryQueryBuilder.buildPagedQuery(unifiedConfig())
        assertThat(result.sql).contains("GROUP BY COALESCE(")
    }

    @Test
    fun `unified paged query contains MAX metadata_score`() {
        val result = MediaLibraryQueryBuilder.buildPagedQuery(unifiedConfig())
        assertThat(result.sql).contains("MAX(media.metadata_score)")
    }

    @Test
    fun `unified paged query contains GROUP_CONCAT`() {
        val result = MediaLibraryQueryBuilder.buildPagedQuery(unifiedConfig())
        assertThat(result.sql).contains("GROUP_CONCAT(media.ratingKey) as ratingKeys")
        assertThat(result.sql).contains("GROUP_CONCAT(media.serverId) as serverIds")
    }

    @Test
    fun `unified paged query binds mediaType`() {
        val result = MediaLibraryQueryBuilder.buildPagedQuery(unifiedConfig(mediaTypeStr = "show"))
        assertThat(result.args).contains("show")
    }

    // ── Non-unified paged query ──

    @Test
    fun `non-unified paged query contains WHERE librarySectionId`() {
        val result = MediaLibraryQueryBuilder.buildPagedQuery(nonUnifiedConfig())
        assertThat(result.sql).contains("WHERE librarySectionId = ?")
    }

    @Test
    fun `non-unified paged query contains ORDER BY MIN pageOffset ASC`() {
        val result = MediaLibraryQueryBuilder.buildPagedQuery(nonUnifiedConfig())
        assertThat(result.sql).contains("ORDER BY MIN(media.pageOffset) ASC")
    }

    @Test
    fun `non-unified paged query binds libraryKey, filter, sortOrder`() {
        val result = MediaLibraryQueryBuilder.buildPagedQuery(
            nonUnifiedConfig(libraryKey = "5", filter = "all", sortOrder = "addedAt:desc"),
        )
        assertThat(result.args[0]).isEqualTo("5")
        assertThat(result.args[1]).isEqualTo("all")
        assertThat(result.args[2]).isEqualTo("addedAt:desc")
    }

    @Test
    fun `non-unified paged query contains GROUP BY for multi-source aggregation`() {
        val result = MediaLibraryQueryBuilder.buildPagedQuery(nonUnifiedConfig())
        assertThat(result.sql).contains("GROUP BY COALESCE(")
    }

    @Test
    fun `non-unified paged query contains GROUP_CONCAT for multi-source tracking`() {
        val result = MediaLibraryQueryBuilder.buildPagedQuery(nonUnifiedConfig())
        assertThat(result.sql).contains("GROUP_CONCAT(media.ratingKey) as ratingKeys")
        assertThat(result.sql).contains("GROUP_CONCAT(media.serverId) as serverIds")
    }

    // ── Genre filter ──

    @Test
    fun `genre filter adds genres LIKE clauses`() {
        val result = MediaLibraryQueryBuilder.buildPagedQuery(
            unifiedConfig(genre = listOf("Action", "Comedy")),
        )
        assertThat(result.sql).contains("genres LIKE ?")
        assertThat(result.sql).contains(" OR ")
        assertThat(result.args).contains("%Action%")
        assertThat(result.args).contains("%Comedy%")
    }

    @Test
    fun `single genre does not add OR`() {
        val result = MediaLibraryQueryBuilder.buildPagedQuery(
            unifiedConfig(genre = listOf("Drama")),
        )
        assertThat(result.sql).contains("AND (genres LIKE ?)")
        assertThat(result.sql).doesNotContain(" OR ")
    }

    @Test
    fun `null genre does not add filter`() {
        val result = MediaLibraryQueryBuilder.buildPagedQuery(unifiedConfig(genre = null))
        assertThat(result.sql).doesNotContain("genres LIKE")
    }

    // ── Server exclusion ──

    @Test
    fun `server exclusion adds NOT IN clause for unified`() {
        val result = MediaLibraryQueryBuilder.buildPagedQuery(
            unifiedConfig(excludedServerIds = listOf("s1", "s2")),
        )
        assertThat(result.sql).contains("AND serverId NOT IN (?,?)")
        assertThat(result.args).contains("s1")
        assertThat(result.args).contains("s2")
    }

    @Test
    fun `server exclusion ignored for non-unified`() {
        val result = MediaLibraryQueryBuilder.buildPagedQuery(
            nonUnifiedConfig(excludedServerIds = listOf("s1")),
        )
        assertThat(result.sql).doesNotContain("NOT IN")
    }

    // ── Server filter ──

    @Test
    fun `server filter adds AND serverId clause for unified`() {
        val result = MediaLibraryQueryBuilder.buildPagedQuery(
            unifiedConfig(selectedServerId = "myServer"),
        )
        assertThat(result.sql).contains("AND serverId = ?")
        assertThat(result.args).contains("myServer")
    }

    @Test
    fun `server filter ignored for non-unified`() {
        val result = MediaLibraryQueryBuilder.buildPagedQuery(
            nonUnifiedConfig(selectedServerId = "myServer"),
        )
        assertThat(result.sql).doesNotContain("AND serverId = ?")
    }

    // ── Search query ──

    @Test
    fun `search query adds title LIKE clause`() {
        val result = MediaLibraryQueryBuilder.buildPagedQuery(
            unifiedConfig(query = "Avatar"),
        )
        assertThat(result.sql).contains("AND title LIKE ?")
        assertThat(result.args).contains("%Avatar%")
    }

    @Test
    fun `null search query does not add filter`() {
        val result = MediaLibraryQueryBuilder.buildPagedQuery(unifiedConfig(query = null))
        assertThat(result.sql).doesNotContain("title LIKE")
    }

    // ── Sort directions ──

    @Test
    fun `unified sort by title ASC`() {
        val result = MediaLibraryQueryBuilder.buildPagedQuery(
            unifiedConfig(baseSort = "title", isDescending = false),
        )
        assertThat(result.sql).contains("ORDER BY title ASC")
    }

    @Test
    fun `unified sort by title DESC`() {
        val result = MediaLibraryQueryBuilder.buildPagedQuery(
            unifiedConfig(baseSort = "title", isDescending = true),
        )
        assertThat(result.sql).contains("ORDER BY title DESC")
    }

    @Test
    fun `unified sort by year includes title secondary sort`() {
        val result = MediaLibraryQueryBuilder.buildPagedQuery(
            unifiedConfig(baseSort = "year", isDescending = true),
        )
        assertThat(result.sql).contains("ORDER BY year DESC, title ASC")
    }

    @Test
    fun `unified sort by rating uses AVG aggregate`() {
        val result = MediaLibraryQueryBuilder.buildPagedQuery(
            unifiedConfig(baseSort = "rating", isDescending = true),
        )
        assertThat(result.sql).contains("ORDER BY AVG(media.displayRating) DESC, title ASC")
    }

    @Test
    fun `unified sort by addedAt uses MAX aggregate`() {
        val result = MediaLibraryQueryBuilder.buildPagedQuery(
            unifiedConfig(baseSort = "addedAt", isDescending = true),
        )
        assertThat(result.sql).contains("ORDER BY MAX(media.addedAt) DESC")
    }

    @Test
    fun `non-unified ignores sort config and uses MIN pageOffset`() {
        val result = MediaLibraryQueryBuilder.buildPagedQuery(
            nonUnifiedConfig(baseSort = "title", isDescending = true),
        )
        assertThat(result.sql).contains("ORDER BY MIN(media.pageOffset) ASC")
        assertThat(result.sql).doesNotContain("title DESC")
    }

    // ── Count query ──

    @Test
    fun `unified count query uses COUNT DISTINCT COALESCE`() {
        val result = MediaLibraryQueryBuilder.buildCountQuery(unifiedConfig())
        assertThat(result.sql).contains("COUNT(DISTINCT COALESCE(")
    }

    @Test
    fun `unified count query uses LEFT JOIN id_bridge`() {
        val result = MediaLibraryQueryBuilder.buildCountQuery(unifiedConfig())
        assertThat(result.sql).contains("LEFT JOIN id_bridge")
    }

    @Test
    fun `non-unified count query uses COUNT star`() {
        val result = MediaLibraryQueryBuilder.buildCountQuery(nonUnifiedConfig())
        assertThat(result.sql).contains("SELECT COUNT(*) FROM media")
    }

    @Test
    fun `count query applies genre filter`() {
        val result = MediaLibraryQueryBuilder.buildCountQuery(
            unifiedConfig(genre = listOf("Horror")),
        )
        assertThat(result.sql).contains("genres LIKE ?")
        assertThat(result.args).contains("%Horror%")
    }

    // ── Index query ──

    @Test
    fun `index query adds alphabet constraint`() {
        val result = MediaLibraryQueryBuilder.buildIndexQuery(unifiedConfig(), "M")
        assertThat(result.sql).contains("AND UPPER(title) < UPPER(?)")
        assertThat(result.args).contains("M")
    }

    @Test
    fun `index query includes count logic plus letter`() {
        val result = MediaLibraryQueryBuilder.buildIndexQuery(
            unifiedConfig(mediaTypeStr = "movie"), "D",
        )
        assertThat(result.sql).contains("COUNT(DISTINCT COALESCE(")
        assertThat(result.sql).contains("UPPER(title) < UPPER(?)")
        assertThat(result.args[0]).isEqualTo("movie")
        assertThat(result.args.last()).isEqualTo("D")
    }

    @Test
    fun `non-unified index query uses COUNT star with letter`() {
        val result = MediaLibraryQueryBuilder.buildIndexQuery(nonUnifiedConfig(), "Z")
        assertThat(result.sql).contains("SELECT COUNT(*) FROM media")
        assertThat(result.sql).contains("UPPER(title) < UPPER(?)")
    }

    // ── Args ordering ──

    @Test
    fun `unified paged query args are in correct order`() {
        val result = MediaLibraryQueryBuilder.buildPagedQuery(
            unifiedConfig(
                mediaTypeStr = "movie",
                genre = listOf("Action"),
                excludedServerIds = listOf("ex1"),
                selectedServerId = "srv1",
                query = "Test",
            ),
        )
        assertThat(result.args).containsExactly(
            "movie",      // WHERE m.type = ?
            "%Action%",   // genres LIKE ?
            "ex1",        // serverId NOT IN (?)
            "srv1",       // serverId = ?
            "%Test%",     // title LIKE ?
        ).inOrder()
    }

    @Test
    fun `non-unified paged query args are in correct order`() {
        val result = MediaLibraryQueryBuilder.buildPagedQuery(
            nonUnifiedConfig(
                libraryKey = "3",
                filter = "all",
                sortOrder = "title:asc",
                genre = listOf("Sci-Fi"),
                query = "Star",
            ),
        )
        assertThat(result.args).containsExactly(
            "3",          // librarySectionId = ?
            "all",        // filter = ?
            "title:asc",  // sortOrder = ?
            "%Sci-Fi%",   // genres LIKE ?
            "%Star%",     // title LIKE ?
        ).inOrder()
    }

    // ── Helpers ──

    private fun unifiedConfig(
        mediaTypeStr: String = "movie",
        genre: List<String>? = null,
        selectedServerId: String? = null,
        excludedServerIds: List<String> = emptyList(),
        query: String? = null,
        baseSort: String = "addedAt",
        isDescending: Boolean = true,
    ) = QueryConfig(
        isUnified = true,
        mediaTypeStr = mediaTypeStr,
        genre = genre,
        selectedServerId = selectedServerId,
        excludedServerIds = excludedServerIds,
        query = query,
        baseSort = baseSort,
        isDescending = isDescending,
    )

    private fun nonUnifiedConfig(
        libraryKey: String = "1",
        filter: String = "all",
        sortOrder: String = "default",
        genre: List<String>? = null,
        selectedServerId: String? = null,
        excludedServerIds: List<String> = emptyList(),
        query: String? = null,
        baseSort: String = "addedAt",
        isDescending: Boolean = true,
    ) = QueryConfig(
        isUnified = false,
        mediaTypeStr = "movie",
        libraryKey = libraryKey,
        filter = filter,
        sortOrder = sortOrder,
        genre = genre,
        selectedServerId = selectedServerId,
        excludedServerIds = excludedServerIds,
        query = query,
        baseSort = baseSort,
        isDescending = isDescending,
    )
}
