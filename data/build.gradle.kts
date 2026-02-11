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
    implementation(project(":core:common"))
    implementation(project(":core:model"))
    implementation(project(":core:network"))
    implementation(project(":core:database"))
    implementation(project(":core:datastore"))
    implementation(project(":domain"))
    implementation("com.google.code.gson:gson:2.11.0") 
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.tv.foundation)
    implementation(libs.androidx.tv.material)
    implementation(libs.androidx.tv.provider)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.foundation)
    debugImplementation(libs.androidx.compose.ui.tooling)
    implementation(libs.androidx.compose.navigation)
    implementation(libs.coil.compose)
    implementation(libs.coil.video)
   
    // Retrofit & OkHttp
    implementation(libs.retrofit)
    implementation(libs.kotlinx.serialization.json)
    implementation("com.jakewharton.retrofit:retrofit2-kotlinx-serialization-converter:1.0.0")
    implementation(libs.okhttp.logging)


     // Room
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    implementation(libs.room.paging)
    ksp(libs.room.compiler)


    // --- HILT (Injection de dépendance) ---
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.hilt.work)
    ksp(libs.hilt.androidx.compiler)
    implementation(libs.androidx.datastore.preferences)
    implementation("androidx.compose.material:material-icons-extended:1.7.8")

       // --- ARCHITECTURE (Paging 3 + Room) ---
    implementation(libs.paging.runtime)
    implementation(libs.paging.compose)
    implementation(libs.androidx.compose.ui.text)

    // Comparison for WorkManager (if repositories trigger workers)
      // WorkManager
    implementation(libs.androidx.work.runtime)
   // --- TEST ---
    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.truth)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation("org.robolectric:robolectric:4.11.1")
}
