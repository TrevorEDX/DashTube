plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.trevoredx.dashtube"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.trevoredx.dashtube"
        // Android Auto shows phone activities on the car screen from Android 15 (API 35) up.
        minSdk = 35
        targetSdk = 37
        versionCode = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()
        versionName = "2.0." + (System.getenv("GITHUB_RUN_NUMBER") ?: "0")
    }

    // Personal-use signing key, kept in the repo so every build installs over the last one
    // (your YouTube login is kept). Anyone with this repo could sign an "update", so only
    // install DashTube APKs from this repository's own releases.
    signingConfigs {
        create("release") {
            storeFile = file("dashtube.keystore")
            storePassword = "dashtube"
            keyAlias = "dashtube"
            keyPassword = "dashtube"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

dependencies {
    implementation(libs.androidx.webkit)
}
