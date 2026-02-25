package com.chakir.plexhubtv.core.di

import com.google.firebase.crashlytics.FirebaseCrashlytics
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module providing Firebase services for dependency injection.
 *
 * Firebase instances are initialized in [PlexHubApplication.initializeFirebase()]
 * before any injection occurs.
 */
@Module
@InstallIn(SingletonComponent::class)
object FirebaseModule {

    /**
     * Provides the singleton [FirebaseCrashlytics] instance.
     *
     * Crashlytics collection is controlled by [FirebaseCrashlytics.setCrashlyticsCollectionEnabled]
     * in [PlexHubApplication], where it's disabled in DEBUG builds.
     */
    @Provides
    @Singleton
    fun provideFirebaseCrashlytics(): FirebaseCrashlytics {
        return FirebaseCrashlytics.getInstance()
    }
}
