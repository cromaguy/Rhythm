import java.util.Properties
import com.android.build.api.variant.FilterConfiguration

val localProperties = Properties().also { props ->
    val f = rootProject.file("local.properties")
    if (f.exists()) props.load(f.inputStream())
}

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    // Note: androidx.baselineprofile plugin is NOT applied here because it is
    // incompatible with AGP 9.x. AGP 8.3+ includes native baseline profile
    // support via com.android.application; the baselineProfile() dep below and
    // the baseline-prof.txt file are sufficient.
    id("kotlin-parcelize")
//    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "chromahub.rhythm.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "chromahub.rhythm.app"
        minSdk = 26
        targetSdk = 37
        
        val overrideVersionCode = project.findProperty("versionCodeOverride")?.toString()?.toIntOrNull()
        val overrideVersionName = project.findProperty("versionNameOverride")?.toString()
        versionCode = overrideVersionCode ?: 544431173
        versionName = overrideVersionName ?: "5.4.443.1173"

        val overrideReleaseDate = project.findProperty("releaseDateOverride")?.toString()
        buildConfigField("String", "RELEASE_DATE", "\"${overrideReleaseDate ?: "2026-08-15"}\"")

        val isNightly = project.findProperty("nightly")?.toString() == "true"
        buildConfigField("boolean", "IS_NIGHTLY", isNightly.toString())

        // Apple Music: fallback token from environment variable (GitHub secrets), local.properties, or fallback
        val appleMusicToken = System.getenv("APPLE_MUSIC_FALLBACK_TOKEN")
            ?: localProperties.getProperty("APPLE_MUSIC_FALLBACK_TOKEN")
            ?: "eyJ0eXAiOiJKV1QiLCJhbGciOiJFUzI1NiIsImtpZCI6IldlYlBsYXlLaWQifQ.eyJpc3MiOiJBTVBXZWJQbGF5IiwiaWF0IjoxNzgxMDMyODU1LCJleHAiOjE3ODQwNTY4NTUsInJvb3RfaHR0cHNfb3JpZ2luIjpbImFwcGxlLmNvbSJdfQ.fiMFcJWkfSlxKP9NVA0UW9CbItD1Rge0SISuepz203XcpU762OqdCpU9M-YkmtKkjRmaIWtjsfGgqZPrlMonpA"
        buildConfigField("String", "APPLE_MUSIC_FALLBACK_TOKEN", "\"$appleMusicToken\"")

        // GitHub Repository info for updates & links (configurable via property, local.properties, env, or default)
        val githubOwner = project.findProperty("githubOwner")?.toString()
            ?: localProperties.getProperty("GITHUB_OWNER")
            ?: System.getenv("GITHUB_OWNER")
            ?: "cromaguy"
        val githubRepo = project.findProperty("githubRepo")?.toString()
            ?: localProperties.getProperty("GITHUB_REPO")
            ?: System.getenv("GITHUB_REPO")
            ?: "Rhythm"
        buildConfigField("String", "GITHUB_OWNER", "\"$githubOwner\"")
        buildConfigField("String", "GITHUB_REPO", "\"$githubRepo\"")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Product flavors for different distribution channels
    flavorDimensions += "distribution"
    
    productFlavors {
        create("fdroid") {
            dimension = "distribution"
            applicationId = "chromahub.rhythm.app"
            
            // F-Droid build: Enable all features (FOSS ethos)
            buildConfigField("boolean", "ENABLE_YOUTUBE_MUSIC", "true")
            buildConfigField("boolean", "ENABLE_LYRICALLY_API", "true")
            buildConfigField("boolean", "ENABLE_DEEZER", "true")
            buildConfigField("boolean", "ENABLE_LRCLIB", "true")
            buildConfigField("boolean", "ENABLE_SPOTIFY_SEARCH", "true")
            buildConfigField("boolean", "ENABLE_WIKIPEDIA", "true")
            buildConfigField("String", "FLAVOR", "\"fdroid\"")
            
            versionNameSuffix = "-fdroid"
        }
        
        create("github") {
            dimension = "distribution"
            applicationId = "chromahub.rhythm.app"
            
            // GitHub releases: Enable all features (same as F-Droid)
            buildConfigField("boolean", "ENABLE_YOUTUBE_MUSIC", "true")
            buildConfigField("boolean", "ENABLE_LYRICALLY_API", "true")
            buildConfigField("boolean", "ENABLE_DEEZER", "true")
            buildConfigField("boolean", "ENABLE_LRCLIB", "true")
            buildConfigField("boolean", "ENABLE_SPOTIFY_SEARCH", "true")
            buildConfigField("boolean", "ENABLE_WIKIPEDIA", "true")
            buildConfigField("String", "FLAVOR", "\"github\"")
            
            versionNameSuffix = "-gh"
        }
    }

    val signingProperties = getProperties(".config/keystore.properties")
    val releaseSigning = if (signingProperties != null) {
        signingConfigs.create("release") {
            keyAlias = signingProperties.property("key_alias")
            keyPassword = signingProperties.property("key_password")
            storePassword = signingProperties.property("store_password")
            storeFile = rootProject.file(signingProperties.property("store_file"))
        }
    } else {
        signingConfigs.getByName("debug")
    }

    defaultConfig {
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = releaseSigning
//            ndk {
//                debugSymbolLevel = "SYMBOL_TABLE"
//            }
            // Reproducible builds: disable build timestamp
            if (System.getenv("CI") == "true" || System.getenv("BUILD_REPRODUCIBLE") == "true") {
                // Use a fixed timestamp for reproducible builds  
                tasks.configureEach {
                    // Disable timestamps in bundle reports for reproducible builds
                    if (name.contains("BundleReport", ignoreCase = true)) {
                        enabled = false
                    }
                }
            }
        }
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            //isMinifyEnabled = false
            //isDebuggable = true
            signingConfig = releaseSigning
        }
        // Required by the macrobenchmark module for baseline profile generation.
        // Mirrors release (fully minified + signed) so the profile reflects production.
        create("benchmark") {
            initWith(buildTypes.getByName("release"))
            signingConfig = releaseSigning
            matchingFallbacks += listOf("release")
            isDebuggable = false
        }
    }
    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }

    dependenciesInfo {
        // Disables dependency metadata when building APKs (for IzzyOnDroid/F-Droid)
        includeInApk = false
        // Disables dependency metadata when building Android App Bundles
        includeInBundle = false
    }

    packaging {
        resources {
            merges += "/META-INF/INDEX.LIST"
            merges += "**/io.netty.versions.properties"
        }
    }

    // ABI splits: create smaller per-architecture APKs (reduces size by ~5–10 MB each)
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86_64", "x86")
            isUniversalApk = true // also keep a universal APK for IzzyOnDroid/F-Droid
        }
    }

    lint {
        abortOnError = true
        disable.addAll(
            listOf(
                "MissingTranslation",
                "UnsafeOptInUsageError",
                "NonObservableLocale",
                "StateFlowValueCalledInComposition",
                "LocalContextGetResourceValueCall",
                "UnusedMaterial3ScaffoldPaddingParameter"
            )
        )
    }

}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        freeCompilerArgs.addAll(
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3ExpressiveApi",
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api"
        )
    }
}

androidComponents {
    onVariants { variant ->
        variant.outputs.forEach { output ->
            val abiSuffix = output.filters
                .find { it.filterType == FilterConfiguration.FilterType.ABI }
                ?.identifier
                ?.let { "-$it" }
                ?: ""

            output.outputFileName.set(
                "Rhythm-${android.defaultConfig.versionName}-${variant.name}${abiSuffix}.apk"
            )
        }
    }
}

dependencies {
    implementation(libs.androidx.compose.ui.unit)
    // Desugaring library
    coreLibraryDesugaring(libs.androidx.desugar.jdk.libs)

    // Core Android dependencies
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.core)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.appcompat)
    
    // Compose dependencies
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    
    // Material 3 dependencies
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.material3.android)
    implementation(libs.androidx.material3.window.size)
    implementation(libs.com.google.android.material)

    // Media3 dependencies
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.exoplayer.dash)
    implementation(libs.androidx.media3.exoplayer.hls)
    implementation(libs.androidx.media3.ui)
    implementation(libs.androidx.media3.session)
    implementation(libs.androidx.media3.exoplayer.midi)
    implementation(libs.org.jellyfin.media3.ffmpeg.decoder)
    
    // Icons - Material Symbols variable font (res/font/material_symbols_outlined.ttf)
    // Replaces the deprecated material-icons-extended library for faster build times
    implementation(libs.androidx.palette.ktx)
    
    // Glance for modern widgets
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.glance.material3)
    
    // Physics-based animations
    implementation(libs.androidx.compose.animation)
    //noinspection GradleDependency
    implementation(libs.androidx.compose.animation.graphics)
    implementation(libs.androidx.compose.animation.core)
    
    // Navigation
    implementation(libs.androidx.navigation.compose)
    
    // Permissions
    implementation(libs.com.google.accompanist.accompanist.permissions)
    
    // Fragment
    implementation(libs.androidx.fragment.ktx)
    
    // MediaRouter for Android media output switching
    implementation(libs.androidx.mediarouter)
    
    // Coil for image loading
    implementation(libs.io.coil.kt.coil.compose)
    
    // Audio metadata editing
    implementation(libs.net.jthink.jaudiotagger)
    implementation(libs.taglib)
    
    // Network
    implementation(libs.com.squareup.retrofit2.retrofit)
    implementation(libs.com.squareup.retrofit2.converter.gson)
    implementation(libs.com.squareup.okhttp3.okhttp)
    implementation(libs.com.squareup.okhttp3.logging.interceptor)
    implementation(libs.com.google.code.gson.gson)
//    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
//    implementation("com.jakewharton.retrofit:retrofit2-kotlinx-serialization-converter:1.0.0")

    // Coroutines for async operations
    implementation(libs.org.jetbrains.kotlinx.coroutines.core)
    implementation(libs.org.jetbrains.kotlinx.coroutines.android)
    implementation(libs.androidx.foundation.layout)
    
    // WorkManager for background tasks
    implementation(libs.androidx.work.runtime.ktx)
    
    // Room database
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.room.paging)
    ksp(libs.androidx.room.compiler)

    // Jetpack Paging 3
    implementation(libs.androidx.paging.runtime.ktx)
    implementation(libs.androidx.paging.compose)

    // Baseline Profile – installer ensures the .prof asset is loaded into ART on first launch.
    // AGP 9.x natively picks up app/src/main/baseline-prof/baseline-prof.txt without any
    // additional plugin wiring; the baselineProfile() configuration is not available without
    // the standalone plugin (which is AGP 8.x only).
    implementation(libs.androidx.profileinstaller)

    // Testing
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
    
    // LeakCanary for memory leak detection (debug builds only)
    debugImplementation(libs.com.squareup.leakcanary.leakcanary.android)
}

composeCompiler {
    includeComposeMappingFile.set(false)
}

fun getProperties(fileName: String): Properties? {
    val file = rootProject.file(fileName)
    return if (file.exists()) {
        Properties().also { properties ->
            file.inputStream().use { properties.load(it) }
        }
    } else null
}

fun Properties.property(key: String) =
    this.getProperty(key) ?: "$key missing"
