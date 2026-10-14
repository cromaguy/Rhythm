/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.util

import androidx.core.content.pm.PackageInfoCompat
import android.app.Activity
import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import chromahub.rhythm.app.shared.data.model.AppSettings
import chromahub.rhythm.app.activities.CrashActivity // Import CrashActivity
import kotlin.system.exitProcess

object CrashReporter {

    private const val TAG = "CrashReporter"
    private lateinit var appSettings: AppSettings // Declare AppSettings

    fun init(application: Application) {
        appSettings = AppSettings.getInstance(application) // Initialize AppSettings
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            if (isRuntimeShutdownThrowable(throwable)) {
                Log.w(TAG, "Ignoring uncaught exception during runtime shutdown on thread ${thread.name}")
                return@setDefaultUncaughtExceptionHandler
            }

            var appVersion = "Unknown"
            try {
                val packageInfo = application.packageManager.getPackageInfo(application.packageName, 0)
                val versionCode = PackageInfoCompat.getLongVersionCode(packageInfo)
                appVersion = "${packageInfo.versionName} ($versionCode)"
            } catch (e: Exception) {
                Log.e(TAG, "Failed to get package info", e)
            }

            val manufacturer = android.os.Build.MANUFACTURER
            val model = android.os.Build.MODEL
            val sdkInt = android.os.Build.VERSION.SDK_INT
            val release = android.os.Build.VERSION.RELEASE

            val builder = StringBuilder()
            builder.append("=== T2Rhythm App Crash Report ===\n")
            builder.append("Time: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", java.util.Locale.US).format(java.util.Date())}\n")
            builder.append("App Version: $appVersion\n")
            builder.append("Device: $manufacturer $model\n")
            builder.append("Android Version: $release (API $sdkInt)\n")
            builder.append("Thread: ${thread.name}\n")
            builder.append("===============================\n\n")
            builder.append(Log.getStackTraceString(throwable))
            val crashLog = builder.toString()

            Log.e(TAG, "Uncaught exception on thread ${thread.name}: $crashLog")
            appSettings.addCrashLogEntry(crashLog) // Add to crash log history
            CrashActivity.start(application.applicationContext, crashLog) // Start CrashActivity
            // Terminate the crashed process to ensure a clean restart
            android.os.Process.killProcess(android.os.Process.myPid())
            System.exit(1)
        }
    }


    private fun isRuntimeShutdownThrowable(throwable: Throwable?): Boolean {
        var cause = throwable
        while (cause != null) {
            val message = cause.message
            if (message != null && (
                message.contains("Thread starting during runtime shutdown") ||
                message.contains("Runtime shutdown in progress")
            )) {
                return true
            }
            cause = cause.cause
        }
        return false
    }



}
