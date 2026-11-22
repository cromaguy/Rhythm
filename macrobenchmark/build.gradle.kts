plugins {
    id("com.android.test")
    // Note: org.jetbrains.kotlin.android is NOT applied — AGP 9.0+ has built-in Kotlin support.
    // Note: androidx.baselineprofile plugin omitted — incompatible with AGP 9.x.
    // The BaselineProfileRule in benchmark-macro-junit4 generates the profile at
    // runtime without requiring the Gradle plugin on either module.
}

android {
    namespace = "chromahub.rhythm.app.macrobenchmark"
    compileSdk {
        version = release(37) {
            minorApiLevel = 2
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }


    defaultConfig {
        minSdk = 26
        targetSdk = 37
        testInstrumentationRunner = "androidx.benchmark.junit4.AndroidBenchmarkRunner"
    }

    // Points the test module at the :app module it instruments
    targetProjectPath = ":app"

    // Required for self-instrumenting baseline profile generation
    experimentalProperties["android.experimental.self-instrumenting"] = true

    buildTypes {
        // The benchmark build type instruments the :app's "benchmark" variant
        create("benchmark") {
            isDebuggable = true
            signingConfig = getByName("debug").signingConfig
            matchingFallbacks += listOf("release")
        }
    }

    // Mirror the :app product flavor dimensions so Gradle can match variants
    flavorDimensions += "distribution"
    productFlavors {
        create("github") {
            dimension = "distribution"
        }
        create("fdroid") {
            dimension = "distribution"
        }
    }
}

dependencies {
    // benchmark-macro-junit4 doesn't transitively include benchmark-junit4 in 1.3.4;
    // AndroidBenchmarkRunner lives in benchmark-junit4 and must be added explicitly.
    implementation(libs.androidx.benchmark.junit4)
    implementation(libs.androidx.benchmark.macro.junit4)
    implementation(libs.androidx.uiautomator)
    implementation(libs.androidx.junit)
}

// Only enable the benchmark build type; disable all others to keep build fast
androidComponents {
    beforeVariants(selector().all()) {
        it.enable = it.buildType == "benchmark"
    }
}
