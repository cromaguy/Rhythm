/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.debug

import leakcanary.LeakCanary
import shark.AndroidReferenceMatchers
import kotlin.jvm.JvmStatic

/**
 * Debug-only LeakCanary tuning, loaded reflectively by
 * [chromahub.rhythm.app.RhythmApplication.configureLeakCanary].
 *
 * Suppresses known false positives so only real app-side leaks get reported.
 */
object LeakCanaryDebugConfig {

    @JvmStatic
    fun applyKnownReferenceMatchers() {
        LeakCanary.config = LeakCanary.config.copy(
            referenceMatchers = AndroidReferenceMatchers.appDefaults +
                listOf(
                    // Framework quirk: on Android 12+ (and especially newer SDKs like 36)
                    // TileService keeps its anonymous IQSTileService$Stub binder (TileService$2)
                    // alive after Service#onDestroy(), which holds the service via `this$0`.
                    // Nothing app-side retains the tile service, so treat it as a known
                    // framework leak instead of a real one.
                    AndroidReferenceMatchers.instanceFieldLeak(
                        className = "android.service.quicksettings.TileService\$2",
                        fieldName = "this\$0",
                        description = "TileService retained by framework TileService\$2 binder stub after onDestroy (system quirk, not an app leak)."
                    ),
                    // Framework quirk: InputMethodManager singleton retains the last served/focused
                    // View or DecorView across Activity destruction on newer Android versions (e.g. SDK 36+),
                    // where Shark / LeakCanary's built-in SDK-bounded matchers do not cover the API level.
                    AndroidReferenceMatchers.instanceFieldLeak(
                        className = "android.view.inputmethod.InputMethodManager",
                        fieldName = "mNextServedView",
                        description = "InputMethodManager.mNextServedView retains destroyed activity view hierarchy (framework bug)."
                    ),
                    AndroidReferenceMatchers.instanceFieldLeak(
                        className = "android.view.inputmethod.InputMethodManager",
                        fieldName = "mServedView",
                        description = "InputMethodManager.mServedView retains destroyed activity view hierarchy (framework bug)."
                    ),
                    AndroidReferenceMatchers.instanceFieldLeak(
                        className = "android.view.inputmethod.InputMethodManager",
                        fieldName = "mCurRootView",
                        description = "InputMethodManager.mCurRootView retains destroyed activity view hierarchy (framework bug)."
                    ),
                    AndroidReferenceMatchers.instanceFieldLeak(
                        className = "android.view.inputmethod.InputMethodManager",
                        fieldName = "mLastSrvView",
                        description = "InputMethodManager.mLastSrvView retains destroyed activity view hierarchy (framework bug)."
                    ),
                    AndroidReferenceMatchers.instanceFieldLeak(
                        className = "android.view.inputmethod.InputMethodManager",
                        fieldName = "mCurrentRootView",
                        description = "InputMethodManager.mCurrentRootView retains destroyed activity view hierarchy (framework bug)."
                    ),
                    AndroidReferenceMatchers.instanceFieldLeak(
                        className = "android.view.inputmethod.InputMethodManager",
                        fieldName = "mServedInputConnection",
                        description = "InputMethodManager.mServedInputConnection retains destroyed activity view hierarchy (framework bug)."
                    ),
                    // Framework quirk: NsdManager internal ServiceHandler / NsdCallbackImpl binder stub
                    // retained asynchronously by system server after stopServiceDiscovery.
                    AndroidReferenceMatchers.instanceFieldLeak(
                        className = "android.net.nsd.NsdManager\$NsdCallbackImpl",
                        fieldName = "mServHandler",
                        description = "NsdManager\$NsdCallbackImpl.mServHandler retained asynchronously by system server binder stub."
                    )
                )
        )
    }
}
