package com.chakir.plexhubtv.feature.appprofile

import com.chakir.plexhubtv.core.model.Profile
import com.chakir.plexhubtv.domain.repository.ProfileRepository
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AppProfileViewModelTest {
    private lateinit var viewModel: AppProfileViewModel
    private lateinit var profileRepository: ProfileRepository

    private val testDispatcher = StandardTestDispatcher()

    private val defaultProfile = Profile(
        id = "default",
        name = "Default",
        isActive = true,
    )

    private val kidsProfile = Profile(
        id = "kids",
        name = "Kids",
        isKidsProfile = true,
        isActive = false,
    )

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        profileRepository = mockk(relaxed = true)
        every { profileRepository.getAllProfiles() } returns flowOf(listOf(defaultProfile, kidsProfile))
        every { profileRepository.getActiveProfileFlow() } returns flowOf(defaultProfile)
        coEvery { profileRepository.ensureDefaultProfile() } returns defaultProfile
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `init loads profiles and active profile`() = runTest {
        viewModel = AppProfileViewModel(profileRepository)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.isLoading).isFalse()
        assertThat(state.profiles).hasSize(2)
        assertThat(state.activeProfile).isEqualTo(defaultProfile)
        assertThat(state.error).isNull()
    }

    @Test
    fun `SelectProfile switches profile`() = runTest {
        coEvery { profileRepository.switchProfile("kids") } returns Result.success(kidsProfile)

        viewModel = AppProfileViewModel(profileRepository)
        advanceUntilIdle()

        viewModel.onAction(AppProfileAction.SelectProfile(kidsProfile))
        advanceUntilIdle()

        coVerify { profileRepository.switchProfile("kids") }
    }

    @Test
    fun `SelectProfile handles error`() = runTest {
        coEvery { profileRepository.switchProfile("kids") } returns Result.failure(Exception("Cannot switch"))

        viewModel = AppProfileViewModel(profileRepository)
        advanceUntilIdle()

        viewModel.onAction(AppProfileAction.SelectProfile(kidsProfile))
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.error).isNotNull()
    }

    @Test
    fun `DeleteProfile calls repository`() = runTest {
        coEvery { profileRepository.deleteProfile("kids") } returns Result.success(Unit)

        viewModel = AppProfileViewModel(profileRepository)
        advanceUntilIdle()

        viewModel.onAction(AppProfileAction.DeleteProfile("kids"))
        advanceUntilIdle()

        coVerify { profileRepository.deleteProfile("kids") }
    }

    @Test
    fun `DismissDialog clears dialog state`() = runTest {
        viewModel = AppProfileViewModel(profileRepository)
        advanceUntilIdle()

        viewModel.onAction(AppProfileAction.CreateProfile)
        assertThat(viewModel.uiState.value.showCreateDialog).isTrue()

        viewModel.onAction(AppProfileAction.DismissDialog)
        assertThat(viewModel.uiState.value.showCreateDialog).isFalse()
    }
}
