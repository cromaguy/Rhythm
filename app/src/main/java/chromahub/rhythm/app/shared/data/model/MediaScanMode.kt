/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.shared.data.model

enum class MediaScanMode(val value: String) {
    BLACKLIST("blacklist"),
    WHITELIST("whitelist"),
    HYBRID("hybrid");

    val usesWhitelist: Boolean get() = this != BLACKLIST
    val usesBlacklist: Boolean get() = this != WHITELIST

    companion object {
        fun fromValue(value: String): MediaScanMode =
            entries.firstOrNull { it.value == value } ?: BLACKLIST
    }
}
