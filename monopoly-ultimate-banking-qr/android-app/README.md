# Android App

Banking QR — Android application foundation for the Monopoly Ultimate Banking QR Scanner Replacement project.

This directory contains the Android implementation. The pure Kotlin game engine lives in `:game-core`.

## Modules

| Module | Purpose |
| ------ | ------- |
| `:app` | Compose UI, navigation, Android lifecycle, future scanner and persistence |
| `:game-core` | Pure Kotlin game engine (no Android dependencies) |

## Source of Truth for Master Data

```text
ROOT data/common/*.json and data/editions/<id>/*.json
        =
game master data source of truth
```

```text
app/src/main/assets/game/*.json
        =
generated Android runtime copies
```

**Do not edit Android asset JSON directly.** Edit the root `data/` files and re-sync.

## Sync Runtime Assets

From the repository root:

```bash
python tools/sync_android_assets.py
```

From `android-app/`:

```bash
python ../tools/sync_android_assets.py
```

Copied files:

- `cards.json`
- `properties.json`
- `events.json`
- `game_rules.json`
- `event_engine_rules.json`
- `board_relationships.json`
- `banking_values.json`

## Build

From `android-app/`:

```bash
./gradlew clean test assembleDebug
```

Windows:

```bat
gradlew.bat clean test assembleDebug
```

Requires Android SDK and `local.properties` with `sdk.dir` (not committed).

## Architecture

See [../docs/ANDROID_ARCHITECTURE.md](../docs/ANDROID_ARCHITECTURE.md).

## Step 5 Scope

This foundation includes:

- Two-module Gradle project
- Jetpack Compose + Material 3 placeholder screens
- Basic navigation (Home → Player Setup → Game)
- Generated runtime JSON assets
- Pure Kotlin `:game-core` module scaffold

Not yet implemented:

- Game Engine rules and state
- QR scanning (CameraX / ML Kit)
- Room persistence
- Game ViewModels with business logic
