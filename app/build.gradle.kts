plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.zepinto.mutiny"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.zepinto.mutiny"
        minSdk = 26
        targetSdk = 36
        versionCode = 3
        versionName = "2.0"
        // Screenshots are blocked by default so nobody can capture a role card. `-PallowScreenshots` lifts that for emulator test builds.
        buildConfigField("boolean", "SECURE_WINDOW", "${!project.hasProperty("allowScreenshots")}")
    }

    // Google Play build: `./gradlew bundleRelease -Pplay` signs the App Bundle with the upload key named by
    // mutiny.upload.* in ~/.gradle/gradle.properties (outside the repo). Without -Pplay the release is signed with
    // the debug key, so a sideloaded APK keeps updating the copies people already installed.
    val play = project.hasProperty("play")
    if (play) {
        signingConfigs {
            create("upload") {
                fun prop(name: String) = project.findProperty("mutiny.upload.$name")?.toString()
                    ?: throw GradleException("-Pplay needs mutiny.upload.$name in ~/.gradle/gradle.properties")
                storeFile = file(prop("storeFile"))
                storePassword = prop("storePassword")
                keyAlias = prop("keyAlias")
                keyPassword = prop("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName(if (play) "upload" else "debug")
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
