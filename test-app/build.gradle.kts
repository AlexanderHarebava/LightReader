import org.jetbrains.kotlin.gradle.dsl.JvmTarget

/*
 * Copyright 2021 Readium Foundation. All rights reserved.
 * Use of this source code is governed by the BSD-style license
 * available in the top-level LICENSE file of the project.
 */

plugins {
    id("com.android.application")
    kotlin("android")
    kotlin("plugin.parcelize")
    alias(libs.plugins.ksp)
    alias(libs.plugins.compose.compiler)

}

android {
    compileSdk = (property("android.compileSdk") as String).toInt()
    defaultConfig {
        minSdk = (property("android.minSdk") as String).toInt()
        targetSdk = (property("android.targetSdk") as String).toInt()

        applicationId = "org.readium.r2reader"

        versionName = "1.0"
        versionCode = 3

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        ndk.abiFilters.add("armeabi-v7a")
        ndk.abiFilters.add("arm64-v8a")
        ndk.abiFilters.add("x86")
        ndk.abiFilters.add("x86_64")
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
        isCoreLibraryDesugaringEnabled = true
    }

    buildFeatures {
        viewBinding = true
        compose = true
        buildConfig = true
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android.txt"))
        }
    }
    packaging {
        resources.excludes.add("META-INF/*")
    }

    sourceSets {
        getByName("main") {
            java.srcDirs("src/main/java")
            res.srcDirs("src/main/res")
            assets.srcDirs("src/main/assets")
        }
    }
    namespace = "org.readium.r2.testapp"
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_11
        freeCompilerArgs.add("-Xannotation-default-target=param-property")
    }
}

dependencies {
    implementation("org.apache.poi:poi-scratchpad:3.0.1-FINAL")
    implementation("androidx.core:core-ktx:1.18.0")
    implementation(libs.cronet.embedded)
    implementation(libs.volley)
    implementation(libs.androidx.swiperefreshlayout)
    implementation(libs.androidx.ui)
    implementation(libs.androidx.datastore.core)
    implementation(libs.localagents.rag)
    implementation(libs.androidx.compose.material3.adaptive.navigation.suite)
    implementation(libs.androidx.palette.ktx)
    coreLibraryDesugaring(libs.desugar.jdk.libs)
    implementation("com.github.junrar:junrar:7.5.4")
    implementation(libs.kotlin.stdlib)
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("io.coil-kt.coil3:coil-compose:3.4.0")
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.4.0")

    implementation("com.squareup.retrofit2:converter-gson:2.9.0")

    implementation("com.squareup.okhttp3:okhttp:4.12.0")
// Logging interceptor для OkHttp — для логирования HTTP-запросов/ответов
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation(project(":readium:readium-shared"))
    implementation(project(":readium:readium-streamer"))
    implementation(project(":readium:readium-navigator"))
    implementation(project(":readium:navigators:media:readium-navigator-media-audio"))
    implementation(project(":readium:navigators:media:readium-navigator-media-tts"))
    // Only required if you want to support audiobooks using ExoPlayer.
    implementation(project(":readium:adapters:exoplayer"))
    implementation(project(":readium:readium-opds"))
    implementation(project(":readium:readium-lcp"))

    implementation(project(":readium:adapters:pdfium"))
  //  implementation(platform("com.google.firebase:firebase-bom:34.13.0"))

    implementation("com.google.ai.client.generativeai:generativeai:0.9.0")
 //   implementation("com.google.firebase:firebase-ai")
    implementation("com.simplecityapps:recyclerview-fastscroll:2.0.1")
    implementation("com.github.albfernandez:juniversalchardet:2.5.0")

    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.browser)
    implementation(libs.androidx.cardview)

    implementation("androidx.security:security-crypto-ktx:1.1.0-alpha06")
    implementation(libs.bundles.compose)
  debugImplementation(libs.androidx.compose.ui)

    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.core)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.lifecycle.common)
    implementation(libs.androidx.navigation.fragment)
    implementation(libs.androidx.navigation.ui)
    implementation(libs.androidx.paging)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.viewpager2)
    implementation(libs.androidx.webkit)
    implementation(libs.google.material)
    implementation(libs.timber)
    implementation(libs.picasso)
    implementation(libs.joda.time)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.jsoup)

    implementation(libs.bundles.media3)

    // Room database
    implementation(libs.bundles.room)
    ksp(libs.androidx.room.compiler)
}
