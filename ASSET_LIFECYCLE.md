# Monopoly Asset Lifecycle

This document describes how image assets move through the Monoreopoly workspace.

## Asset stages

### Card fronts

```text
Source artwork (event centre only)
→ generated HTML
→ generated Card Front PNG
→ published Resources Card Front
→ Android synchronized copy
```

### Card backs

```text
events.json / properties.json
→ separate QRCode workspace
→ generated complete QR Card Back PNG
→ qrAsset path under Resources
```

## Directory ownership

### `monopoly-edition-generator/assets/`

Irreplaceable **source inputs** only:

* Event centre artwork (`assets/cards/editions/<edition>/event-artwork/`)
* Board-space artwork (`assets/board-spaces/`)
* Template artwork and common source graphics

Do **not** store complete generated Card Fronts or QR Card Backs here.

### `monopoly-edition-generator/output/`

Reproducible generated files only:

* Generated HTML
* Generated Card Front PNGs
* Board renders and generation reports

This directory is **gitignored** and safe to delete and regenerate.

Nothing in authoritative JSON should reference `output/`.

### `Resources/`

Canonical **publication layer**:

* Complete Card Front PNGs (`frontAsset`)
* Complete QR Card Back PNGs (`qrAsset`)
* Common user-card assets
* Published board assets

Do **not** store source centre artwork or generated HTML here.

### `monopoly-ultimate-banking-qr/android-app/.../assets/cards/`

Synchronized **runtime copies** only. Never edit manually.

All Android Card Front images must come from top-level `Resources/` through `tools/sync_android_card_images.py`.

### Separate QRCode workspace

The QRCode application remains outside this monorepo.

It should:

1. Read `events.json` and `properties.json`.
2. Encode each card's `qrPayload`.
3. Write finished Card Back PNGs directly to each card's `qrAsset` path under `Resources/`.

Do not create a second QR-output folder inside the Edition Generator.

## JSON field meanings

| Field | Meaning | Resolved against |
|-------|---------|------------------|
| `artworkAsset` | Centre illustration used by the Event Card generator | `monopoly-edition-generator/` project root |
| `frontAsset` | Complete published Card Front | `C:\Personal\Monopoly` workspace root |
| `qrAsset` | Complete published Card Back containing the QR image | `C:\Personal\Monopoly` workspace root |
| `qrPayload` | Exact text encoded inside the QR | Used by Android scanner and validation tools |

## Manual-edit rules

**Safe to edit manually**

* `monopoly-edition-generator/assets/`
* Edition JSON data under `monopoly-ultimate-banking-qr/data/editions/`
* Templates and themes
* Common source artwork under `Resources/Common/`

**Never edit manually**

* `monopoly-edition-generator/output/`
* `monopoly-ultimate-banking-qr/android-app/.../assets/cards/`

**Published through tools only**

* `Resources/Editions/<edition>/EventCards/`
* `Resources/Editions/<edition>/PropertyCards/`

## Commands

Run from `monopoly-ultimate-banking-qr/` unless noted.

### Regenerate HTML and PNG fronts

From `monopoly-edition-generator/`:

```bash
python generate.py india --only events
python generate.py india --only properties
```

Add `--no-png` for HTML only.

### Publish fronts to Resources

```bash
python tools/publish_edition_card_fronts.py --edition india --card-type all --dry-run
python tools/publish_edition_card_fronts.py --edition india --card-type all
python tools/publish_edition_card_fronts.py --edition india --card-type all --verify
```

Backward-compatible wrapper:

```bash
python tools/wire_india_card_artwork.py --dry-run
```

### Validate asset references

```bash
python tools/validate_asset_references.py --edition india
```

### Synchronize Android fronts

```bash
python tools/sync_android_card_images.py --edition india --dry-run
python tools/sync_android_card_images.py --edition india --prune
python tools/sync_android_card_images.py --edition india --verify
```

## Edition artwork status

| Status | Meaning |
|--------|---------|
| `INCOMPLETE` | Required published fronts are missing |
| `FRONTS_READY` | Published fronts exist; QR Card Backs may still be pending |
| `READY` | Published fronts and QR Card Backs are complete |

India should remain `FRONTS_READY` until all 47 `qrAsset` files exist.
