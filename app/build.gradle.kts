plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.terpenesalad.nugofwar"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.terpenesalad.nugofwar"
        minSdk = 24
        targetSdk = 35
        versionCode = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()
        versionName = "1.0." + (System.getenv("GITHUB_RUN_NUMBER") ?: "0")
    }

    signingConfigs {
        // A fixed, non-secret key that lives in the repo so every build can be
        // installed over the previous one without uninstalling first.
        create("shared") {
            storeFile = rootProject.file("keystore/nugofwar.keystore")
            storePassword = "nugofwar"
            keyAlias = "nugofwar"
            keyPassword = "nugofwar"
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("shared")
        }
        getByName("debug") {
            signingConfig = signingConfigs.getByName("shared")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}
