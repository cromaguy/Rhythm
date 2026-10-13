// SPDX-License-Identifier: GPL-3.0-or-later
package chromahub.rhythm.app.core.domain.scan

import chromahub.rhythm.app.shared.data.model.MediaScanMode

/** A whitelist selects the scope; a blacklist vetoes entries inside that scope in HYBRID. */
class LibraryFilterPolicy(
    private val mode: MediaScanMode,
    blacklistedIds: Collection<String>, blacklistedFolders: Collection<String>,
    whitelistedIds: Collection<String>, whitelistedFolders: Collection<String>,
) {
    private val blocked = blacklistedIds.toSet()
    private val allowed = whitelistedIds.toSet()
    private val blockedFolders = blacklistedFolders.map(::normalize)
    private val allowedFolders = whitelistedFolders.map(::normalize)
    fun includes(id: String, path: String?): Boolean {
        val normalized = path?.let(::normalize)
        fun inFolders(folders: List<String>) = normalized != null && folders.any {
            normalized == it || normalized.startsWith("$it/")
        }
        if (mode.usesWhitelist && id !in allowed && !inFolders(allowedFolders)) return false
        if (mode.usesBlacklist && (id in blocked || inFolders(blockedFolders))) return false
        return true
    }
    companion object {
        private fun normalize(path: String): String {
            val clean = path.trim().replace('\\', '/').replace(Regex("/+"), "/").trimEnd('/')
            val canonical = if (clean.startsWith("/")) runCatching { java.io.File(clean).canonicalPath }.getOrDefault(clean) else clean
            return canonical.lowercase()
        }
    }
}
