# Changelog

All notable changes to Rhythm will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- 

## [5.6.506.1360] - 2026-11-24

### Added
- **Rootless ViPER4Android DSP Engine**: Integrated the ViPER4Android audio engine directly into Rhythm's 32-bit floating-point audio pipeline without requiring root privileges ([#704](https://github.com/cromaguy/Rhythm/pull/704)).
  - **ViPER Clarity**: Real-time high-frequency reconstruction, dynamic range enhancement, and punchy bass control.
  - **ViPER-DDC**: Digital Dynamic Correction providing precise frequency response correction profiles for hundreds of popular headphone models.
  - **Convolver**: Impulse Response (IRS) audio processing for realistic acoustic environments, studio spaces, and analog audio hardware simulation.
  - **Dedicated ViPER Settings**: Brand-new equalizer screen with grouped control sections, profile import/export, and real-time DSP telemetry.
- **Revamped Miniplayer & Motion**: Redesigned the miniplayer with Material 3 expressive motion tokens, smooth morphing play/pause buttons, and intuitive carousel swipe gestures for switching tracks.
- **External Playback Screen**: Remote and external playback sessions (Bluetooth, Cast) now open in an interactive bottom sheet with synchronized lyrics, playback controls, button groups, and tactile haptic feedback.
- **Server "Download All" for Rhythm Go**: Added a one-tap action to download entire server playlists and albums from Subsonic and Jellyfin servers ([#696](https://github.com/cromaguy/Rhythm/pull/696)).
- **Offline Badges & Audio Format Chips**: Go playlists now display offline download status badges and format badges (FLAC, MP3, AAC, Opus) ([#698](https://github.com/cromaguy/Rhythm/pull/698)).
- **Embedded Tag Lyrics Extraction**: Added support for reading and parsing embedded synchronized and unsynchronized lyrics from Vorbis comments (FLAC, Ogg) and MP4 atoms (M4A) ([#687](https://github.com/cromaguy/Rhythm/issues/687)).
- **Android 17+ Local Network Support**: Added automated local network permission handling and resolved discovery popups/timeouts when searching for local streaming servers on Android 17+.
- **Standard Media Intent Filters**: Registered standard music player and media view intent filters to open and play audio files directly from external file managers ([#678](https://github.com/cromaguy/Rhythm/issues/678)).
- **Manage Folders Grouping**: Rebuilt the folder management sheet with structured settings groups and cleaner whitelist navigation.

### Changed
- **Action Buttons Unification**: Replaced inconsistent action rows with unified `RhythmDetailActionButton` components across album and playlist views.
- **Ambient Lighting & Empty States**: Adjusted default player ambient intensity to 60% and properly centered empty playlist placeholders.
- **Responsive Tablet & Foldable UI**: Restored the action row on portrait tablets and unfolded foldable displays.
- **Language Picker Completion**: Updated language switcher to dynamically show live translation completion percentages for all locales.
- **Translations**: Updated community translations across multiple languages including Russian, Greek, Armenian, Chinese, Japanese, and Spanish.

### Fixed
- **Multi-Disc Album Grouping**: Unified folder and multi-disc grouping logic to prevent split discs from generating duplicate album entries ([#692](https://github.com/cromaguy/Rhythm/issues/692)).
- **File Explorer Playback**: Clicking an audio file in folder view now plays within its folder context instead of auto-enqueuing an entire album ([#708](https://github.com/cromaguy/Rhythm/issues/708)).
- **File Explorer Traversal Lag**: Pruned non-whitelisted storage directories during navigation, eliminating path resolution lag and freezes ([#710](https://github.com/cromaguy/Rhythm/issues/710)).
- **Synchronized Lyrics Jitter**: Enforced monotonic playback progress tracking to prevent lyric highlights from jittering or jumping during playback ([#685](https://github.com/cromaguy/Rhythm/issues/685)).
- **Filtered Playback Scoping**: Tapping play on search results or genre filtered lists now strictly scopes playback to matching songs rather than the entire library ([#684](https://github.com/cromaguy/Rhythm/issues/684)).
- **Repeat Mode & Notification Sync**: Corrected repeat mode icon mapping, eliminated repeat-one queue flickering, and synchronized MediaSession notification repeat states ([#671](https://github.com/cromaguy/Rhythm/issues/671), [#672](https://github.com/cromaguy/Rhythm/issues/672), [#674](https://github.com/cromaguy/Rhythm/issues/674)).
- **Artwork Gestures & Collisions**: Restored swipe and tap gestures on player artwork ([#615](https://github.com/cromaguy/Rhythm/issues/615)), prevented artist artwork collisions on collaboration tracks ([#670](https://github.com/cromaguy/Rhythm/issues/670)), and resolved double URI decoding for paths containing percent signs ([#706](https://github.com/cromaguy/Rhythm/issues/706)).
- **Offline Playlist & Download Persistence**: Cached server playlists now remain visible offline and are protected against being overwritten by empty lists ([#693](https://github.com/cromaguy/Rhythm/pull/693)); non-MP3 downloaded tracks persist properly across restarts ([#700](https://github.com/cromaguy/Rhythm/pull/700)).
- **Playlist Concurrency & Emojis**: Fixed playlist clobbering during rapid modifications or creation race conditions ([#414](https://github.com/cromaguy/Rhythm/issues/414)) and added full emoji support in playlist names ([#638](https://github.com/cromaguy/Rhythm/issues/638), [#660](https://github.com/cromaguy/Rhythm/issues/660)).
- **Search Query Persistence**: Preserved search bar queries and filter states across device rotation ([#688](https://github.com/cromaguy/Rhythm/issues/688)).
- **Widget Control Clipping**: Constrained responsive widget scaling by height to prevent playback buttons from being clipped on compact home screen grids ([#541](https://github.com/cromaguy/Rhythm/issues/541)).
- **Network Boundaries & Data Restrictions**: Enforced local-only restrictions, respected metered network policies, and added circuit breakers to prevent runaway artwork and lyrics fetching ([#656](https://github.com/cromaguy/Rhythm/issues/656)).
- **Media Scanner Whitelist**: Enforced strict folder boundary matching to prevent unauthorized directory indexing ([#703](https://github.com/cromaguy/Rhythm/issues/703)).

### Performance
- **Audio Buffer Pooling & Track Transitions**: Refactored internal audio buffer pooling and logging to resolve ExoPlayer reallocation memory leaks and crashes during rapid track changes ([#686](https://github.com/cromaguy/Rhythm/issues/686), [#697](https://github.com/cromaguy/Rhythm/pull/697)).
- **Eliminated Transition Stutter & ANRs**: Removed main-thread lock contention when collecting playback stats on track transitions, eliminating UI freezes and ANR errors ([#701](https://github.com/cromaguy/Rhythm/issues/701)).
- **Debounced MediaStore Observer**: Debounced system media observer events behind a staleness check to prevent scanning storms during large file transfers ([#646](https://github.com/cromaguy/Rhythm/issues/646)).

## [5.5.499.1302] - 2026-10-13

### Fixed
- **Playback Speed & Pitch Controls**: Resolved an issue where adjusting playback speed and pitch in the player bottom sheet had no effect, particularly on FLAC and high-resolution audio files ([#666](https://github.com/cromaguy/Rhythm/issues/666)).
- **Speed & Pitch Continuity**: Playback speed and pitch adjustments now stay properly synchronized across track changes, crossfade transitions, and background service reconnects.
- **Dolby Atmos & Surround Sound Detection**: Refined format detection so Dolby Atmos and multi-channel surround tracks accurately display their dedicated quality badges without false positives on stereo media.
- **Audio Engine Compatibility**: Tuned 32-bit PCM float output to safely coordinate with software equalizer effects, bass boost, spatializer, and time-stretching.

## [5.5.498.1300] - 2026-10-11

### Added
- **Real-Time Playback Quality Info**: Tap the audio quality badge in the player to inspect comprehensive audio telemetry, including source format, active decoder engine, decoded stream specs (sample rate, bit depth, bitrate, channels), and real-time output destination (Bluetooth codec & transmission rate, USB DAC Bit-Perfect pass-through, or Android HAL routing/resampling status).
- **Native Capabilities Screen**: Inspect device audio pipeline parameters, native HAL clock rates, kernel buffer frame bursts, DSP audio offload support, 32-bit float support, physical audio endpoints, and SoC hardware details with one-tap diagnostic report export.
- **External Capabilities & Software Decoders**: Manage software audio engines, prioritize on-chip hardware codecs over software emulation, toggle 32-bit PCM float output, and inspect/toggle individual platform and software decoders.
- **Decoder Extensions Screen**: Configure external audio extensions and inspect extended codec capabilities (FFmpeg, MIDI plugins) with ExoPlayer compatibility status.
- **Persistent Quality Badge Option**: Added setting in Tuner to keep the audio quality indicator persistently visible on the Expressive Player instead of auto-hiding after 5 seconds.
- **Player Bottom Controls Customization**: Normal mode bottom player controls can now be customized with pin limits and status indicators.

### Changed
- **Queue & Playlist Reordering**: Completely overhauled drag-and-drop list reordering with smoother auto-scrolling during drag, fluid edge blending, and stabilized list scroll.
- **AutoEQ Preset Persistence**: Imported custom AutoEQ profiles are now permanently retained across sessions and properly interpolated across device hardware bands.
- **Audio Output Detection**: Enhanced automatic switching and detection between USB DACs, Bluetooth A2DP/BLE, wired headphones, and internal speakers using system MediaRouter integration.
- **Crossfade Engine Overhaul**: Refined track transition controller, reverb tail tolerances, and artwork cache calculation during crossfades.
- **Missing Media Handling**: Queue now gracefully skips and prunes deleted or missing local audio files instead of halting playback.
- **Translations**: Updated community translations across multiple locales from Weblate.

### Performance
- **Expanded Subsonic Library Sync**: Raised library sync limit from 5,000 to 50,000 songs.
- **Resilient Page Checkpointing**: Subsonic full library sync now checkpoints each page to disk, allowing interrupted syncs to resume seamlessly without restarting from scratch.
- **Off-Main-Thread Processing**: Catalog mapping, grouping, deduplication, and sorting moved completely off the main thread for smooth 120Hz scrolling.
- **Optimized Catalog Cache**: Compact on-disk catalog caching without expiring signed URLs, with atomic file writes and batched saves.
- **Deezer Artist Artwork Caching**: Deduped artist image queries to fetch once per artist and eliminated redundant network requests.

### Fixed
- **Date Added Reset**: Resolved issue where "Date Added" timestamps reset when moving, renaming, or recreating local audio files (#661).
- **Subsonic Liked Songs**: Fixed starred Subsonic tracks not appearing in the Liked Songs playlist (#639).
- **Subsonic Sync Storms**: Fixed redundant library re-fetches and sync storms on app startup when the server library is unchanged (#620).
- **Subsonic Network Timeouts**: Added automatic retry for timed-out library sync requests and bounded album queries (#633).
- **Jellyfin & Navidrome Endpoints**: Resolved playback quality switching, download endpoints, and connection issues (#561, #605, #606, #617).

## [5.5.492.1288 Beta] - 2026-10-01

### Added
- USB DAC and USB headset now appear in the audio output switcher with device product names
- New Audio Routing dialog to switch between Bit-Perfect, app-managed, and system routing modes
- Crossfade now has its own dedicated settings screen showing active/disabled/constrained state, with controls for fade duration, repeat-one behavior, and skip behavior

### Fixed
- Equalizer sliders no longer reset adjacent band values when adjusting a single band (#618)
- ReplayGain no longer causes a volume burst when switching tracks (#610)
- Crossfade queue no longer stalls after a few tracks and correctly continues through the playlist (#652)
- Skip Silence no longer accelerates or distorts quiet song intros (#652)
- USB DAC playback no longer incorrectly triggers RhythmGuard speaker volume protection
- ALAC and other high-resolution audio formats (FLAC, Opus) now decode correctly via FFmpeg extension — previously produced silence due to a class reflection failure (#652)
- Lyrics desync on track skip and queue state loss on shuffle toggle (#612, #613)
- Recycled bitmap crash in MediaSession artwork scaling (#603)
- Online artwork no longer disappears when switching tracks or refreshing the library (#616)
- Jellyfin and Navidrome playback, download endpoints, lyrics, and quality switching (#561, #605, #606, #617)
- Bluetooth CDM SecurityException handled gracefully instead of crashing

### Performance
- Audio processors (EQ, Bass Boost, Virtualizer, ReplayGain) now bypass the buffer pipeline entirely when disabled, eliminating unnecessary memory copies per audio packet
- Preferred audio device now correctly bound to ExoPlayer's AudioTrack so AudioFlinger applies Bit-Perfect mixer configuration instead of falling back to resampling

## [5.5.487.1274] - 2026-09-18

### Fixed
- fix(player): bypass ExoPlayer cache for local media and sanitize OGG metadata writes
- fix(player): harden audio offload gating and migrate to PlayerTransferState
- perf(artwork): implement two-tier artwork cache with zero-copy descriptor serving

## [5.5.485.1270] - 2026-09-17

### Added
- Persistent playback queue and shuffle memory: automatically restores your playlist, exact track position, and shuffle state across app restarts (#595)
- AutoEQ headroom protection: automatic preamp attenuation (-maxGain) prevents audio distortion and digital clipping when boosting EQ bands
- AutoEQ speaker bypass: automatically turns off AutoEQ when playing through device speakers and restores it when headphones or external audio connect
- Custom AutoEQ profile management: import, save, and reorder custom equalizer profiles across sessions
- Multi-voice and duet lyrics display with synchronized part highlighting across LRC, TTML, and syllable renderers (#587)
- Theme Intensity customization (Standard, Medium, Vivid) and Expressive Color Set options for dynamic Material 3 palettes (#590)
- Android Photo Picker integration for selecting custom album and playlist artwork without requiring media storage permissions
- On-demand lossless album artwork streaming directly from embedded audio tags with instant visual updates

### Changed
- Equalizer preset frequency mapping now logarithmically interpolates 10-band presets to match device hardware bands accurately
- Dynamic color extraction from album artwork refined to preserve natural hues and color vibrancy
- Adopted Android 15 edge-to-edge layout design and modernized Material 3 Slider interactions
- Updated translations from Weblate

### Fixed
- Fixed playback queue being wiped or re-shuffled when reopening the app or restarting playback (#595)
- Fixed lyric fetching for explicit songs and downloaded audio files, and resolved LRCLIB HTTP 520 connection errors (#587, #583)
- Fixed lyrics cache invalidation and eliminated fetch cancellation thrashing during rapid song skips (#585)
- Hardened background media service startup flow to prevent service crashes and notification failures
- Equalizer curve rendering clamped within display bounds to eliminate visual clipping artifacts

### Performance
- Zero-latency song details sheet: offloaded file path resolution and MediaStore queries to background threads to eliminate UI thread blocking (#589)
- Efficient on-demand artwork streaming eliminating redundant disk caching and memory overhead

## [5.5.480.1260] - 2026-09-06

### Added
- Rhythm Go offline music downloads: download tracks from Subsonic and Jellyfin servers with real-time progress tracking and offline management
- Local network server discovery to auto-scan and connect to nearby Jellyfin and Subsonic servers
- Adaptive Two-Pane layouts and dialogs designed specifically for tablets, foldables, and large screens
- Enhanced tablet sheet navigation with pinned headers, interactive scrollbars, and desktop mouse-wheel support
- Comprehensive gesture customization with independent controls across player, mini-player, queue, library tabs, and lyrics screens
- Mini-player swipe gestures to configure horizontal track skipping and vertical dismiss actions independently
- Album artwork tap actions including single-tap (toggle lyrics), double-tap (play/pause), and tap-to-seek controls
- Customizable and reorderable bottom player action buttons for Normal and Merge modes
- Revamped playback queue with played and up-next sections, swipe-to-dismiss tracks, sticky headers, and smooth drag-and-drop reordering
- Context-aware playback queue preserving album and artist context on track taps (#546)
- Option to keep shuffle active when manually selecting a song from the library
- TTML and Enhanced LRC (.elrc) synchronized lyrics export, editing, and local file discovery
- Streaming service selection step in the initial onboarding tour

### Changed
- Streaming resilience with automatic fallback to downloaded offline music when servers disconnect, plus faster sync and cover art caching
- Touch-aware lyrics auto-scroll that pauses automatically while reading or interacting with lyrics
- Updated translations from Weblate

### Fixed
- Audio service stability fixes preventing background playback termination and native audio engine crashes
- Preserved correct playback queue order when using "Play Next" and "Add to Queue" while shuffle is active
- Multi-artist delimiter handling preventing multi-character delimiters (such as "ft.") from splitting names into individual letters
- Missing artist indexing, empty album navigation, and media scanner folder whitelist filtering
- Normalized track added and modified timestamps to milliseconds with automatic database migration
- Restored missing Favorite and Lyrics action buttons in Merge Mode bottom player controls
- Bottom sheet visual jitter and layout jumps during transitions

### Performance
- Instant backward seeking with a 10-second back-buffer cache to eliminate re-buffering
- Dynamic high-precision scrubbing with sample-accurate playback alignment on release

## [5.5.479.1258 Beta] - 2026-09-05

### Added
- Streaming service selection step in the onboarding tour
- Option to keep shuffle active when manually selecting a track
- Context-aware playback queue preserving album and artist context on track taps (#546)
- Updated translations from Weblate

### Changed
- Refined playback queue UX, artist navigation sheets, and detail screen haptics (#571, #543)

### Fixed
- Restored Favorite and Lyrics action buttons in Merge Mode bottom player controls
- Improved streaming provider reliability across Subsonic and Jellyfin with real-time download tracking, faster sync, and offline fallback
- Resolved local library indexing, artist delimiter splitting, and media scanner folder whitelist filtering
- Fixed audio service stability and background playback termination

## [5.5.473.1246 Beta] - 2026-09-01

### Added
- Customizable bottom player action buttons with reordering and quick toggles
- Adaptive bottom sheet architecture with responsive layouts and fluid scrollbar interactions
- TTML and Enhanced LRC (.elrc) export, editing, and file discovery support

### Fixed
- Preserve playback queue order for Play Next and Add to Queue under Shuffle Engine
- Normalize date added and modified timestamps to milliseconds with Room DB migration
- Suppress lyrics auto-scroll during active user gestures and touch interactions
- Improved TTML line-level timing and Unicode script detection for romanization
- Updated translations from Weblate

## [5.4.468.1233] - 2026-08-28

### Added
- Overhauled onboarding tour with interactive setup for shape presets and artist delimiters
- Enhanced multi-artist splitting with custom delimiter creation, presets, and protected artist names
- Integrated multi-artist splitting into playback stats, play counts, and top artists
- Minimum song duration filter in media scan settings to ignore short audio clips

### Changed
- Clear and user-friendly error messages when Jellyfin connection fails #561
- Updated Material 3 design components and improved navigation stability

## [5.4.466.1229] - 2026-08-27

### Added
- Docked navigation bar mode option alongside floating navigation bar
- Ambient infinite background zoom animation toggle in Player Customization
- On-demand Coil AudioArtworkFetcher and AudioArtworkKeyer for efficient cover art decoding
- Lossless embedded artwork extraction and folder cover reconciliation pipeline
- Direct audio file artwork embedding dialog for fetched covers

### Changed
- Refined synchronized lyrics background gradient in accent color mode
- Updated translations

### Fixed
- Fix OutOfMemoryError during Room playlist transactions and Canvas ExoPlayer playback
- Fix controller player commands permission mask in MediaPlaybackService restricting external controls
- Fix storage write permission handling when embedding artwork from the player
- Resolve compiler deprecations and Jetpack Compose lint warnings

### Performance
- Optimize cold startup performance for large libraries by eliminating blocking disk I/O
- Optimize media scanning and artwork extraction with native TagLib routines and folder cover caching
- Stabilize Discover carousel state across background metadata emissions

## [5.4.465.1226] - 2026-08-27

### Added
- Docked navigation bar mode option alongside floating navigation bar
- Ambient infinite background zoom animation toggle in Player Customization
- On-demand Coil AudioArtworkFetcher and AudioArtworkKeyer for efficient cover art decoding
- Lossless embedded artwork extraction and folder cover reconciliation pipeline
- Direct audio file artwork embedding dialog for fetched covers

### Changed
- Refined synchronized lyrics background gradient in accent color mode
- Updated translations

### Fixed
- Fix OutOfMemoryError during Room playlist transactions and Canvas ExoPlayer playback
- Fix controller player commands permission mask in MediaPlaybackService restricting external controls
- Fix storage write permission handling when embedding artwork from the player
- Resolve compiler deprecations and Jetpack Compose lint warnings

### Performance
- Optimize cold startup performance for large libraries by eliminating blocking disk I/O
- Optimize media scanning and artwork extraction with native TagLib routines and folder cover caching
- Stabilize Discover carousel state across background metadata emissions

## [5.4.462.1216] - 2026-08-23

### Added
- Enable non-blocking instant startup with asynchronous background media sync and live progress indicator
- Support for TTML and XML lyrics with enhanced "Better Lyrics" format parsing #363
- Foldable and posture-aware player layouts for multi-window and folding devices #529 #530
- Mono audio downmixing with dedicated per-device toggle #531
- Expanded support for MP4 audio containers and advanced audio codecs #535
- Android Quick Settings tile shortcuts for active playback controls #227 #457
- Custom artwork editing and management for artists and playlists #280 #415
- Option to select and batch-manage songs from the folder view #492
- Option to toggle remaining playback time on track seekbars
- Option to disable online and custom artist images #475
- Dedicated Album Artists tab in the library
- Comprehensive song deletion flow with storage permission handling #179 #493 #501 #508
- Granular audio format scan controls and extended codec filtering

### Changed
- Merge streaming mode seamlessly into shared local UI
- Standardize bottom sheet radii and surface elevations across the app
- Redesigned and improved Song Info dialog with rich metadata display
- Refined Home and About screen layouts with improved artist separation
- Drop legacy song ratings in favor of modern favorites workflow

### Fixed
- Fix app crash when opening lyrics settings in streaming mode #554
- Fix ambient intensity slider numerical step and range display #542
- Fix app volume not persisting across playback and session flows #551
- Fix Dolby Atmos detection to match both `eac3-joc` and `atmos` MIME types
- Fix metadata parsing edge-cases and improve multi-disc album grouping #521 #538
- Fix touch event handling and responsiveness on playback speed sheet
- Fix vertical duration text alignment in Expressive player layout
- Fix empty state styling and active song indicators in Liked tab
- Fix plural string formatting across localized settings

### Performance
- Harden Rhythm processor initialization and audio device routing
- Optimize Compose duration formatting performance and clickable modifier hitboxes
- Upgrade GitHub Actions workflows to latest Node 24 runtime
- Bump Gradle and AndroidX dependency versions
- Updated translations

## [5.4.457.1200 Beta] - 2026-08-11

### Added
- Add support for TTML lyrics and "Better Lyrics" #363
- Add custom artwork editing for artists/playlists #280 #415
- Add shortcuts to active it via android quick settings tile #227 #457
- Add option to disable artist image #475
- Add option to select songs from the folder section #492
- Add song deletion flow and player UX polish #179 #493 #501 #508
- Add foldable posture-aware player layouts #529 #530
- Add mono audio downmix and device toggle #531
- Expand support for MP4 and advanced audio codecs #535
- Improve metadata parsing and album grouping #521 #538
- Add format scan controls and metadata fixes
- Drop song ratings, add Album Artists tab
- Merge streaming mode into shared local UI
- Harden plural string handling in settings
- Fix vertical alignment of time duration texts in Expressive player layout
- Optimize compose duration formatting performance and click modifiers target area
- Add toggle option for remaining playback time
- Update translations

## [5.4.457.1199 Beta] - 2026-08-11

### Added

- Add support for TTML lyrics and "Better Lyrics" #363
- Add custom artwork editing for artists/playlists #280 #415
- Add shortcuts to active it via android quick settings tile #227 #457
- Add option to disable artist image #475
- Add option to select songs from the folder section #492
- Add song deletion flow and player UX polish #179 #493 #501 #508
- Add foldable posture-aware player layouts #529 #530
- Add mono audio downmix and device toggle #531
- Expand support for MP4 and advanced audio codecs #535
- Improve metadata parsing and album grouping #521 #538
- Add format scan controls and metadata fixes
- Drop song ratings, add Album Artists tab
- Merge streaming mode into shared local UI
- Harden plural string handling in settings
- Fix vertical alignment of time duration texts in Expressive player layout
- Optimize compose duration formatting performance and click modifiers target area
- Add toggle option for remaining playback time
- Update translations

## [5.3.440.1160] - 2026-07-28

### Added

- Tablet UI fixes
- Major Library Fixes
- Add track error checker toggle and API fixes
- Improve LAN server discovery and Jellyfin parsing
- Harden backup payload and playlist restore
- Refine fullscreen lyrics sync UI behavior #517
- Stabilize carousel auto-scroll on resume #511
- Keep playback active when queueing songs #514
- Respect whitelist scan mode in library refresh #498
- Add .opa format support across media handling #516
- Updated translations

## [5.3.434.1139 Beta] - 2026-07-26

### Added

* Refine fullscreen lyrics sync UI behavior #517
* Stabilize carousel auto-scroll on resume #511
* Keep playback active when queueing songs #514
* Minor Library Fixes
* Respect whitelist scan mode in library refresh #498
* Add .opa format support across media handling #516

## [5.3.432.1135] - 2026-07-17

### Added

* Add track error dialog and UI refinements
* Handle implicit lyric end times and gaps #482 #496
* Unify shuffle flow and restore playback state
* Improved Library Padding and Tablet view
* Improve streaming metadata and offline fallback
* Scope updater viewmodel and update flows
* Persist playlists in Room
* Stabilize library refresh and cache writes
* Fix queue restore with ExoPlayer shuffle
* Fixed: Permanent shuffle mode disables by itself #488
* Fixed: Scrolling error in album list of songs #479
* Fixed: Play button on wrong layer #483
* Fixed: Login on Nextcloud #485
* Fixed: Incorrect scrolling and selection in library #477
* Fixed: Rhythm Guard 'Lock' ineffective #474
* Fixed: Too much battery consumption #471
* Fixed: Lyric exports ignore context #467
* Fixed: Unintentional draggable element #468
* Fixed: Cannot "back" from Library to Home #466
* Added new translation: Uzbek
* Updated translations: Spanish, Swedish, Ukrainian, Arabic, French, Polish, Chinese (Traditional), and Chinese (Simplified)

## [5.3.429.1123 Beta] - 2026-07-12

### Added

* Fixed: Too much battery consumption #471
* Fixed: Lyric exports ignore context #467
* Fixed: Unintentional draggable element #468
* Fixed: Cannot "back" from Library to Home #466
* Fixed: Scrolling error in album list of songs #479
* Fixed: Play button on wrong layer #483
* Fixed: Login on nextcloud #485
* Fixed: Incorrect scrolling and selection in library #477
* Fixed: Rhythm Guard 'Lock' Ineffective. #474
* Improved Library Padding and Tablet view
* Improve streaming metadata and offline fallback
* Fix playback and UI cleanup
* Use BottomSheetState; fix nullability \& network
* Scope updater viewmodel and update flows
* Persist playlists in Room
* Stabilize library refresh and cache writes
* chore(l10n): update Chinese (Simplified Han script) translation
* chore(l10n): update Chinese (Traditional Han script) translation
* chore(l10n): update Ukrainian translation
* chore(l10n): update French translation
* chore(l10n): update Arabic translation
* chore(l10n): update Swedish translation
* chore(l10n): update Polish translation
* chore(l10n): update Spanish translation

## [5.2.423.1109] - 2026-07-05

### Added

* Harden playlist serialization #462
* Improve album grouping, matching, and navigation
* Improve updater mismatch handling
* Handle zero-volume resume and extend sleep timer
* Stabilize album song list scrolling
* chore(l10n): update Spanish translation
* chore(l10n): update Indonesian translation
* chore(l10n): update Estonian translation
* chore(l10n): update French translation
* chore(l10n): update Chinese (Simplified Han script) translation

## [5.2.422.1105] - 2026-07-04

### Added

* Add Weblate integration and translation updates
* Implement play next and improve broadcast safety
* Fix player action wiring and song selection
* Improved Updater \& New Nightly channel
* Added exact artwork color setting
* Bump Compose and UI dependency versions
* Added Motion Canvas support
* Refresh artwork on settings changes
* Improved Mini Player and Player transitions
* Refactor lyrics fetching with multi-source support
* Handle missing picker and suggest folders #455
* Fixed: cannot import playlists from json backup #449
* Fixed Lyrics Embedding \& Sleep Timer
* Fixed: Sleep timer remaining time not counted down #450
* Fixed: cannot import playlists from json backup #449
* Fixed: Connection failed: java.security.cert.CertPathValidatorException: Trust anchor for certification path not found.  #451

## [5.2.422.1104 Beta] - 2026-07-04

### Added

* Restrict nightly update check to nightly builds
* Implement play next and improve broadcast safety
* Fix player action wiring and song selection
* Added exact artwork color setting
* Bump Compose and UI dependency versions
* Added Canvas support

## [5.2.422.1103 Beta] - 2026-07-03

### Added

* Implement play next and improve broadcast safety
* Fix player action wiring and song selection
* Improved Updater
* Add nightly builds \& exact artwork color setting
* Bump Compose and UI dependency versions
* Added Canvas support

## [5.2.421.1101 Beta] - 2026-07-02

### Added

* Added Canvas support

## [5.2.419.1099 Beta] - 2026-07-02

### Added

* Refresh artwork on settings changes
* Improved Mini Player and Player transitions
* Refactor lyrics fetching with multi-source support
* Handle missing picker and suggest folders #455
* Fixed: cannot import playlists from json backup #449
* Minor Improvements

## [5.2.418.1097 Beta] - 2026-07-01

### Added

* Refactor lyrics fetching with multi-source support
* Handle missing picker and suggest folders #455
* Fixed: cannot import playlists from json backup #449
* Minor Improvements

## [5.2.417.1095 Beta] - 2026-07-01

### Added

* Handle missing picker and suggest folders #455
* Fixed: cannot import playlists from json backup #449
* Minor Improvements

## [5.1.416.1093 Beta] - 2026-06-26

### Added

* Minor Improvements
* Fixed Sleep Timer
* Fixed Lyrics Embedding
* Fixed: Sleep timer remaining time not counted down #450
* Fixed: cannot import playlists from json backup #449
* Fixed: Connection failed: java.security.cert.CertPathValidatorException: Trust anchor for certification path not found. #451

## [5.1.415.1089 Beta] - 2026-06-25

### Added

* Fixed: Sleep timer remaining time not counted down #450
* Fixed: cannot import playlists from json backup #449
* Fixed: Connection failed: java.security.cert.CertPathValidatorException: Trust anchor for certification path not found. #451

## [5.1.414.1086] - 2026-06-22

### Added

* Added Song-Specific Lyrics Preferences and Custom LRC File Management
* Fixed: Premature signaling of Instrumental lyrics #442
* Added ability to share tracks from all song overflow menus
* Fixed: all playlists missing in latest beta build  #437
* Fixed: Backup button not showing  #435
* Major optimizations made
* Fixed Stats and Equalizer opening lag
* Fix artist splitting, sorting, and chooser flow across player screens
* Fixed Library Scrollbar
* Refactor: replace magic strings with typed enums for media scanning
* Feat: Extend format/codec/tag support
* Fixed Carousel Scrolling
* Fixed: Update fails every time #429
* Added: Sort the Album tab by Year #432
* Fixed: Cannot save word by word lyrics and save button squished #433
* Added: long press lyrics chip on player to launch immersive view #406
* Library improvements
* Addressed color issues
* Attempt fixes: Whitelist mode doesn't work  #405
* Added mkv/mka format support
* fix(ArtistDetailScreen): Update album filtering to use more appropriate matching function

## [5.1.414.1085 Beta] - 2026-06-21

### Added

* Added Song-Specific Lyrics Preferences and Custom LRC File Management
* Fixed: Premature signaling of Instrumental lyrics #442
* Added ability to share tracks from all song overflow menus
* Fixed: all playlists missing in latest beta build  #437
* Updated dependencies

## [5.1.413.1080\\ Beta] - 2026-06-20

### Added

* Added ability to share tracks from all song overflow menus
* Fixed: all playlists missing in latest beta build  #437
* Updated dependencies

## [5.1.413.1080 Beta] - 2026-06-20

### Added

* Added ability to share tracks from all song overflow menus
* Fixed: all playlists missing in latest beta build  #437
* Updated dependencies

