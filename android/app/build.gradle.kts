plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "io.github.sreekumartp.aimlatlas"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.github.sreekumartp.aimlatlas"
        minSdk = 26
        targetSdk = 36
        versionCode = 19
        versionName = "3.0"
    }

    // Play upload key. Supplied by CI from GitHub Secrets (see playstore/README.md);
    // never committed. Without it, release builds fall back to the debug key so the
    // APK still installs for testing, but such builds cannot be uploaded to Play.
    val uploadStore = System.getenv("UPLOAD_KEYSTORE_FILE")
    signingConfigs {
        if (uploadStore != null) {
            create("upload") {
                storeFile = file(uploadStore)
                storePassword = System.getenv("UPLOAD_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("UPLOAD_KEY_ALIAS")
                keyPassword = System.getenv("UPLOAD_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = if (uploadStore != null) signingConfigs.getByName("upload")
                            else signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    androidResources {
        // Keep the web assets uncompressed-friendly; woff2 is already compressed.
        noCompress += listOf("woff2")
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.activity:activity-ktx:1.9.3")
    implementation("androidx.webkit:webkit:1.12.1")
    implementation("androidx.browser:browser:1.8.0")
}
