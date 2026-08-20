# Card Front Display

This document describes how physical card front artwork is generated, mapped, and shown on the Active Game screen and during Player Setup name entry.

## Source assets (read-only)

Authoritative card artwork lives outside the Android project:

```text
Resources/Cards/
├── EventCards/
├── PropertyCards/
└── UserCards/
```

These source files are never modified by tooling or the app.

User banking-card fronts are JPG landscape artwork, for example:

```text
Resources/Cards/UserCards/Car_Front.jpg
Resources/Cards/UserCards/Helicopter_Front.jpg
Resources/Cards/UserCards/Ship_Front.jpg
Resources/Cards/UserCards/Aeroplane_Front.jpg
```

## Canonical front selection

Stable card IDs and canonical front paths come from the master registry:

```text
data/cards.json
```

Each card entry includes `assets.front` (never `*_Back*` or `*_Back_QR*`).

## Card-type orientation policy

The sync tool applies orientation rules **by card type**. Do not rotate every horizontal source image.

### USER CARD FRONTS

```text
Source: Resources/Cards/UserCards/*_Front.jpg
Display: LANDSCAPE
Rotation: none
```

Examples: Car, Helicopter, Ship, Aeroplane.

User banking cards remain horizontal in runtime assets and UI.

### EVENT CARD FRONTS

```text
Display: PORTRAIT
Rotation: 90° counter-clockwise when source width > height
```

### PROPERTY CARD FRONTS

```text
Display: PORTRAIT
Rotation: 90° counter-clockwise when source width > height
```

## Runtime generation

```text
python tools/sync_android_card_images.py
```

The sync tool:

1. Reads `data/cards.json`
2. Verifies each canonical front exists under `Resources/Cards/`
3. Accepts `*_Front.png`, `*_Front.jpg`, and `*_Front.jpeg`
4. Preserves **USER** landscape orientation without rotation
5. Converts **EVENT/PROPERTY** landscape sources to portrait using the existing 90° CCW rule
6. Writes Android runtime PNGs to `android-app/app/src/main/assets/cards/fronts/`
7. Writes manifest JSON to:
   - `data/android_card_front_manifest.json`
   - `android-app/app/src/main/assets/cards/android_card_front_manifest.json`

Runtime filenames are Android-safe and derived from stable IDs, for example:

```text
usr_01.png
prp_01.png
evt_06.png
```

Images are capped to a maximum width of **1024px** using high-quality Lanczos resampling when needed.

## Validation

```text
python tools/validate_card_front_assets.py
```

Writes `data/card_front_asset_validation.txt` and verifies:

- 4 / 4 user fronts, all **LANDSCAPE**
- 22 / 22 property fronts, portrait canonical orientation
- 23 / 23 event fronts, portrait canonical orientation
- 49 / 49 total runtime assets
- USER JPG sources accepted
- no back/QR artwork used as fronts
- unique cardId mappings
- no image dependencies in `:game-core`

## Runtime lookup (`:app` only)

Game Core remains unaware of PNG/JPG assets.

```text
ResolvedCard.cardId
       ↓
CardFrontRegistry (manifest)
       ↓
CardFrontImageProvider.getFrontImage(cardId)
       ↓
CardFrontImage composable
```

Manifest entries include `orientation`, `width`, and `height`. The composable uses that metadata (not runtime width/height heuristics) to choose landscape vs portrait layout.

Card artwork is loaded on demand and cached in memory by `CardFrontImageProvider`. Images use `ContentScale.Fit` so the full card remains readable.

## Player Setup UI behavior

During **NEW GAME / Player Setup**:

```text
Scan USER card → User audio → show User front (landscape) → enter custom name → ADD PLAYER
```

The scanned User front is transient presentation state only. It is not stored in `GameSession`.

After successful registration, the setup list shows compact identity only:

```text
[Car icon] Nishith
M1500
```

## Active Game UI behavior

When a QR card is resolved on Active Game:

```text
QR → CardResolver → ResolvedCard → existing workflow + card front display
```

The card front is transient presentation state only. It is **not** stored in `GameSession`, bitmap bytes are not persisted, and partially displayed workflows are discarded on restart per **GR-SAVE-002**.

Contextual actions remain workflow-driven:

- Unowned property: BUY, AUCTION, CANCEL
- Owned property: SCAN PLAYER, CANCEL (no BUY/AUCTION)
- Event intro: CONTINUE, CANCEL (then existing event target workflow)
- User card (READY): player summary + DONE
- Committed results: keep the relevant card front visible with DONE

## Regression commands

```text
python tools/sync_android_card_images.py
python tools/validate_card_front_assets.py
./gradlew :game-core:test
./gradlew :app:testDebugUnitTest
./gradlew assembleDebug
```
