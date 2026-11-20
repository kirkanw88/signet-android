plugins {
    id("com.android.application")
    kotlin("android")
    kotlin("kapt")
    id("org.jetbrains.kotlin.plugin.compose")
}
android {
    namespace = "com.signet.client"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.signet.client"
        minSdk = 26
        targetSdk = 36
        versionCode = 8
        versionName = "0.2.6"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    flavorDimensions += "edition"
    productFlavors {
        create("signet") {
            dimension = "edition"
            buildConfigField("boolean", "SIGNET_ACCOUNT", "true")
            buildConfigField("String", "IMPORT_SCHEME", "\"signet\"")
            manifestPlaceholders["importScheme"] = "signet"
        }
        create("external") {
            versionCode = 20
            versionName = "0.2.18"
            dimension = "edition"
            applicationIdSuffix = ".external"
            buildConfigField("boolean", "SIGNET_ACCOUNT", "false")
            buildConfigField("String", "IMPORT_SCHEME", "\"signet-external\"")
            manifestPlaceholders["importScheme"] = "signet-external"
        }
    }
    buildFeatures { compose = true; buildConfig = true }
    sourceSets.getByName("androidTest").assets.srcDir(rootProject.file(".gradle/transport-lab/android-assets"))
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    packaging { jniLibs.useLegacyPackaging = false; resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
    buildTypes { release {
        isMinifyEnabled = true; isShrinkResources = true
        // Installable development release. Production signing is injected outside the repository.
        signingConfig = signingConfigs.getByName("debug")
        proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
    } }
    splits { abi { isEnable = true; reset(); include("arm64-v8a", "armeabi-v7a", "x86_64"); isUniversalApk = true } }
}
dependencies {
    implementation(project(":core"))
    implementation(files("libs/libXray.aar"))
    implementation(platform("androidx.compose:compose-bom:2025.05.01"))
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.0")
    implementation("androidx.work:work-runtime-ktx:2.10.1")
    implementation("androidx.datastore:datastore-preferences:1.1.7")
    implementation("io.insert-koin:koin-android:4.0.4")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.moshi:moshi:1.15.2")
    implementation("org.locationtech.jts:jts-core:1.20.0")
    implementation("androidx.room:room-runtime:2.7.1")
    implementation("androidx.room:room-ktx:2.7.1")
    kapt("androidx.room:room-compiler:2.7.1")
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2025.05.01"))
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.uiautomator:uiautomator:2.3.0")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
