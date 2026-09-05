# Changelog

All notable changes to Rhythm will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- 

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

