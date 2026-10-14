// SPDX-License-Identifier: GPL-3.0-or-later
package chromahub.rhythm.app.util

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import chromahub.rhythm.app.network.GitHubAsset
import java.io.File
import java.security.MessageDigest

object UpdateIntegrity {
    fun sha256(text: String?): String? = text?.trim()?.lowercase()?.removePrefix("sha256:")?.takeIf { it.matches(Regex("[a-f0-9]{64}")) }
    fun selectAsset(assets: List<GitHubAsset>, flavor: String, debug: Boolean, abis: List<String>): GitHubAsset? {
        val fork = Regex("^T2Rhythm-[0-9.]+${if (debug) "-debug" else ""}\\.apk$", RegexOption.IGNORE_CASE)
        val supported = assets.filter { asset ->
            val name = asset.name.lowercase()
            val suffixes = listOf("arm64-v8a", "armeabi-v7a", "x86_64", "x86")
            val abi = suffixes.firstOrNull { name.contains(it) }
            asset.state == "uploaded" && name.endsWith(".apk") && name.contains("debug") == debug &&
                (abi == null || abi in abis) && ((flavor == "github" && fork.matches(asset.name)) ||
                name.contains("${flavor}${if (debug) "debug" else "release"}") || name.contains("-$flavor-"))
        }
        return supported.firstOrNull { fork.matches(it.name) } ?: supported.firstOrNull { it.name.contains("universal", true) }
            ?: supported.firstOrNull { asset -> listOf("arm64-v8a", "armeabi-v7a", "x86_64", "x86").none { asset.name.contains(it, true) } }
            ?: abis.firstNotNullOfOrNull { abi -> supported.firstOrNull { it.name.contains(abi, true) } }
    }
    fun fileDigest(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input -> val buffer = ByteArray(8192); while (true) { val size = input.read(buffer); if (size < 0) break; digest.update(buffer, 0, size) } }
        return digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
    }
    fun verifyBytes(file: File, expectedSize: Long, digest: String?) {
        require(expectedSize > 0 && file.length() == expectedSize) { "APK size mismatch" }
        val expected = sha256(digest) ?: error("Missing SHA256")
        require(fileDigest(file) == expected) { "APK checksum mismatch" }
    }
    @Suppress("DEPRECATION")
    fun verifyPackage(context: Context, file: File, expectedVersion: Int? = null) {
        val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES or PackageManager.GET_SIGNATURES else PackageManager.GET_SIGNATURES
        val downloaded = context.packageManager.getPackageArchiveInfo(file.path, flags) ?: error("Invalid APK")
        val installed = context.packageManager.getPackageInfo(context.packageName, flags)
        require(downloaded.packageName == context.packageName) { "APK package mismatch" }
        val version = if (Build.VERSION.SDK_INT >= 28) downloaded.longVersionCode else downloaded.versionCode.toLong()
        val installedVersion = if (Build.VERSION.SDK_INT >= 28) installed.longVersionCode else installed.versionCode.toLong()
        require(version >= installedVersion && (expectedVersion == null || expectedVersion <= 0 || version == expectedVersion.toLong())) { "APK version mismatch" }
        fun signatures(info: android.content.pm.PackageInfo): Set<String> = (if (Build.VERSION.SDK_INT >= 28) info.signingInfo?.apkContentsSigners?.takeIf { it.isNotEmpty() } ?: info.signatures else info.signatures)
            .orEmpty().map { MessageDigest.getInstance("SHA-256").digest(it.toByteArray()).joinToString("") { byte -> "%02x".format(byte.toInt() and 255) } }.toSet()
        val actual = signatures(downloaded); require(actual.isNotEmpty() && actual == signatures(installed)) { "APK signing certificate mismatch" }
    }
}
