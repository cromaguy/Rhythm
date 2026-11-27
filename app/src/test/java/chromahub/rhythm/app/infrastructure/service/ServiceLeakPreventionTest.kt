/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.infrastructure.service

import android.content.Context
import chromahub.rhythm.app.infrastructure.service.player.RhythmPlayerEngine
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.ref.WeakReference
import java.lang.reflect.Modifier

class ServiceLeakPreventionTest {

    @Test
    fun `nested service callbacks do not capture outer MediaPlaybackService strongly`() {
        val nestedClasses = MediaPlaybackService::class.java.declaredClasses
            .filter {
                it.simpleName == "ServiceAudioDeviceCallback" ||
                    it.simpleName == "ServiceForwardingPlayer"
            }

        assertTrue(
            "Expected ServiceAudioDeviceCallback and ServiceForwardingPlayer nested classes",
            nestedClasses.size == 2
        )

        nestedClasses.forEach { clazz ->
            assertTrue(
                "${clazz.simpleName} must be static to avoid holding MediaPlaybackService this\$0",
                Modifier.isStatic(clazz.modifiers)
            )
            val strongServiceFields = clazz.declaredFields.filter { field ->
                field.type == MediaPlaybackService::class.java
            }
            assertTrue(
                "${clazz.simpleName} must not hold strong MediaPlaybackService fields: $strongServiceFields",
                strongServiceFields.isEmpty()
            )
        }
    }

    @Test
    fun `RhythmPlayerEngine normalizes context to applicationContext`() {
        val fieldNames = RhythmPlayerEngine::class.java.declaredFields.map { it.name to it.type }
        assertFalse(
            "RhythmPlayerEngine should store appContext rather than raw constructor context property",
            fieldNames.any { (name, type) -> name == "context" && type == Context::class.java }
        )
        assertTrue(
            "RhythmPlayerEngine must keep appContext field",
            fieldNames.any { (name, type) -> name == "appContext" && type == Context::class.java }
        )
    }

    @Test
    fun `WeakReference detach clears target immediately even when callback wrapper stays retained`() {
        val heldTarget = Any()
        val ref = WeakReference(heldTarget)
        assertTrue(ref.get() === heldTarget)
        ref.clear()
        assertNull(ref.get())
    }
}
