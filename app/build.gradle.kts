plugins {
    id("com.android.application")
}

android {
    namespace = "com.example.autorunstudio"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.autorunstudio"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            isShrinkResources = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
