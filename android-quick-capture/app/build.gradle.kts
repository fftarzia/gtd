plugins {
    id("com.android.application")
}

android {
    namespace = "com.fftarzia.gtddo.quickcapture"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.fftarzia.gtddo.quickcapture"
        minSdk = 26
        targetSdk = 37
        versionCode = 4
        versionName = "0.3.1"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
    buildFeatures { resValues = true }

    buildTypes.getByName("debug") {
        // Keep the working 0.2.0 app and its Android Keystore completely untouched.
        applicationIdSuffix = ".preview"
    }

    // A release is deliberately unsigned unless a private, persistent key is supplied.
    // Never commit a keystore, its passwords or an installed app's configuration.
    val keyFile = System.getenv("GTDDO_KEYSTORE_FILE")
    if (!keyFile.isNullOrBlank()) {
        signingConfigs.create("personal") {
            storeFile = file(keyFile)
            storePassword = System.getenv("GTDDO_KEYSTORE_PASSWORD")
            keyAlias = System.getenv("GTDDO_KEY_ALIAS")
            keyPassword = System.getenv("GTDDO_KEY_PASSWORD")
        }
        buildTypes.getByName("release").signingConfig = signingConfigs.getByName("personal")
    }
}

dependencies {
    implementation("com.google.android.material:material:1.14.0")
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel:2.9.4")
    implementation("androidx.lifecycle:lifecycle-livedata:2.9.4")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.18.1")
    testImplementation("androidx.arch.core:core-testing:2.2.0")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    testImplementation("com.squareup.okhttp3:okhttp-tls:4.12.0")
}

tasks.withType<Test>().configureEach {
    // Keep Android framework state and native graphics isolated between test classes.
    forkEvery = 1
}
