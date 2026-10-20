import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.roborazzi)
}

// Firebase is optional: the google-services plugin is applied only when app/google-services.json exists,
// so the project builds (with push disabled) on machines without Firebase credentials.
if (file("google-services.json").exists()) apply(plugin = "com.google.gms.google-services")

// Secrets/config: local.properties > gradle property (-P or ~/.gradle/gradle.properties) > env > default.
val localProps = Properties().apply { rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) } }
fun cfg(key: String, default: String = ""): String =
    (localProps.getProperty(key) ?: (project.findProperty(key) as String?) ?: System.getenv(key) ?: default).replace("\"", "")

android {
    namespace = "com.durpalla.driver"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.durpalla.driver"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
        vectorDrawables { useSupportLibrary = true }
        resourceConfigurations += listOf("en", "bn")
        buildConfigField("String", "API_BASE_URL", "\"${cfg("API_BASE_URL", "https://api.durpalla.com/driver/v1/")}\"")
        buildConfigField("String", "PUSHER_KEY", "\"${cfg("PUSHER_KEY")}\"")
        buildConfigField("String", "PUSHER_CLUSTER", "\"${cfg("PUSHER_CLUSTER", "ap2")}\"")
        buildConfigField("String", "PUSHER_AUTH_URL", "\"${cfg("PUSHER_AUTH_URL", "https://api.durpalla.com/driver/v1/realtime/auth")}\"")
        buildConfigField("String", "RECAPTCHA_SITE_KEY", "\"${cfg("RECAPTCHA_SITE_KEY")}\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        // java.time (dates, slots) on minSdk 24.
        isCoreLibraryDesugaringEnabled = true
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    // Keep Bangla strings in the base APK so the in-app language switch works for Play installs too.
    bundle { language { enableSplit = false } }
    lint {
        // Every user-facing string must exist in values-bn; fail the build otherwise.
        error += listOf("MissingTranslation", "ExtraTranslation", "MissingQuantity")
        abortOnError = true
        checkReleaseBuilds = false
    }
    testOptions { unitTests { isIncludeAndroidResources = true } }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)
    implementation(libs.firebase.appcheck.playintegrity)
    implementation(libs.pusher.java.client)
    implementation(libs.recaptcha)
    implementation(libs.gson)
    implementation(libs.androidx.appcompat)
    coreLibraryDesugaring(libs.desugar.jdk.libs)
    implementation(libs.androidx.datastore.preferences)
    debugImplementation(libs.androidx.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.androidx.ui.test.junit4)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    debugImplementation(libs.androidx.ui.test.manifest)
}
