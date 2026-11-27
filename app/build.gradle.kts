import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

// Load external API credentials from local.properties so they stay out of git.
//   USDA — falls back to public DEMO_KEY (rate-limited but works out of the box)
private val externalProps: Properties = run {
    val props = Properties()
    val localProps = rootProject.file("local.properties")
    if (localProps.exists()) localProps.inputStream().use { props.load(it) }
    props
}
private fun creds(name: String, default: String = ""): String =
    externalProps.getProperty(name) ?: System.getenv(name) ?: default

val usdaApiKey: String = creds("USDA_API_KEY", "DEMO_KEY")

android {
    namespace = "com.example.foodtracker"
    compileSdk {
        version = release(37)
    }
    compileSdkMinor = 2

    defaultConfig {
        applicationId = "com.example.foodtracker"
        minSdk = 31
        targetSdk = 37
        versionCode = 14
        versionName = "2.6"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "USDA_API_KEY", "\"$usdaApiKey\"")
    }

    signingConfigs {
        // Deliberately public: the key and both passwords are in the repo, so anyone can
        // build an APK that installs over — and updates — a GitHub release. That also means
        // anyone can sign one, so it proves nothing about who built a given APK. Never use
        // it for Play.
        create("githubPublicRelease") {
            storeFile = rootProject.file("github-public-release.jks")
            storePassword = "github-public-release"
            keyAlias = "github-public-release"
            keyPassword = "github-public-release"
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("githubPublicRelease")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // A universal APK carries every ABI forever: all four sit in
            // base.apk on disk for the life of the install, but only the
            // device's own is ever loaded. Dropping the x86 pair is ~135 KB
            // of permanent footprint for no loss on any phone. (It does rule
            // out x86_64 Chromebooks; nothing else runs x86 Android.)
            // Debug builds keep every ABI, so emulators are unaffected.
            ndk {
                abiFilters += listOf("arm64-v8a", "armeabi-v7a")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    androidResources {
        localeFilters += listOf("en")
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/*.version"
            excludes += "/META-INF/com.android.tools/**"
            excludes += "/DebugProbesKt.bin"
            excludes += "/kotlin-tooling-metadata.json"
        }
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    // Downloadable Google Fonts (Manrope, Inter) — fetched by Play Services, zero APK cost.
    implementation(libs.androidx.compose.ui.text.google.fonts)
    // Lucide line icons. R8 strips every icon the app does not reference.
    implementation(libs.lucide.icons)
    // Backdrop blur for the floating tab bar (RenderEffect on API 31+).
    implementation(libs.haze)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.documentfile)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.datastore.preferences)

    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    // Not an unused dependency: CameraX already pulls guava in for
    // ListenableFuture, so this declares nothing new to the compiler. It is
    // here as a version pin — dropping it resolves guava to whatever CameraX
    // asks for (33.3.1 today) instead of the version named in the catalog.
    implementation(libs.guava)
    // Play-Services-hosted ML Kit — the scan engine + model live in Google Play
    // Services, so our APK doesn't ship them (~19 MB saved across 4 ABIs).
    // Tradeoff: requires GMS installed on the device. Acceptable since the
    // target audience is mainstream Play Store users.
    implementation(libs.play.services.mlkit.barcode.scanning)

    // Jetpack Glance — Compose-style API for AppWidgets. We read directly from
    // the existing Room DAOs inside provideGlance (suspend context, no extra
    // serialization layer) and ping widgets to update after food log writes.
    implementation(libs.androidx.glance.appwidget)
    testImplementation(libs.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
