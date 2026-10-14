// SPDX-License-Identifier: GPL-3.0-or-later
package chromahub.rhythm.app.features.local.presentation.viewmodel

import android.app.Application
import android.app.NotificationManager
import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import chromahub.rhythm.app.shared.data.model.AppSettings
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Test

class LibraryProgressRegressionTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as Application
    private val notifications get() = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private fun current() = notifications.activeNotifications.firstOrNull { it.id == LibraryNotificationManager.LIBRARY_SETUP_NOTIFICATION_ID }?.notification
    private fun seed(manager: LibraryNotificationManager) = manager.showOperationProgressNotification(
        LibraryNotificationManager.LIBRARY_SETUP_NOTIFICATION_ID, "Regression", "Resolving album art")

    @Test fun oldArtworkProgressIsClearedWhenMonitoringRestarts() = runBlocking {
        val settings = AppSettings.getInstance(context); val previous = settings.libraryOperationsNotificationsEnabled.value
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val manager = LibraryNotificationManager(context)
        try {
            settings.setLibraryOperationsNotificationsEnabled(true); seed(manager)
            withTimeout(20000) { while (current() == null) delay(50) }
            assertNotNull(current())
            manager.startLibrarySetupCompletionMonitor(scope, MutableStateFlow(false), MutableStateFlow(false), MutableStateFlow(false), { 0 }, { 0 }, { 0 })
            withTimeout(20000) { while (current() != null) delay(50) }
            assertNull(current())
        } finally { scope.cancel(); manager.disarmLibrarySetupCompletionNotification(); settings.setLibraryOperationsNotificationsEnabled(previous) }
    }

    @Test fun initialSetupFinishesAfterMetadataAndCancelledProgressIsRemoved() = runBlocking {
        val settings = AppSettings.getInstance(context); val previous = settings.libraryOperationsNotificationsEnabled.value
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val manager = LibraryNotificationManager(context)
        val metadata = MutableStateFlow(true)
        try {
            settings.setLibraryOperationsNotificationsEnabled(true)
            manager.armLibrarySetupCompletionNotification()
            manager.startLibrarySetupCompletionMonitor(scope, MutableStateFlow(false), MutableStateFlow(false), metadata, { 1 }, { 1 }, { 1 })
            withTimeout(20000) { while (current() == null) delay(50) }
            metadata.value = false
            withTimeout(20000) { while (current()?.flags?.and(android.app.Notification.FLAG_ONGOING_EVENT) != 0) delay(50) }
            assertNotNull(current())
            seed(manager); assertNotNull(current())
            manager.disarmLibrarySetupCompletionNotification()
            withTimeout(20000) { while (current() != null) delay(50) }
            assertNull(current())
        } finally { scope.cancel(); manager.disarmLibrarySetupCompletionNotification(); settings.setLibraryOperationsNotificationsEnabled(previous) }
    }

    @Test fun storedMoreDoesNotConsumeAFifthSelectableAction() {
        val prefs = context.getSharedPreferences("rhythm_preferences", Context.MODE_PRIVATE)
        val keys = listOf("expressive_bottom_buttons_normal", "expressive_hidden_bottom_buttons_normal", "expressive_bottom_buttons_merge", "expressive_hidden_bottom_buttons_merge")
        val originals = keys.associateWith { prefs.getString(it, null) }
        try {
            prefs.edit().putString(keys[0], "DEVICE,QUEUE,MORE,SHUFFLE").putString(keys[1], "MORE")
                .putString(keys[2], "LYRICS,FAVORITE,DEVICE,QUEUE,MORE,SHUFFLE").putString(keys[3], "MORE").commit()
            val ctor = AppSettings::class.java.getDeclaredConstructor(Context::class.java).apply { isAccessible = true }
            val cold = ctor.newInstance(context)
            assertEquals(listOf("LYRICS", "FAVORITE", "DEVICE", "QUEUE", "SHUFFLE"), cold.expressiveBottomButtonsMerge.value)
            assertFalse(cold.allExpressiveBottomButtons.contains("MORE"))
            assertFalse(cold.expressiveBottomButtonsNormal.value.contains("MORE"))
            assertFalse(cold.expressiveHiddenBottomButtonsMerge.value.contains("MORE"))
            assertEquals(5, AppSettings.MAX_EXPRESSIVE_BOTTOM_PINS_NORMAL + AppSettings.fixedBottomButtonsNormal.size)
        } finally {
            val edit = prefs.edit(); originals.forEach { (key, value) -> if (value == null) edit.remove(key) else edit.putString(key, value) }; edit.commit()
        }
    }
}
