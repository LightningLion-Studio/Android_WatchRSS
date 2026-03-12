# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

腕上RSS (WatchRSS) is an Android wearable application for reading RSS feeds, Bilibili, and Douyin content on smartwatches. The app targets Android wearable devices with minSdk 30, targetSdk 34, and compileSdk 36.

## Build Commands

```bash
# First-time setup: copy local gradle properties
cp gradle.properties.example gradle.properties

# Build debug APK
./gradlew assembleDebug

# Build release APK (requires keystore.properties)
./gradlew assembleRelease

# Run tests
./gradlew test

# Run instrumented tests
./gradlew connectedAndroidTest

# Clean build
./gradlew clean
```

## Project Structure

Multi-module Gradle project:
- `app/` - Main application module with UI and business logic
- `sdk/bili/` - Bilibili SDK integration module
- `sdk/douyin/` - Douyin SDK integration module

## Architecture

### Dependency Injection
Uses manual DI via `AppContainer` pattern (not Dagger/Hilt):
- `AppContainer` interface defines app-level dependencies
- `DefaultAppContainer` provides concrete implementations
- Initialized in `WatchRssApplication` and accessed via `(application as WatchRssApplication).container`

### Repository Pattern
Data layer organized by feature:
- `RssRepository` - RSS feed management, parsing, offline storage
- `BiliRepository` - Bilibili API integration and caching
- `DouyinRepository` - Douyin API integration
- `SettingsRepository` - User preferences via DataStore

All repositories are instantiated in `DefaultAppContainer`.

### Database
Room database (`WatchRssDatabase`) with migration support:
- Current version: 6 (see `MIGRATION_1_2` through `MIGRATION_5_6`)
- When adding schema changes, always create a new migration
- DAOs: `RssChannelDao`, `RssItemDao`, `SavedEntryDao`, `OfflineMediaDao`

### UI Layer
- Jetpack Compose for all UI (RecyclerView/Adapter removed)
- ViewModels follow naming convention: `<Feature>ViewModel` (e.g., `HomeViewModel`, `BiliLoginViewModel`)
- ViewModelFactory pattern for ViewModels requiring repository dependencies

## Key Technologies

- **RSS Parsing**: RSS-Parser 6.0.10 (Kotlin Multiplatform, supports RSS/Atom/RDF)
- **Async**: Kotlin Coroutines with `CoroutineScope(SupervisorJob() + Dispatchers.IO)`
- **Image Loading**: Coil with GIF support
- **Video Playback**: Media3 ExoPlayer
- **QR Code**: ZXing core library
- **Local Server**: NanoHTTPD for device-to-device sync

## Logging

Unified logging via `AppLogger`:
```kotlin
AppLogger.log(tag: String, message: String)
```

Debug builds also populate `DebugLogBuffer` for in-app log viewing. Bilibili SDK logs are redirected to this buffer when `ApplicationInfo.FLAG_DEBUGGABLE` is set.

## Configuration Files

- `gradle.properties` - Local machine-specific config (NOT in git, copy from `gradle.properties.example`)
- `keystore.properties` - Release signing config (optional, NOT in git)
- `gradle/libs.versions.toml` - Centralized dependency version management

## Development Notes

- Package name: `com.lightningstudio.watchrss`
- Application class: `WatchRssApplication`
- Base activity for wearable UI: `BaseWatchActivity`
- ProGuard enabled for release builds with resource shrinking
