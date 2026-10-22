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
    // Agora voice: drop optional extensions we never load (lip sync, spatial audio, voice beautifier).
    // AI noise suppression / echo cancellation stay for boat and road noise.
    packaging {
        jniLibs {
            excludes += listOf("lib/*/libagora_lip_sync_extension.so", "lib/*/libagora_spatial_audio_extension.so", "lib/*/libagora_audio_beauty_extension.so")
        }
    }
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
        buildConfigField("String", "PUSHER_KEY", "\"${cfg("PUSHER_KEY")}\"")
        buildConfigField("String", "PUSHER_CLUSTER", "\"${cfg("PUSHER_CLUSTER", "ap2")}\"")
        buildConfigField("String", "RECAPTCHA_SITE_KEY", "\"${cfg("RECAPTCHA_SITE_KEY")}\"")
    }

    // Durpalla apigw driver API (/api/v1/driver/): dev for debug builds, prod for release. Override with API_HOST.
    fun apiFields(t: com.android.build.api.dsl.ApplicationBuildType, defaultHost: String) {
        val host = cfg("API_HOST", defaultHost).trimEnd('/')
        t.buildConfigField("String", "API_BASE_URL", "\"$host/api/v1/driver/\"")
        t.buildConfigField("String", "PUSHER_AUTH_URL", "\"$host/api/v1/driver/realtime/auth\"")
    }
    buildTypes {
        debug { apiFields(this, "https://dev-apigw.durpalla.com") }
        release {
            apiFields(this, "https://apigw.durpalla.com")
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

    // `-Pabi=arm64-v8a` → one small per-ABI APK (the universal debug APK carries every native ABI: Agora + WebRTC).
    splits {
        abi {
            val abis = (project.findProperty("abi") as String?)?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty()
            isEnable = abis.isNotEmpty()
            reset()
            include(*abis.toTypedArray())
            isUniversalApk = false
        }
    }
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
    // No dependency with a known advisory: raise vulnerable transitive versions (Firestore → protobuf-javalite, Robolectric → bcprov).
    constraints {
        implementation(libs.protobuf.javalite) { because("GHSA-735f-pc8j-v9w8 fixed in 3.25.5+") }
        testImplementation(libs.bouncycastle.bcprov) { because("Bouncy Castle advisories fixed in 1.85+") }
    }
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
    // In-app calling: WebRTC audio + Firestore signaling (docs/calling-contract.md)
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.auth)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.stream.webrtc.android)
    // Agora voice: server picks agora|webrtc per call (CallProviderRouter)
    implementation(libs.agora.voice.sdk)
    implementation(libs.pusher.java.client)
    implementation(libs.recaptcha)
    implementation(libs.gson)
    implementation(libs.androidx.appcompat)
    coreLibraryDesugaring(libs.desugar.jdk.libs)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.okhttp)
    debugImplementation(libs.androidx.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.androidx.ui.test.junit4)
    testImplementation(libs.roborazzi)
    testImplementation(libs.androidx.navigation.testing)
    testImplementation(libs.roborazzi.compose)
    debugImplementation(libs.androidx.ui.test.manifest)
}

kotlin {
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
}
