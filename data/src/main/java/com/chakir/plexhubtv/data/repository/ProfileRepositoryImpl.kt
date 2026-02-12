package com.chakir.plexhubtv.data.repository

import com.chakir.plexhubtv.core.database.ProfileDao
import com.chakir.plexhubtv.core.database.toEntity
import com.chakir.plexhubtv.core.database.toProfile
import com.chakir.plexhubtv.core.model.AgeRating
import com.chakir.plexhubtv.core.model.Profile
import com.chakir.plexhubtv.core.model.VideoQuality
import com.chakir.plexhubtv.domain.repository.ProfileRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import timber.log.Timber
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProfileRepositoryImpl @Inject constructor(
    private val profileDao: ProfileDao
) : ProfileRepository {

    override fun getAllProfiles(): Flow<List<Profile>> {
        return profileDao.getAllProfiles().map { entities ->
            entities.map { it.toProfile() }
        }
    }

    override suspend fun getProfileById(profileId: String): Profile? {
        return try {
            profileDao.getProfileById(profileId)?.toProfile()
        } catch (e: Exception) {
            Timber.e(e, "Failed to get profile by ID: $profileId")
            null
        }
    }

    override suspend fun getActiveProfile(): Profile? {
        return try {
            profileDao.getActiveProfile()?.toProfile()
        } catch (e: Exception) {
            Timber.e(e, "Failed to get active profile")
            null
        }
    }

    override fun getActiveProfileFlow(): Flow<Profile?> {
        return profileDao.getActiveProfileFlow().map { it?.toProfile() }
    }

    override suspend fun createProfile(profile: Profile): Result<Profile> {
        return try {
            profileDao.insertProfile(profile.toEntity())
            Timber.i("Profile created: ${profile.name}")
            Result.success(profile)
        } catch (e: Exception) {
            Timber.e(e, "Failed to create profile: ${profile.name}")
            Result.failure(e)
        }
    }

    override suspend fun updateProfile(profile: Profile): Result<Profile> {
        return try {
            profileDao.updateProfile(profile.toEntity())
            Timber.i("Profile updated: ${profile.name}")
            Result.success(profile)
        } catch (e: Exception) {
            Timber.e(e, "Failed to update profile: ${profile.name}")
            Result.failure(e)
        }
    }

    override suspend fun deleteProfile(profileId: String): Result<Unit> {
        return try {
            // Check if it's the active profile
            val activeProfile = getActiveProfile()
            if (activeProfile?.id == profileId) {
                return Result.failure(Exception("Cannot delete active profile. Switch to another profile first."))
            }

            // Check if it's the last profile
            val profileCount = getProfileCount()
            if (profileCount <= 1) {
                return Result.failure(Exception("Cannot delete the last profile."))
            }

            profileDao.deleteProfileById(profileId)
            Timber.i("Profile deleted: $profileId")
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Failed to delete profile: $profileId")
            Result.failure(e)
        }
    }

    override suspend fun switchProfile(profileId: String): Result<Profile> {
        return try {
            val profile = getProfileById(profileId)
                ?: return Result.failure(Exception("Profile not found"))

            // Deactivate all profiles
            profileDao.deactivateAllProfiles()

            // Activate the selected profile
            profileDao.activateProfile(profileId)

            Timber.i("Switched to profile: ${profile.name}")
            Result.success(profile.copy(isActive = true, lastUsed = System.currentTimeMillis()))
        } catch (e: Exception) {
            Timber.e(e, "Failed to switch profile: $profileId")
            Result.failure(e)
        }
    }

    override suspend fun getProfileCount(): Int {
        return try {
            profileDao.getProfileCount()
        } catch (e: Exception) {
            Timber.e(e, "Failed to get profile count")
            0
        }
    }

    override suspend fun ensureDefaultProfile(): Profile {
        return try {
            // Check if any profiles exist
            val profileCount = getProfileCount()
            if (profileCount == 0) {
                // Create default profile
                val defaultProfile = Profile(
                    id = UUID.randomUUID().toString(),
                    name = "Default",
                    avatarEmoji = "😊",
                    isKidsProfile = false,
                    ageRating = AgeRating.GENERAL,
                    autoPlayNext = true,
                    preferredQuality = VideoQuality.AUTO,
                    createdAt = System.currentTimeMillis(),
                    lastUsed = System.currentTimeMillis(),
                    isActive = true
                )

                profileDao.insertProfile(defaultProfile.toEntity())
                Timber.i("Default profile created")
                defaultProfile
            } else {
                // Return active profile or first profile
                getActiveProfile() ?: run {
                    val profileList = profileDao.getAllProfiles().first()
                    val firstProfile = profileList.firstOrNull()?.toProfile()
                    firstProfile?.let {
                        switchProfile(it.id)
                        it.copy(isActive = true)
                    } ?: throw Exception("No profiles found")
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to ensure default profile")
            throw e
        }
    }
}
