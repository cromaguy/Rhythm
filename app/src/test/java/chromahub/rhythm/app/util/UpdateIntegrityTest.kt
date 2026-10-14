// SPDX-License-Identifier: GPL-3.0-or-later
package chromahub.rhythm.app.util

import chromahub.rhythm.app.network.GitHubAsset
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class UpdateIntegrityTest {
    private fun asset(name: String) = GitHubAsset(1, name, "https://github.com/test/$name", "application/vnd.android.package-archive", 10, "uploaded")
    @Test fun `fork APK names select the installed package type instead of opening a browser`() {
        val assets = listOf(asset("T2Rhythm-5.5.498.1305-debug.apk"), asset("T2Rhythm-5.5.498.1305.apk"))
        assertEquals(assets[1], UpdateIntegrity.selectAsset(assets, "github", false, listOf("arm64-v8a")))
        assertEquals(assets[0], UpdateIntegrity.selectAsset(assets, "github", true, listOf("arm64-v8a")))
        assertNull(UpdateIntegrity.selectAsset(assets, "fdroid", false, listOf("arm64-v8a")))
    }
    @Test fun `unsupported ABIs and debug APKs cannot enter release update selection`() {
        assertNull(UpdateIntegrity.selectAsset(listOf(asset("Rhythm-githubRelease-x86_64.apk"), asset("Rhythm-githubDebug.apk")), "github", false, listOf("arm64-v8a")))
    }
    @Test fun `truncated and modified downloads fail independently of metadata trust`() {
        val file = File.createTempFile("integrity", ".apk")
        try {
            file.writeText("verified apk"); val checksum = UpdateIntegrity.fileDigest(file)
            UpdateIntegrity.verifyBytes(file, file.length(), "sha256:$checksum")
            assertTrue(runCatching { UpdateIntegrity.verifyBytes(file, file.length() + 1, checksum) }.isFailure)
            file.writeText("modified apk")
            assertTrue(runCatching { UpdateIntegrity.verifyBytes(file, file.length(), checksum) }.isFailure)
            assertTrue(runCatching { UpdateIntegrity.verifyBytes(file, file.length(), null) }.isFailure)
        } finally { file.delete() }
    }
}
