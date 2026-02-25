import java.io.FileInputStream
import java.util.Properties
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.hilt)
    alias(libs.plugins.detekt)
    alias(libs.plugins.ktlint)
}

android {
    namespace = "com.chakir.plexhubtv.data"
    compileSdk = 36

    defaultConfig {
        minSdk = 27
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
         debug {
            buildConfigField("String", "API_BASE_URL", "\"http://192.168.0.175:8186/\"")
            val localProperties = Properties()
            val localPropertiesFile = rootProject.file("local.properties")
            if (localPropertiesFile.exists()) {
                localProperties.load(FileInputStream(localPropertiesFile))
            }
            val plexToken = localProperties.getProperty("PLEX_TOKEN") ?: ""
            buildConfigField("String", "PLEX_TOKEN", "\"$plexToken\"")
            val iptvUrl = localProperties.getProperty("IPTV_PLAYLIST_URL") ?: ""
            buildConfigField("String", "IPTV_PLAYLIST_URL", "\"$iptvUrl\"")
            val tmdbApiKey = localProperties.getProperty("TMDB_API_KEY") ?: ""
            buildConfigField("String", "TMDB_API_KEY", "\"$tmdbApiKey\"")
            val omdbApiKey = localProperties.getProperty("OMDB_API_KEY") ?: ""
            buildConfigField("String", "OMDB_API_KEY", "\"$omdbApiKey\"")
        }
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            buildConfigField("String", "API_BASE_URL", "\"https://plex.tv/\"")
            buildConfigField("String", "PLEX_TOKEN", "\"\"")
            buildConfigField("String", "IPTV_PLAYLIST_URL", "\"\"")
            buildConfigField("String", "TMDB_API_KEY", "\"\"")
            buildConfigField("String", "OMDB_API_KEY", "\"\"")
        }
    }

    buildFeatures {
        buildConfig = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    // Clean Architecture: Data layer dependencies only
    implementation(project(":core:common"))
    implementation(project(":core:model"))
    implementation(project(":core:network"))
    implementation(project(":core:database"))
    implementation(project(":core:datastore"))
    implementation(project(":domain"))

    // Core Android
    implementation(libs.androidx.core.ktx)

    // Gson for JSON parsing in mappers
    implementation("com.google.code.gson:gson:2.11.0")

    // Networking (already in core:network, but needed for mappers)
    implementation(libs.kotlinx.serialization.json)

    // Retrofit & Networking
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)


    // Room (already in core:database, but needed for DAOs access)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    implementation(libs.room.paging)

    // Paging 3 (for PagingSource implementations)
    implementation(libs.paging.runtime)
    implementation(libs.paging.common)

    // Hilt (Dependency Injection)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    // WorkManager (for background sync)
    implementation(libs.androidx.work.runtime)
    implementation(libs.hilt.work)
    ksp(libs.hilt.androidx.compiler)

    // Coroutines
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)

    // Timber for logging
    implementation(libs.timber)

    // TvProvider (for TvChannelManager)
    implementation(libs.androidx.tv.provider)

   // --- TEST ---
    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.truth)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation("org.robolectric:robolectric:4.11.1")
}
