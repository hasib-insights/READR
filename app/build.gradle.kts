import java.io.File

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.readr.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.readr.app"
        minSdk = 24
        targetSdk = 35
        versionCode = 3
        versionName = "1.0.2"
        vectorDrawables { useSupportLibrary = true }
    }

    // Release signing is read from environment variables (CI) or gradle.properties
    // (local machine). The keystore file itself is NEVER committed to the repository.
    signingConfigs {
        create("release") {
            val ksPath = System.getenv("READR_KEYSTORE_PATH")
                ?: (project.findProperty("readr.keystore.path") as String?)
            val ksPass = System.getenv("READR_KEYSTORE_PASSWORD")
                ?: (project.findProperty("readr.keystore.password") as String?)
            val ksAlias = System.getenv("READR_KEY_ALIAS")
                ?: (project.findProperty("readr.keystore.alias") as String? ?: "readr")
            val ksKeyPass = System.getenv("READR_KEY_PASSWORD")
                ?: (project.findProperty("readr.keystore.keyPassword") as String?) ?: ksPass
            if (ksPath != null && ksPass != null && File(ksPath).exists()) {
                storeFile = File(ksPath)
                storePassword = ksPass
                keyAlias = ksAlias
                keyPassword = ksKeyPass
            }
        }
    }

    buildTypes {
        release {
            // Code shrinking can be disabled on memory-constrained build machines:
            //   ./gradlew :app:assembleRelease -Preadr.release.minify=false
            isMinifyEnabled = (project.findProperty("readr.release.minify") as String?) != "false"
            isShrinkResources = (project.findProperty("readr.release.minify") as String?) != "false"
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig =
                if (signingConfigs.getByName("release").storeFile != null)
                    signingConfigs.getByName("release")
                else
                    signingConfigs.getByName("debug")
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
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.datastore.preferences)
    implementation(libs.coil.compose)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    // XmlPullParser implementation for running the document engines on the JVM.
    testImplementation(libs.kxml2)

    debugImplementation(libs.androidx.compose.ui.tooling)
}
