import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.focusrefine.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.focusrefine.app"
        minSdk = 28
        targetSdk = 36
        versionCode = 5
        versionName = "1.1.3"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    buildFeatures {
        viewBinding = true
        buildConfig = true
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.8"
    }
    // Release signing: put the upload key in keystore.properties at the repo root (git-ignored) with storeFile,
    // storePassword, keyAlias and keyPassword. Without it the release build falls back to the debug key, which works
    // for installing and testing but is rejected by Google Play.
    val keystoreProps = Properties().apply {
        val f = rootProject.file("keystore.properties")
        if (f.exists()) f.inputStream().use { load(it) }
    }
    val hasUploadKey = keystoreProps.getProperty("storeFile") != null
    if (hasUploadKey) {
        signingConfigs.create("upload") {
            storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
            storePassword = keystoreProps.getProperty("storePassword")
            keyAlias = keystoreProps.getProperty("keyAlias")
            keyPassword = keystoreProps.getProperty("keyPassword")
        }
    }
    buildTypes {
        release {
            signingConfig = signingConfigs.getByName(if (hasUploadKey) "upload" else "debug")
            // Shrink code and resources. Phones only need the ARM libraries; pass -PallAbis to also keep x86/x86_64
            // (for running a release build on an emulator).
            isMinifyEnabled = !project.hasProperty("noMinify")
            isShrinkResources = !project.hasProperty("noMinify")
            if (!project.hasProperty("allAbis")) ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    packaging {
        jniLibs {
            useLegacyPackaging = false
        }
    }
    lint {
        abortOnError = false
    }
    sourceSets {
        getByName("androidTest") {
            // Golden + holdout notification datasets, shared with scripts/replay_golden.py
            assets.srcDirs("$rootDir/testdata/golden", "$rootDir/testdata/corpus")
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.car.app:app:1.4.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")

    val room_version = "2.6.1"
    implementation("androidx.room:room-runtime:${room_version}")
    implementation("androidx.room:room-ktx:${room_version}")
    ksp("androidx.room:room-compiler:${room_version}")

    implementation("androidx.activity:activity-ktx:1.8.2")
    implementation("androidx.fragment:fragment-ktx:1.6.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0")
    // Track B on-device model. LiteRT keeps the TFLite Java API and is 16 KB aligned. Its transitive libraries are not
    // used by the interpreter, and they would move Compose and Lifecycle to newer versions than the app is built against.
    implementation("com.google.ai.edge.litert:litert:2.2.0") {
        exclude(group = "androidx.lifecycle")
        exclude(group = "com.google.guava")
        exclude(group = "org.jetbrains.kotlinx")
        exclude(group = "com.google.android.play")
    }
    implementation("androidx.biometric:biometric:1.2.0-alpha05")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    implementation("net.zetetic:sqlcipher-android:4.19.1") // 16 KB aligned; 4.5.4 was not

    val composeBom = platform("androidx.compose:compose-bom:2024.02.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    debugImplementation("androidx.compose.ui:ui-tooling")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20231013") // the Android stub of org.json does nothing in JVM tests
    testImplementation("org.mockito:mockito-core:4.11.0")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation("androidx.room:room-testing:2.6.1")
    androidTestImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
}
