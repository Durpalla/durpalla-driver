pluginManagement {
    // Kotlin 2.4 metadata needs R8 ≥ 9.1.29; AGP 8.7.3 ships an older R8 (D8 kotlin-metadata warnings).
    // https://developer.android.com/build/kotlin-support
    buildscript {
        repositories {
            mavenCentral()
            maven { url = uri("https://storage.googleapis.com/r8-releases/raw") }
        }
        dependencies {
            classpath("com.android.tools:r8:9.1.29")
        }
    }
    repositories { google(); mavenCentral(); gradlePluginPortal() }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories { google(); mavenCentral() }
}
rootProject.name = "durpalla-driver"
include(":app")
