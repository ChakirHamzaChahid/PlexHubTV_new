import java.io.FileInputStream
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization") version "2.0.21"
    id("kotlin-parcelize")
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
}

android {
    namespace = "com.chakir.plexhubtv"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.chakir.plexhubtv"
        minSdk = 27
        targetSdk = 34
        versionCode = 1
        versionName = "0.6.1"
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
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true // Ajout de la suppression des ressources
            // buildConfigField("String", "API_BASE_URL", "\"https://api.example.com/\"")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    packaging {

        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "META-INF/DEPENDENCIES"
            excludes += "META-INF/LICENSE"
            excludes += "META-INF/LICENSE.txt"
            excludes += "META-INF/license.txt"
            excludes += "META-INF/NOTICE"
            excludes += "META-INF/NOTICE.txt"
            excludes += "META-INF/notice.txt"
            excludes += "META-INF/ASL2.0"
            excludes += "META-INF/*.kotlin_module"
        }
        jniLibs {
            pickFirsts.add("lib/*/libc++_shared.so")
        }
    }

    splits {
        abi {
            isEnable = true
            reset()
            include("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
            isUniversalApk = true
        }
    }
}

dependencies {
    // --- UI & Compose ---
    implementation(platform("androidx.compose:compose-bom:2026.01.00"))
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.10.0")
    implementation("androidx.tv:tv-foundation:1.0.0-alpha12")
    implementation("androidx.tv:tv-material:1.0.1")
    implementation(libs.androidx.leanback)
    implementation(libs.androidx.tv.provider)
    implementation("androidx.compose.material3:material3")
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.foundation)
    debugImplementation(libs.androidx.compose.ui.tooling)
    implementation(libs.glide)
    implementation("androidx.navigation:navigation-compose:2.9.6")
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("io.coil-kt:coil-video:2.7.0")

    // --- Networking & Data ---
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
    implementation("com.squareup.retrofit2:retrofit:3.0.0")
    implementation(libs.retrofit.converter.gson)
    implementation(libs.gson)
    implementation("com.jakewharton.retrofit:retrofit2-kotlinx-serialization-converter:1.0.0")
    implementation("com.squareup.okhttp3:okhttp:5.3.2")
    implementation("com.squareup.okhttp3:logging-interceptor:5.3.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")

    // --- Player Vidéo ---
    implementation("androidx.media3:media3-exoplayer:1.5.1")
    implementation("androidx.media3:media3-exoplayer-hls:1.5.1")
    implementation("androidx.media3:media3-ui:1.5.1")
    implementation("androidx.media3:media3-common:1.5.1")
    implementation("androidx.media3:media3-session:1.5.1")

    // Hybrid Engine - MPV Fallback
    implementation("dev.jdtech.mpv:libmpv:0.5.1")

    // ExoPlayer Extensions (Codecs & Subtitles) - Copied from Plezy
    // FFmpeg audio decoder for unsupported codecs (ALAC, DTS, TrueHD, etc.)
    implementation("org.jellyfin.media3:media3-ffmpeg-decoder:1.9.0+1")
    // libass-android for ASS/SSA subtitle rendering
    implementation("io.github.peerless2012:ass-media:0.4.0-beta01")

    // --- ARCHITECTURE (Paging 3 + Room) ---
    implementation("androidx.paging:paging-runtime-ktx:3.3.6")
    implementation("androidx.paging:paging-compose:3.3.6")
    implementation(libs.androidx.compose.ui.text)

    // Room
    val room_version = "2.8.4"
    implementation("androidx.room:room-runtime:$room_version")
    implementation("androidx.room:room-ktx:$room_version")
    implementation("androidx.room:room-paging:$room_version")
    ksp("androidx.room:room-compiler:$room_version") // ESSENTIEL POUR FTS5

    // WorkManager
    implementation("androidx.work:work-runtime-ktx:2.11.0")

    // --- HILT (Injection de dépendance) ---
    implementation("com.google.dagger:hilt-android:2.58")
    ksp("com.google.dagger:hilt-compiler:2.58")
    implementation("androidx.hilt:hilt-navigation-compose:1.3.0")
    implementation("androidx.hilt:hilt-work:1.3.0")
    ksp("androidx.hilt:hilt-compiler:1.3.0")
    implementation("androidx.datastore:datastore-preferences:1.2.0")
    implementation("androidx.compose.material:material-icons-extended:1.7.8")
}
