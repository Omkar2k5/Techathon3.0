plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.example.edunet"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "com.example.edunet"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/DEPENDENCIES"
            excludes += "/META-INF/LICENSE*"
            excludes += "/META-INF/NOTICE*"
            excludes += "/META-INF/INDEX.LIST"
            excludes += "/META-INF/io.netty.versions.properties"
            excludes += "/META-INF/native-image/**/*.properties"
            excludes += "META-INF/native-image/org.mongodb/bson/native-image.properties"
            excludes += "META-INF/native-image/org.mongodb/mongodb-driver-core/native-image.properties"
            excludes += "META-INF/nanohttpd/**"
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    // Navigation
    implementation("androidx.navigation:navigation-compose:2.7.7")
    // MongoDB Kotlin Coroutine Driver (direct Atlas connection)
    implementation("org.mongodb:mongodb-driver-kotlin-coroutine:4.11.1") {
        exclude(group = "org.mongodb", module = "bson-record-codec")
    }
    implementation("org.mongodb:bson-kotlinx:4.11.1") {
        exclude(group = "org.mongodb", module = "bson-record-codec")
    }
    // ViewModel + Coroutines
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // HTTP client to talk to local FastAPI backend
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // Offline session: embedded HTTP server on teacher's device
    implementation("org.nanohttpd:nanohttpd:2.3.1")

    // QR code generation + scanning
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")
    implementation("com.google.zxing:core:3.5.3")

    // Image loading for QR bitmap display
    implementation("io.coil-kt:coil-compose:2.6.0")
}

configurations.all {
    exclude(group = "org.mongodb", module = "bson-record-codec")
}