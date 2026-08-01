# Changelog

All notable changes to Rhythm will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- 

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
