plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "de.ulf.dashboard"
    compileSdk = 34

    defaultConfig {
        applicationId = "de.ulf.dashboard"
        minSdk = 30 // ViewBoard runs Android 11
        targetSdk = 30
        // Overridden by the release workflow via -PversionCode / -PversionName
        versionCode = (findProperty("versionCode") as String? ?: "1").toInt()
        versionName = findProperty("versionName") as String? ?: "1.0"
    }

    // Only configured when the release workflow provides the keystore; local release builds stay unsigned
    val keystorePath = System.getenv("KEYSTORE_FILE")
    if (keystorePath != null) {
        signingConfigs.create("release") {
            storeFile = file(keystorePath)
            storePassword = System.getenv("KEYSTORE_PASSWORD")
            keyAlias = System.getenv("KEY_ALIAS")
            keyPassword = System.getenv("KEY_PASSWORD")
        }
        buildTypes.getByName("release").signingConfig = signingConfigs.getByName("release")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    // Sideloaded onto the ViewBoard, never published to Google Play
    lint { disable += "ExpiredTargetSdkVersion" }
}

dependencies {
    // Test-only: JUnit runner and a real org.json (android.jar only ships stubs)
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}
