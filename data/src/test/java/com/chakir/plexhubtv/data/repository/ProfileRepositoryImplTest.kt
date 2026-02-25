package com.chakir.plexhubtv.data.repository

import com.chakir.plexhubtv.core.database.ProfileDao
import com.chakir.plexhubtv.core.database.ProfileEntity
import com.chakir.plexhubtv.core.model.AgeRating
import com.chakir.plexhubtv.core.model.Profile
import com.chakir.plexhubtv.core.model.VideoQuality
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class ProfileRepositoryImplTest {

    private lateinit var profileDao: ProfileDao
    private lateinit var repository: ProfileRepositoryImpl

    private val testProfile = Profile(
        id = "test-id",
        name = "Test User",
        avatarEmoji = "😊",
        isKidsProfile = false,
        ageRating = AgeRating.GENERAL,
        autoPlayNext = true,
        preferredQuality = VideoQuality.AUTO,
        createdAt = System.currentTimeMillis(),
        lastUsed = System.currentTimeMillis(),
        isActive = false
    )

    private val testProfileEntity = ProfileEntity(
        id = "test-id",
        name = "Test User",
        avatarUrl = null,
        avatarEmoji = "😊",
        isKidsProfile = false,
        ageRating = "GENERAL",
        autoPlayNext = true,
        preferredAudioLanguage = null,
        preferredSubtitleLanguage = null,
        preferredQuality = "AUTO",
        createdAt = System.currentTimeMillis(),
        lastUsed = System.currentTimeMillis(),
        isActive = false
    )

    @Before
    fun setup() {
        profileDao = mockk(relaxed = true)
        repository = ProfileRepositoryImpl(profileDao)
    }

    @Test
    fun `getAllProfiles returns flow of profiles`() = runTest {
        // Given
        every { profileDao.getAllProfiles() } returns flowOf(listOf(testProfileEntity))

        // When
        val profiles = repository.getAllProfiles().first()

        // Then
        assertThat(profiles).hasSize(1)
        assertThat(profiles.first().id).isEqualTo("test-id")
        assertThat(profiles.first().name).isEqualTo("Test User")
    }

    @Test
    fun `getProfileById returns profile when exists`() = runTest {
        // Given
        coEvery { profileDao.getProfileById("test-id") } returns testProfileEntity

        // When
        val profile = repository.getProfileById("test-id")

        // Then
        assertThat(profile).isNotNull()
        assertThat(profile?.id).isEqualTo("test-id")
        assertThat(profile?.name).isEqualTo("Test User")
    }

    @Test
    fun `getProfileById returns null when not exists`() = runTest {
        // Given
        coEvery { profileDao.getProfileById("non-existent") } returns null

        // When
        val profile = repository.getProfileById("non-existent")

        // Then
        assertThat(profile).isNull()
    }

    @Test
    fun `getActiveProfile returns active profile`() = runTest {
        // Given
        val activeProfile = testProfileEntity.copy(isActive = true)
        coEvery { profileDao.getActiveProfile() } returns activeProfile

        // When
        val profile = repository.getActiveProfile()

        // Then
        assertThat(profile).isNotNull()
        assertThat(profile?.isActive).isTrue()
    }

    @Test
    fun `createProfile inserts profile successfully`() = runTest {
        // Given
        coEvery { profileDao.insertProfile(any()) } returns Unit

        // When
        val result = repository.createProfile(testProfile)

        // Then
        assertThat(result.isSuccess).isTrue()
        coVerify { profileDao.insertProfile(any()) }
    }

    @Test
    fun `updateProfile updates profile successfully`() = runTest {
        // Given
        coEvery { profileDao.updateProfile(any()) } returns Unit

        // When
        val result = repository.updateProfile(testProfile)

        // Then
        assertThat(result.isSuccess).isTrue()
        coVerify { profileDao.updateProfile(any()) }
    }

    @Test
    fun `deleteProfile fails when trying to delete active profile`() = runTest {
        // Given
        val activeProfile = testProfile.copy(isActive = true)
        coEvery { profileDao.getActiveProfile() } returns testProfileEntity.copy(isActive = true)

        // When
        val result = repository.deleteProfile("test-id")

        // Then
        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()?.message).contains("Cannot delete active profile")
        coVerify(exactly = 0) { profileDao.deleteProfileById(any()) }
    }

    @Test
    fun `deleteProfile fails when only one profile exists`() = runTest {
        // Given
        coEvery { profileDao.getActiveProfile() } returns null
        coEvery { profileDao.getProfileCount() } returns 1

        // When
        val result = repository.deleteProfile("test-id")

        // Then
        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()?.message).contains("Cannot delete the last profile")
        coVerify(exactly = 0) { profileDao.deleteProfileById(any()) }
    }

    @Test
    fun `deleteProfile succeeds when conditions are met`() = runTest {
        // Given
        coEvery { profileDao.getActiveProfile() } returns testProfileEntity.copy(id = "other-id")
        coEvery { profileDao.getProfileCount() } returns 2
        coEvery { profileDao.deleteProfileById("test-id") } returns Unit

        // When
        val result = repository.deleteProfile("test-id")

        // Then
        assertThat(result.isSuccess).isTrue()
        coVerify { profileDao.deleteProfileById("test-id") }
    }

    @Test
    fun `switchProfile activates profile successfully`() = runTest {
        // Given
        coEvery { profileDao.getProfileById("test-id") } returns testProfileEntity
        coEvery { profileDao.deactivateAllProfiles() } returns Unit
        coEvery { profileDao.activateProfile("test-id", any()) } returns Unit

        // When
        val result = repository.switchProfile("test-id")

        // Then
        assertThat(result.isSuccess).isTrue()
        assertThat(result.getOrNull()?.isActive).isTrue()
        coVerify { profileDao.deactivateAllProfiles() }
        coVerify { profileDao.activateProfile("test-id", any()) }
    }

    @Test
    fun `switchProfile fails when profile not found`() = runTest {
        // Given
        coEvery { profileDao.getProfileById("non-existent") } returns null

        // When
        val result = repository.switchProfile("non-existent")

        // Then
        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()?.message).contains("Profile not found")
        coVerify(exactly = 0) { profileDao.deactivateAllProfiles() }
    }

    @Test
    fun `ensureDefaultProfile creates default when no profiles exist`() = runTest {
        // Given
        coEvery { profileDao.getProfileCount() } returns 0
        coEvery { profileDao.insertProfile(any()) } returns Unit

        // When
        val profile = repository.ensureDefaultProfile()

        // Then
        assertThat(profile.name).isEqualTo("Default")
        assertThat(profile.isActive).isTrue()
        coVerify { profileDao.insertProfile(any()) }
    }

    @Test
    fun `ensureDefaultProfile returns active profile when exists`() = runTest {
        // Given
        val activeProfile = testProfileEntity.copy(isActive = true)
        coEvery { profileDao.getProfileCount() } returns 1
        coEvery { profileDao.getActiveProfile() } returns activeProfile

        // When
        val profile = repository.ensureDefaultProfile()

        // Then
        assertThat(profile.id).isEqualTo("test-id")
        assertThat(profile.isActive).isTrue()
        coVerify(exactly = 0) { profileDao.insertProfile(any()) }
    }
}
