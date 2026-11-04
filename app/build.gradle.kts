plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.zepinto.resistance"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.zepinto.resistance"
        minSdk = 26
        targetSdk = 35
        versionCode = 2
        versionName = "1.1"
        // Screenshots are blocked by default so nobody can capture a role card. `-PallowScreenshots` lifts that for emulator test builds.
        buildConfigField("boolean", "SECURE_WINDOW", "${!project.hasProperty("allowScreenshots")}")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // Signed with the debug key so the APK can be sideloaded as it is. Use your own keystore for a Play Store upload.
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.core:core-ktx:1.15.0")

    testImplementation("junit:junit:4.13.2")
    // The Android SDK only ships stubs of org.json for unit tests; the real library lets the saved game be tested on the JVM.
    testImplementation("org.json:json:20240303")
}
