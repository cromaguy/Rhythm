/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.features.local.presentation.screens.onboarding

enum class OnboardingStep {
    WELCOME,
    APP_MODE_CHOICE, // Step to select Local vs Streaming (GO) mode
    STREAMING_SERVICE_CHOICE, // Step to choose streaming provider (Jellyfin vs Subsonic/Navidrome)
    STREAMING_SETUP, // Step to setup credentials for streaming servers
    PERMISSIONS,
    RHYTHM_GUARD, // Core hearing safety setup
    UPDATER, // Core update preferences
    FULL_TOUR_PROMPT, // Ask user whether to continue full setup tour
    NOTIFICATIONS, // Legacy step (not shown in onboarding flow)
    BACKUP_RESTORE, // Step for backup and restore setup
    AUDIO_PLAYBACK, // Step for audio and playback settings
    THEMING,
    PLAYER_THEME_CHOICE, // Step to choose between player theme and miniplayer theme
    GESTURES, // Step for gesture controls introduction
    LIBRARY_SETUP, // Legacy step (not shown in onboarding flow)
    MEDIA_SCAN, // Core step for choosing blacklist/whitelist filtering mode
    WIDGETS, // Step for home screen widget setup
    INTEGRATIONS, // Step for API services, scrobbling, Discord presence
    RHYTHM_STATS, // Step for listening statistics introduction
    SETUP_FINISHED, // Step showing setup completion with finish button
    COMPLETE
}

sealed class PermissionScreenState {
    object Loading : PermissionScreenState()
    object PermissionsRequired : PermissionScreenState()
    object ShowRationale : PermissionScreenState()
    object RedirectToSettings : PermissionScreenState()
    object PermissionsGranted : PermissionScreenState()
}
