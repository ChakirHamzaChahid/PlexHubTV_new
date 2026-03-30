package com.chakir.plexhubtv.di

import com.chakir.plexhubtv.domain.service.AnalyticsService
import com.google.firebase.Firebase
import com.google.firebase.analytics.analytics
import com.google.firebase.analytics.logEvent
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AnalyticsModule {

    @Provides
    @Singleton
    fun provideAnalyticsService(): AnalyticsService = FirebaseAnalyticsService()
}

private class FirebaseAnalyticsService : AnalyticsService {
    override fun logEvent(name: String, params: Map<String, Any>) {
        Firebase.analytics.logEvent(name) {
            params.forEach { (key, value) ->
                when (value) {
                    is String -> param(key, value)
                    is Long -> param(key, value)
                    is Double -> param(key, value)
                    else -> param(key, value.toString())
                }
            }
        }
    }
}
