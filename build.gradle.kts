// SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
// SPDX-License-Identifier: GPL-3.0-or-later

// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.test) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    // Note: kotlin.android is NOT listed here — AGP 9.0+ has built-in Kotlin support
    // and applying kotlin.android would throw "no longer required since AGP 9.0".
    alias(libs.plugins.ksp) apply false
}
