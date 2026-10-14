// SPDX-License-Identifier: GPL-3.0-or-later
package chromahub.rhythm.app.shared.data.model

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.lang.reflect.Modifier
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LocalModeMigrationTest {
    @Test fun oldGoSettingIsNormalizedWithoutClearingOtherPreferences() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val prefs = context.getSharedPreferences("rhythm_preferences", android.content.Context.MODE_PRIVATE)
        val oldMode = prefs.getString("app_mode", null)
        val sentinel = "go_removal_test_music_data"
        val oldSentinel = prefs.getString(sentinel, null)
        val instance = AppSettings::class.java.declaredFields.single {
            Modifier.isStatic(it.modifiers) && it.type == AppSettings::class.java
        }.apply { isAccessible = true }
        val originalInstance = instance.get(null)
        try {
            prefs.edit().putString("app_mode", "STREAMING").putString(sentinel, "음악 기록 보존").commit()
            instance.set(null, null)
            val migrated = AppSettings.getInstance(context)
            assertEquals("LOCAL", migrated.appMode.value)
            assertEquals("LOCAL", prefs.getString("app_mode", null))
            assertEquals("음악 기록 보존", prefs.getString(sentinel, null))
            migrated.setAppMode("STREAMING")
            assertEquals("LOCAL", migrated.appMode.value)
            assertEquals("LOCAL", prefs.getString("app_mode", null))
        } finally {
            val editor = prefs.edit()
            if (oldMode == null) editor.remove("app_mode") else editor.putString("app_mode", oldMode)
            if (oldSentinel == null) editor.remove(sentinel) else editor.putString(sentinel, oldSentinel)
            editor.commit()
            instance.set(null, originalInstance)
        }
    }
}
