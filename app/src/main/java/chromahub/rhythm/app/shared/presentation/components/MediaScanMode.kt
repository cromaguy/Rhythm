/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.shared.presentation.components

/**
 * Enum representing the media scanning mode for filtering music library
 */
enum class MediaScanMode {
    /**
     * Blacklist mode: Exclude specified songs/folders, include everything else
     */
    BLACKLIST,
    
    /**
     * Whitelist mode: Include only specified songs/folders, exclude everything else
     */
    WHITELIST,
    HYBRID
}
