import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.outspoken"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.outspoken"
        // 31 is the floor for the on-device SpeechRecognizer used later.
        minSdk = 31
        targetSdk = 36
        versionCode = 1
        versionName = "0.1"

        // Only the iQOO's chip type. Native libraries for every chip made the APK 140 MB, which
        // took over 20 minutes to reach the phone; it is the only device the app runs on.
        ndk {
            abiFilters += "arm64-v8a"
        }

        // Which code this APK was built from, shown on the eye check page and in the run log, so
        // two phones can check they run the same build.
        buildConfigField("String", "COMMIT", "\"${gitCommit()}\"")
        buildConfigField("boolean", "CHANGED", "${gitChanged()}")
        buildConfigField("boolean", "FROM_CI", "${System.getenv("GITHUB_ACTIONS") == "true"}")
        buildConfigField("boolean", "SHARED_KEY", "${System.getenv("GITHUB_ACTIONS") == "true" || sharedDebugKey.exists()}")
    }

    // The team's shared debug key, the one CI signs with, kept outside the repo. With it every
    // laptop build and every CI APK installs over the last one, so nothing has to be uninstalled
    // and the phone keeps its settings, logs and "All files access".
    signingConfigs {
        getByName("debug") {
            if (sharedDebugKey.exists()) storeFile = sharedDebugKey
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    androidResources {
        // MediaPipe maps the face model straight from the APK, which needs it stored uncompressed.
        noCompress += "task"
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)

    implementation(libs.mediapipe.tasks.vision)
    implementation(libs.litertlm.android)

    testImplementation(libs.junit)
}

val sharedDebugKey: File get() = File(System.getProperty("user.home"), ".android/outspoken-debug.keystore")

fun git(vararg args: String): String = runCatching {
    providers.exec { commandLine("git", *args) }.standardOutput.asText.get().trim()
}.getOrDefault("")

fun gitCommit(): String = git("rev-parse", "--short=8", "HEAD").ifEmpty { "unknown" }

/** True when the build has edits that are not committed, so it may differ from the commit. */
fun gitChanged(): Boolean = git("status", "--porcelain", "--untracked-files=no").isNotEmpty()
