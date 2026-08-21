# Card Registry

This document describes how physical QR codes map to stable internal card identities in the Monopoly Ultimate Banking QR Scanner Replacement project.

Step 2 produced the machine-readable master registry from existing physical card assets. The registry is the authoritative lookup table for card identity — not filenames, not front artwork text.

---

## Lookup Flow

```text
Physical QR
    ↓
qrPayload
    ↓
Card Registry lookup
    ↓
cardId
    ↓
CardDefinition
    ↓
Game Engine
```

When a player scans a physical card, the Android camera will decode the QR string (`qrPayload`). The application performs a registry lookup equivalent to:

```text
findCardByQrPayload(scannedValue)
```

and receives a stable internal identifier such as:

```text
USR_01    (Car)
EVT_14    (Pick Your Own)
PRP_07    (Whitehall)
```

Only after resolving `cardId` does the Game Engine decide what action to perform (purchase, rent, Event effect, balance check, etc.).

---

## Architectural Rules

### QR payload ≠ card ID

The decoded QR string and the internal `cardId` are **separate fields**. They must not be assumed identical.

Example:

| cardId | name | qrPayload |
| ------ | ---- | --------- |
| USR_01 | Car | `MUB:PL:CAR` |
| EVT_01 | Boom Town | `MUB:E:E01` |
| PRP_01 | Old Kent Road | `MUB:P:01` |

The QR payload format observed in Step 2 uses the prefix `MUB:` (Monopoly Ultimate Banking) with type-specific segments:

| cardType | Payload pattern (observed) | Example |
| -------- | -------------------------- | ------- |
| USER | `MUB:PL:<NAME>` | `MUB:PL:HELICOPTER` |
| EVENT | `MUB:E:E<NN>` | `MUB:E:E14` |
| PROPERTY | `MUB:P:<NN>` | `MUB:P:07` |

### No filename-based runtime logic

The Android app must **never** identify cards by image filename at runtime:

```text
# BAD — do not do this
if filename == "01_Property_Back_QR.png"
```

The scanner returns only the decoded string. Lookup is always:

```text
QR payload → Card Registry → cardId
```

### Legacy barcode backs are excluded

Files matching `*_Back.png` (without `_QR`) contain the original hardware unit barcodes. They are inventoried for audit purposes in `data/card_asset_inventory.csv` but are **not** registered in `data/cards.json`.

### Static identity vs runtime state

The registry holds **card identity** only:

- `cardId`, `cardType`, `name`, `qrPayload`, asset paths

It does **not** hold game state such as owner, balance, or rent level. Those belong to `GameSession` state as defined in [GAME_STATE_MODEL.md](GAME_STATE_MODEL.md).

---

## Stable Internal Card IDs

### User / Player Cards

| cardId | sequence | name | qrPayload |
| ------ | -------- | ---- | --------- |
| USR_01 | 1 | Car | `MUB:PL:CAR` |
| USR_02 | 2 | Helicopter | `MUB:PL:HELICOPTER` |
| USR_03 | 3 | Ship | `MUB:PL:SHIP` |
| USR_04 | 4 | Aeroplane | `MUB:PL:AEROPLANE` |

### Event Cards

| cardId | sequence | name | qrPayload |
| ------ | -------- | ---- | --------- |
| EVT_01 | 1 | Boom Town | `MUB:E:E01` |
| EVT_02 | 2 | Crime Down | `MUB:E:E02` |
| … | … | … | … |
| EVT_23 | 23 | Wibble Wobble | `MUB:E:E23` |

### Property Cards

| cardId | sequence | name | qrPayload |
| ------ | -------- | ---- | --------- |
| PRP_01 | 1 | Old Kent Road | `MUB:P:01` |
| PRP_02 | 2 | Whitechapel Road | `MUB:P:02` |
| … | … | … | … |
| PRP_22 | 22 | Mayfair | `MUB:P:P22` |

Full mappings are in `data/cards.json` and `data/card_registry.csv`.

---

## Master Data Files

| File | Purpose |
| ---- | ------- |
| `data/cards.json` | Machine-readable card registry (primary source for app loading) |
| `data/card_registry.csv` | Human-readable registry for inspection and editing |
| `data/qr_decode_results.csv` | QR decode audit log with status per card |
| `data/card_asset_inventory.csv` | Complete asset inventory including legacy backs and front variants |
| `data/card_registry_validation.txt` | Validation report from Step 2 |

---

## Asset Paths

Asset paths in the registry are relative to the workspace root (`Resources/...`).

Property front artwork uses the canonical `{NN}_Property_Front.png` filename under `Resources/Cards/PropertyCards/` (QR backs are `{NN}_Property_Back_QR.png`). Display names stay in registry data, not in the filename.

Example:

```json
{
  "cardId": "PRP_01",
  "cardType": "PROPERTY",
  "sequence": 1,
  "name": "Old Kent Road",
  "qrPayload": "MUB:P:01",
  "assets": {
    "front": "Resources/Cards/PropertyCards/01_Property_Front.png",
    "qr": "Resources/Cards/PropertyCards/01_Property_Back_QR.png"
  }
}
```

---

## Development Tools

| Tool | Purpose |
| ---- | ------- |
| `tools/decode_qr_assets.py` | Inventory assets, decode QR images, build registry files |
| `tools/validate_card_registry.py` | Validate counts, fields, asset existence, and QR uniqueness |
| `tools/requirements.txt` | Python dependency (`opencv-python`) for QR decoding |

Re-run after any QR image changes:

```bash
cd monopoly-ultimate-banking-qr/tools
pip install -r requirements.txt
python decode_qr_assets.py
python validate_card_registry.py
```

---

## Step 2 Validation Summary

```text
User cards:     4 / 4
Event cards:    23 / 23
Property cards: 22 / 22
Total:          49 / 49

QR decoding:    49 successful, 0 failed, 0 duplicates
Registry validation: PASS
```

See `data/card_registry_validation.txt` for the full report.

---

## What This Step Did Not Include

Step 2 resolved **card identity and QR mapping only**. The following remain for later steps:

- Property purchase prices and rent structures
- Event card effects and required scan targets
- Game rules and banking behavior
- Android runtime implementation

See [OPEN_QUESTIONS.md](OPEN_QUESTIONS.md) for remaining unknowns.
