# Card Front Display

This document describes how physical card front artwork is generated, mapped, and shown on the Active Game screen and during Player Setup name entry.

## Source assets (read-only)

Authoritative card artwork lives outside the Android project:

```text
Resources/Common/UserCards/
Resources/Editions/uk/PropertyCards/
Resources/Editions/uk/EventCards/
```

These source files are never modified by tooling or the app.

User banking-card fronts are JPG landscape artwork, for example:

```text
Resources/Common/UserCards/Car_Front.jpg
Resources/Common/UserCards/Helicopter_Front.jpg
Resources/Common/UserCards/Ship_Front.jpg
Resources/Common/UserCards/Aeroplane_Front.jpg
```

## Canonical front selection

Stable card IDs and canonical front paths come from the master registry:

```text
data/common/card_registry.json
```

Each card entry includes `assets.front` (never `*_Back*` or `*_Back_QR*`).

## Card-type orientation policy

The sync tool applies orientation rules **by card type**. Do not rotate every horizontal source image.

### USER CARD FRONTS

```text
Source: Resources/Common/UserCards/*_Front.jpg
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

1. Reads `data/common/card_registry.json`
2. Verifies each canonical front exists under `Resources/Common/` or `Resources/Editions/uk/`
3. Accepts `*_Front.png`, `*_Front.jpg`, and `*_Front.jpeg`
4. Preserves **USER** landscape orientation without rotation
5. Converts **EVENT/PROPERTY** landscape sources to portrait using the existing 90° CCW rule
6. Writes Android runtime PNGs to edition-aware paths:
   - `android-app/app/src/main/assets/cards/common/user/`
   - `android-app/app/src/main/assets/cards/editions/<editionId>/property/`
   - `android-app/app/src/main/assets/cards/editions/<editionId>/event/`
7. Writes per-package manifest JSON to:
   - `data/cards/common/android_card_front_manifest.json`
   - `data/cards/editions/<editionId>/android_card_front_manifest.json`
   - matching paths under `android-app/app/src/main/assets/cards/`

Runtime filenames are Android-safe and derived from stable IDs within each edition package, for example:

```text
cards/common/user/usr_01.png
cards/editions/uk/property/prp_01.png
cards/editions/uk/event/evt_06.png
```

Images are capped to a maximum width of **1024px** using high-quality Lanczos resampling when needed.

## Validation

```text
python tools/validate_card_front_assets.py
```

Writes `data/card_front_asset_validation.txt` and verifies per edition:

- common user fronts (explicit shared package)
- edition property/event fronts loaded from that edition's definitions
- no back/QR artwork used as fronts
- unique cardId mappings within each manifest
- no image dependencies in `:game-core`

Editions marked `artworkStatus: INCOMPLETE` may ship zero property/event fronts; runtime lookup returns a missing-artwork placeholder instead of UK fallback artwork.

`FRONTS_READY` means published card fronts exist under `Resources/Editions/<edition>/`, but QR Card Backs may still be pending.

## Runtime lookup (`:app` only)

Game Core remains unaware of PNG/JPG assets.

```text
active editionId + CardType + cardId
       ↓
CardFrontRegistry.resolve(editionId, cardType, cardId)
       ↓
CardFrontImageProvider
       ↓
CardFrontImage composable
```

USER cards resolve from the explicit common manifest. PROPERTY and EVENT cards resolve only from the active edition manifest. Missing artwork shows a neutral placeholder with edition/type/card diagnostics; it never falls back to another edition's asset.

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
