plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Passed in by the GitHub workflow (see ADMIN-SETUP.md). Empty = normal debug key, version 1.
val rioKsFile: String = (project.findProperty("RIO_KS_FILE") as String?) ?: ""
val rioKsPass: String = (project.findProperty("RIO_KS_PASS") as String?) ?: ""
val rioVerCode: Int = ((project.findProperty("RIO_VERCODE") as String?) ?: "").toIntOrNull() ?: 1
val rioVerName: String = (project.findProperty("RIO_VERNAME") as String?) ?: "1.0"

android {
    namespace = "com.rio.board"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.rio.board"
        minSdk = 24
        targetSdk = 34
        versionCode = rioVerCode
        versionName = rioVerName
    }

    signingConfigs {
        if (rioKsFile.isNotEmpty()) {
            create("rio") {
                storeFile = file(rioKsFile)
                storePassword = rioKsPass
                keyAlias = "rio"
                keyPassword = rioKsPass
            }
        }
    }

    buildTypes {
        debug {
            if (rioKsFile.isNotEmpty()) signingConfig = signingConfigs.getByName("rio")
        }
        release {
            isMinifyEnabled = false
            if (rioKsFile.isNotEmpty()) signingConfig = signingConfigs.getByName("rio")
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
