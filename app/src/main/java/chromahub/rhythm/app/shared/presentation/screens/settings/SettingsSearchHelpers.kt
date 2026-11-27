/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.shared.presentation.screens.settings

import chromahub.rhythm.app.shared.data.model.AppSettings
import chromahub.rhythm.app.shared.data.model.settings.PlaybackPreferencesStore
import chromahub.rhythm.app.shared.data.model.settings.PresetsPreferencesStore
import chromahub.rhythm.app.shared.data.model.settings.UiPreferencesStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Coordinates one-shot visual highlight targets when navigating from Settings Search into
 * any destination settings screen.
 */
object SettingHighlightController {
    private val _highlightedKey = MutableStateFlow<String?>(null)
    val highlightedKey: StateFlow<String?> = _highlightedKey.asStateFlow()

    private val _highlightedTitle = MutableStateFlow<String?>(null)
    val highlightedTitle: StateFlow<String?> = _highlightedTitle.asStateFlow()

    fun requestHighlight(settingKey: String?, title: String?) {
        val cleanKey = settingKey?.trim()?.takeIf { it.isNotEmpty() }
        val cleanTitle = title?.trim()?.takeIf { it.isNotEmpty() }
        _highlightedKey.value = cleanKey
        _highlightedTitle.value = cleanTitle
    }

    fun clear() {
        _highlightedKey.value = null
        _highlightedTitle.value = null
    }

    fun matches(
        targetKey: String?,
        targetTitle: String?,
        itemKey: String?,
        itemTitle: String?
    ): Boolean {
        if (!targetKey.isNullOrBlank() && !itemKey.isNullOrBlank() && targetKey == itemKey) {
            return true
        }
        return !targetTitle.isNullOrBlank() &&
            !itemTitle.isNullOrBlank() &&
            targetTitle.equals(itemTitle, ignoreCase = true)
    }
}

/**
 * Binding for rendering inline boolean switches directly inside Settings Search results.
 */
data class InlineBooleanSettingBinding(
    val key: String,
    val stateFlow: StateFlow<Boolean>,
    val onToggle: (Boolean) -> Unit
)

object SettingsInlineToggleRegistry {

    fun supportedKeys(): Set<String> = setOf(
        "albumBottomSheetGradientBlur",
        "albumHideAbout",
        "amoledTheme",
        "artistSeparatorEnabled",
        "audioOffloadEnabled",
        "autoBackupEnabled",
        "autoCheckForUpdates",
        "autoHideLyricsControls",
        "automix_bpm_sync_enabled",
        "agsl_fluid_backdrop_enabled",
        "alac_decoder_enabled",
        "batterySaverDisableAutoFetchArtwork",
        "batterySaverDisableHaptics",
        "batterySaverDisableLosslessArtwork",
        "batterySaverDisableMarquee",
        "batterySaverEnableOffload",
        "bluetoothLyricsEnabled",
        "broadcastStatusEnabled",
        "clearQueueOnNewSong",
        "crossfade",
        "crossfadeOnSkip",
        "crossfadeRepeatOne",
        "darkMode",
        "double_precision_peq_enabled",
        "enableAlbumEditing",
        "expressiveShapesEnabled",
        "external_player_isolate_preview",
        "floatingNavigationBar",
        "forcePlayerCompactMode",
        "gaplessPlayback",
        "hapticFeedback",
        "hidePlayedQueueSongs",
        "homeDiscoverShowAlbumName",
        "homeDiscoverShowArtistName",
        "homeDiscoverShowGradient",
        "homeDiscoverShowPlayButton",
        "homeDiscoverShowYear",
        "keepScreenOnLyrics",
        "keepShuffleOnSelection",
        "libraryCombineDiscs",
        "losslessArtwork",
        "lyricBold",
        "lyricNoAnimation",
        "lyricsApiFallbackRetry",
        "main_thread_stall_watchdog",
        "monoAudioEnabled",
        "music_haptics_enabled",
        "playerAmbientBackdropEnabled",
        "playerAmbientInfiniteZoom",
        "playerProgressThumbRotate",
        "playerShowArtBelowLyrics",
        "playerShowGradientOverlay",
        "playerShowSongInfoOnArtwork",
        "preferSongArtwork",
        "queuePersistenceEnabled",
        "repeatModePersistence",
        "replayGain",
        "replayGainDrc",
        "respectAlbumOnPlay",
        "resumeOnDeviceReconnect",
        "showKeyboardOnSearchOpen",
        "showLibraryBottomBarAlways",
        "showLyrics",
        "showLyricsBackgroundArtwork",
        "showLyricsRomanization",
        "showLyricsTranslation",
        "showRemainingTime",
        "showSettingsSuggestions",
        "shuffleModePersistence",
        "shuffleUsesExoplayer",
        "skipSilenceEnabled",
        "status_bar_ticker_enabled",
        "stopPlaybackOnAppClose",
        "stopPlaybackOnZeroVolume",
        "surround_downmix_enabled",
        "trackErrorCheckerEnabled",
        "translationAutoWord",
        "trimLyrics",
        "unifiedLibraryEnabled",
        "updatesEnabled",
        "useDefaultPlaybackSpeed",
        "useHoursInTimeFormat",
        "useSystemTheme",
        "useSystemVolume"
    )

    internal fun resolveStoreToggle(
        key: String,
        playbackPrefs: PlaybackPreferencesStore,
        uiPrefs: UiPreferencesStore,
        presetsPrefs: PresetsPreferencesStore
    ): InlineBooleanSettingBinding? = when (key) {
        "alac_decoder_enabled" -> InlineBooleanSettingBinding(key, playbackPrefs.alacDecoderEnabled, playbackPrefs::setAlacDecoderEnabled)
        "automix_bpm_sync_enabled" -> InlineBooleanSettingBinding(key, playbackPrefs.autoMixBpmSyncEnabled, playbackPrefs::setAutoMixBpmSyncEnabled)
        "agsl_fluid_backdrop_enabled" -> InlineBooleanSettingBinding(key, uiPrefs.agslFluidBackdropEnabled, uiPrefs::setAgslFluidBackdropEnabled)
        "double_precision_peq_enabled" -> InlineBooleanSettingBinding(key, presetsPrefs.doublePrecisionPeqEnabled, presetsPrefs::setDoublePrecisionPeqEnabled)
        "music_haptics_enabled" -> InlineBooleanSettingBinding(key, playbackPrefs.musicHapticsEnabled, playbackPrefs::setMusicHapticsEnabled)
        "status_bar_ticker_enabled" -> InlineBooleanSettingBinding(key, uiPrefs.statusBarTickerEnabled, uiPrefs::setStatusBarTickerEnabled)
        "surround_downmix_enabled" -> InlineBooleanSettingBinding(key, playbackPrefs.surroundDownmixEnabled, playbackPrefs::setSurroundDownmixEnabled)
        else -> null
    }

    fun resolve(appSettings: AppSettings, settingKey: String?): InlineBooleanSettingBinding? {
        val key = settingKey?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        resolveStoreToggle(
            key = key,
            playbackPrefs = appSettings.playbackPrefs,
            uiPrefs = appSettings.uiPrefs,
            presetsPrefs = appSettings.presetsPrefs
        )?.let { return it }
        return when (key) {
            "albumBottomSheetGradientBlur" -> InlineBooleanSettingBinding(key, appSettings.albumBottomSheetGradientBlur, appSettings::setAlbumBottomSheetGradientBlur)
            "albumHideAbout" -> InlineBooleanSettingBinding(key, appSettings.albumHideAbout, appSettings::setAlbumHideAbout)
            "amoledTheme" -> InlineBooleanSettingBinding(key, appSettings.amoledTheme, appSettings::setAmoledTheme)
            "artistSeparatorEnabled" -> InlineBooleanSettingBinding(key, appSettings.artistSeparatorEnabled, appSettings::setArtistSeparatorEnabled)
            "audioOffloadEnabled" -> InlineBooleanSettingBinding(key, appSettings.audioOffloadEnabled, appSettings::setAudioOffloadEnabled)
            "autoBackupEnabled" -> InlineBooleanSettingBinding(key, appSettings.autoBackupEnabled, appSettings::setAutoBackupEnabled)
            "autoCheckForUpdates" -> InlineBooleanSettingBinding(key, appSettings.autoCheckForUpdates, appSettings::setAutoCheckForUpdates)
            "autoHideLyricsControls" -> InlineBooleanSettingBinding(key, appSettings.autoHideLyricsControls, appSettings::setAutoHideLyricsControls)
            "batterySaverDisableAutoFetchArtwork" -> InlineBooleanSettingBinding(key, appSettings.batterySaverDisableAutoFetchArtwork, appSettings::setBatterySaverDisableAutoFetchArtwork)
            "batterySaverDisableHaptics" -> InlineBooleanSettingBinding(key, appSettings.batterySaverDisableHaptics, appSettings::setBatterySaverDisableHaptics)
            "batterySaverDisableLosslessArtwork" -> InlineBooleanSettingBinding(key, appSettings.batterySaverDisableLosslessArtwork, appSettings::setBatterySaverDisableLosslessArtwork)
            "batterySaverDisableMarquee" -> InlineBooleanSettingBinding(key, appSettings.batterySaverDisableMarquee, appSettings::setBatterySaverDisableMarquee)
            "batterySaverEnableOffload" -> InlineBooleanSettingBinding(key, appSettings.batterySaverEnableOffload, appSettings::setBatterySaverEnableOffload)
            "bluetoothLyricsEnabled" -> InlineBooleanSettingBinding(key, appSettings.bluetoothLyricsEnabled, appSettings::setBluetoothLyricsEnabled)
            "broadcastStatusEnabled" -> InlineBooleanSettingBinding(key, appSettings.broadcastStatusEnabled, appSettings::setBroadcastStatusEnabled)
            "clearQueueOnNewSong" -> InlineBooleanSettingBinding(key, appSettings.clearQueueOnNewSong, appSettings::setClearQueueOnNewSong)
            "crossfade" -> InlineBooleanSettingBinding(key, appSettings.crossfade, appSettings::setCrossfade)
            "crossfadeOnSkip" -> InlineBooleanSettingBinding(key, appSettings.crossfadeOnSkip, appSettings::setCrossfadeOnSkip)
            "crossfadeRepeatOne" -> InlineBooleanSettingBinding(key, appSettings.crossfadeRepeatOne, appSettings::setCrossfadeRepeatOne)
            "darkMode" -> InlineBooleanSettingBinding(key, appSettings.darkMode, appSettings::setDarkMode)
            "enableAlbumEditing" -> InlineBooleanSettingBinding(key, appSettings.enableAlbumEditing, appSettings::setEnableAlbumEditing)
            "expressiveShapesEnabled" -> InlineBooleanSettingBinding(key, appSettings.expressiveShapesEnabled, appSettings::setExpressiveShapesEnabled)
            "external_player_isolate_preview" -> InlineBooleanSettingBinding(key, appSettings.externalPlayerIsolatePreview, appSettings::setExternalPlayerIsolatePreview)
            "floatingNavigationBar" -> InlineBooleanSettingBinding(key, appSettings.floatingNavigationBar, appSettings::setFloatingNavigationBar)
            "forcePlayerCompactMode" -> InlineBooleanSettingBinding(key, appSettings.forcePlayerCompactMode, appSettings::setForcePlayerCompactMode)
            "gaplessPlayback" -> InlineBooleanSettingBinding(key, appSettings.gaplessPlayback, appSettings::setGaplessPlayback)
            "hapticFeedback" -> InlineBooleanSettingBinding(key, appSettings.hapticFeedbackEnabled, appSettings::setHapticFeedbackEnabled)
            "hidePlayedQueueSongs" -> InlineBooleanSettingBinding(key, appSettings.hidePlayedQueueSongs, appSettings::setHidePlayedQueueSongs)
            "homeDiscoverShowAlbumName" -> InlineBooleanSettingBinding(key, appSettings.homeDiscoverShowAlbumName, appSettings::setHomeDiscoverShowAlbumName)
            "homeDiscoverShowArtistName" -> InlineBooleanSettingBinding(key, appSettings.homeDiscoverShowArtistName, appSettings::setHomeDiscoverShowArtistName)
            "homeDiscoverShowGradient" -> InlineBooleanSettingBinding(key, appSettings.homeDiscoverShowGradient, appSettings::setHomeDiscoverShowGradient)
            "homeDiscoverShowPlayButton" -> InlineBooleanSettingBinding(key, appSettings.homeDiscoverShowPlayButton, appSettings::setHomeDiscoverShowPlayButton)
            "homeDiscoverShowYear" -> InlineBooleanSettingBinding(key, appSettings.homeDiscoverShowYear, appSettings::setHomeDiscoverShowYear)
            "keepScreenOnLyrics" -> InlineBooleanSettingBinding(key, appSettings.keepScreenOnLyrics, appSettings::setKeepScreenOnLyrics)
            "keepShuffleOnSelection" -> InlineBooleanSettingBinding(key, appSettings.keepShuffleOnSelection, appSettings::setKeepShuffleOnSelection)
            "libraryCombineDiscs" -> InlineBooleanSettingBinding(key, appSettings.libraryCombineDiscs, appSettings::setLibraryCombineDiscs)
            "losslessArtwork" -> InlineBooleanSettingBinding(key, appSettings.losslessArtwork, appSettings::setLosslessArtwork)
            "lyricBold" -> InlineBooleanSettingBinding(key, appSettings.lyricBold, appSettings::setLyricBold)
            "lyricNoAnimation" -> InlineBooleanSettingBinding(key, appSettings.lyricNoAnimation, appSettings::setLyricNoAnimation)
            "lyricsApiFallbackRetry" -> InlineBooleanSettingBinding(key, appSettings.lyricsApiFallbackRetry, appSettings::setLyricsApiFallbackRetry)
            "main_thread_stall_watchdog" -> InlineBooleanSettingBinding(key, appSettings.mainThreadStallMonitorEnabled) { enabled ->
                appSettings.setMainThreadStallMonitorEnabled(enabled)
                chromahub.rhythm.app.RhythmApplication.instance.setStallMonitorEnabled(enabled)
            }
            "monoAudioEnabled" -> InlineBooleanSettingBinding(key, appSettings.monoAudioEnabled, appSettings::setMonoAudioEnabled)
            "playerAmbientBackdropEnabled" -> InlineBooleanSettingBinding(key, appSettings.playerAmbientBackdropEnabled, appSettings::setPlayerAmbientBackdropEnabled)
            "playerAmbientInfiniteZoom" -> InlineBooleanSettingBinding(key, appSettings.playerAmbientInfiniteZoom, appSettings::setPlayerAmbientInfiniteZoom)
            "playerProgressThumbRotate" -> InlineBooleanSettingBinding(key, appSettings.playerProgressThumbRotate, appSettings::setPlayerProgressThumbRotate)
            "playerShowArtBelowLyrics" -> InlineBooleanSettingBinding(key, appSettings.playerShowArtBelowLyrics, appSettings::setPlayerShowArtBelowLyrics)
            "playerShowGradientOverlay" -> InlineBooleanSettingBinding(key, appSettings.playerShowGradientOverlay, appSettings::setPlayerShowGradientOverlay)
            "playerShowSongInfoOnArtwork" -> InlineBooleanSettingBinding(key, appSettings.playerShowSongInfoOnArtwork, appSettings::setPlayerShowSongInfoOnArtwork)
            "preferSongArtwork" -> InlineBooleanSettingBinding(key, appSettings.preferSongArtwork, appSettings::setPreferSongArtwork)
            "queuePersistenceEnabled" -> InlineBooleanSettingBinding(key, appSettings.queuePersistenceEnabled, appSettings::setQueuePersistenceEnabled)
            "repeatModePersistence" -> InlineBooleanSettingBinding(key, appSettings.repeatModePersistence, appSettings::setRepeatModePersistence)
            "replayGain" -> InlineBooleanSettingBinding(key, appSettings.replayGain, appSettings::setReplayGain)
            "replayGainDrc" -> InlineBooleanSettingBinding(key, appSettings.replayGainDrc, appSettings::setReplayGainDrc)
            "respectAlbumOnPlay" -> InlineBooleanSettingBinding(key, appSettings.respectAlbumOnPlay, appSettings::setRespectAlbumOnPlay)
            "resumeOnDeviceReconnect" -> InlineBooleanSettingBinding(key, appSettings.resumeOnDeviceReconnect, appSettings::setResumeOnDeviceReconnect)
            "showKeyboardOnSearchOpen" -> InlineBooleanSettingBinding(key, appSettings.showKeyboardOnSearchOpen, appSettings::setShowKeyboardOnSearchOpen)
            "showLibraryBottomBarAlways" -> InlineBooleanSettingBinding(key, appSettings.showLibraryBottomBarAlways, appSettings::setShowLibraryBottomBarAlways)
            "showLyrics" -> InlineBooleanSettingBinding(key, appSettings.showLyrics, appSettings::setShowLyrics)
            "showLyricsBackgroundArtwork" -> InlineBooleanSettingBinding(key, appSettings.showLyricsBackgroundArtwork, appSettings::setShowLyricsBackgroundArtwork)
            "showLyricsRomanization" -> InlineBooleanSettingBinding(key, appSettings.showLyricsRomanization, appSettings::setShowLyricsRomanization)
            "showLyricsTranslation" -> InlineBooleanSettingBinding(key, appSettings.showLyricsTranslation, appSettings::setShowLyricsTranslation)
            "showRemainingTime" -> InlineBooleanSettingBinding(key, appSettings.showRemainingTime, appSettings::setShowRemainingTime)
            "showSettingsSuggestions" -> InlineBooleanSettingBinding(key, appSettings.showSettingsSuggestions, appSettings::setShowSettingsSuggestions)
            "shuffleModePersistence" -> InlineBooleanSettingBinding(key, appSettings.shuffleModePersistence, appSettings::setShuffleModePersistence)
            "shuffleUsesExoplayer" -> InlineBooleanSettingBinding(key, appSettings.shuffleUsesExoplayer, appSettings::setShuffleUsesExoplayer)
            "skipSilenceEnabled" -> InlineBooleanSettingBinding(key, appSettings.skipSilenceEnabled, appSettings::setSkipSilenceEnabled)
            "stopPlaybackOnAppClose" -> InlineBooleanSettingBinding(key, appSettings.stopPlaybackOnAppClose, appSettings::setStopPlaybackOnAppClose)
            "stopPlaybackOnZeroVolume" -> InlineBooleanSettingBinding(key, appSettings.stopPlaybackOnZeroVolume, appSettings::setStopPlaybackOnZeroVolume)
            "trackErrorCheckerEnabled" -> InlineBooleanSettingBinding(key, appSettings.trackErrorCheckerEnabled, appSettings::setTrackErrorCheckerEnabled)
            "translationAutoWord" -> InlineBooleanSettingBinding(key, appSettings.translationAutoWord, appSettings::setTranslationAutoWord)
            "trimLyrics" -> InlineBooleanSettingBinding(key, appSettings.trimLyrics, appSettings::setTrimLyrics)
            "unifiedLibraryEnabled" -> InlineBooleanSettingBinding(key, appSettings.unifiedLibraryEnabled, appSettings::setUnifiedLibraryEnabled)
            "updatesEnabled" -> InlineBooleanSettingBinding(key, appSettings.updatesEnabled, appSettings::setUpdatesEnabled)
            "useDefaultPlaybackSpeed" -> InlineBooleanSettingBinding(key, appSettings.useDefaultPlaybackSpeed, appSettings::setUseDefaultPlaybackSpeed)
            "useHoursInTimeFormat" -> InlineBooleanSettingBinding(key, appSettings.useHoursInTimeFormat, appSettings::setUseHoursInTimeFormat)
            "useSystemTheme" -> InlineBooleanSettingBinding(key, appSettings.useSystemTheme, appSettings::setUseSystemTheme)
            "useSystemVolume" -> InlineBooleanSettingBinding(key, appSettings.useSystemVolume, appSettings::setUseSystemVolume)
            else -> null
        }
    }
}
