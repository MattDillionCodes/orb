// A tiny stand-in for Spotify or YouTube Music, for testing ORB UI's "other music apps" support on
// the emulator, which can't run the real apps. Never shipped; the run script installs it only on OrbDash_TV.
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.orbdash.testmusic"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.orbdash.testmusic"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
