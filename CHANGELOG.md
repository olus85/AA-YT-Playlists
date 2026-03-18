# Changelog

All notable changes to this project will be documented in this file.

## [Unreleased] - 2026-03-18

### Added
- **Android Auto Playlist Detail Screen**: A new view in the car (`PlaylistDetailScreen`) displaying the tracks of the selected playlist, replacing the direct playback intent.
- **Track Fetching API**: `MetadataFetcher.kt` integrated with Invidious API to fetch and parse exact track lists from playlists.
- **Manual Thumbnail URL**: The `EditPlaylistDialog` now allows manually updating a playlist's image URL.

### Changed
- **UI/UX Redesign**: Revamped the Compose UI with a modern Material 3 design, edge-to-edge cards, improved typography, and smooth entrance/scroll animations.
- **Android Auto Thumbnails**: Switched from `GridTemplate` to `ListTemplate` and updated Coil to use `Scale.FILL`, rendering gorgeous, borderless, large thumbnails on the infotainment screen.
- **Drag & Drop Reorder**: Replaced manual `pointerInput` with an established reorderable library (`org.burnoutcrew.composereorderable`) handling the drag & drop seamlessly via the drag handle.

### Fixed
- Fixed visual glitches and the list jumping erratically when reordering playlists on the smartphone.
- Handled correct parsing for individual videos (`&v=`) versus list launches in the `PlaylistReceiver.kt` for more robust track start logic.
