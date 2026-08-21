# Game Data

This document describes the **static game definition** data extracted from physical Property and Event card fronts in Step 3.

Runtime game state (owner, current rent level, balances) is **not** stored here. See [GAME_STATE_MODEL.md](GAME_STATE_MODEL.md).

---

## Data Flow

```text
Card Registry
     ↓
Static Game Definition
     ↓
Runtime Game State
```

### Property path

```text
PRP_01
     ↓
PropertyDefinition (data/editions/uk/properties.json)
     ↓
purchasePrice, rentLevels, colorGroup
     ↓
PropertyState during game
     ↓
owner, currentRentLevel
```

### Event path

```text
EVT_01
     ↓
EventDefinition (data/editions/uk/events.json)
     ↓
printed rule, parsed action, parameters
     ↓
Event Engine later executes it
```

---

## Master Data Files

| File | Records | Purpose |
| ---- | ------- | ------- |
| `data/editions/uk/properties.json` | 22 | Machine-readable property definitions |
| `data/properties.csv` | 22 | Human-readable property data |
| `data/editions/uk/events.json` | 23 | Machine-readable event definitions |
| `data/events.csv` | 23 | Human-readable event data |
| `data/property_extraction_report.txt` | — | Per-property extraction audit |
| `data/event_extraction_report.txt` | — | Per-event extraction audit |
| `data/game_data_validation.txt` | — | Structural and content validation report |

These files link to the card registry via `propertyId` / `eventId` and `qrPayload`. Asset paths reference preferred front images from `data/common/card_registry.json`.

---

## Property Data Model

Each property record includes:

| Field | Description |
| ----- | ----------- |
| `propertyId` | Stable ID (`PRP_01`–`PRP_22`) |
| `name` | Property name from card |
| `sequence` | Board sequence number (gold coin on card) |
| `qrPayload` | QR lookup value from Step 2 |
| `frontAsset` / `qrAsset` | Preferred image paths |
| `colorGroup` | Stable color group code (e.g. `BROWN`, `LIGHT_BLUE`) |
| `colorGroupLabel` | Human-readable color label |
| `purchasePrice` | Purchase cost |
| `initialRentLevel` | Rent level when first purchased (1 for all properties) |
| `rentLevels` | Array of `{level, amount}` for levels 1–5 |
| `maximumRentLevel` | Highest rent level (5 for all properties) |
| `extractionStatus` | `COMPLETE`, `NEEDS_REVIEW`, or `UNREADABLE` |
| `notes` | Extraction caveats |

### Property color groups

| colorGroup | Properties |
| ---------- | ---------- |
| BROWN | Old Kent Road, Whitechapel Road |
| LIGHT_BLUE | The Angel Islington, Euston Road, Pentonville Road |
| PINK | Pall Mall, Whitehall, Northumberland Avenue |
| ORANGE | Bow Street, Marlborough Street, Vine Street |
| RED | Strand, Fleet Street, Trafalgar Square |
| YELLOW | Leicester Square, Coventry Street, Piccadilly |
| GREEN | Regent Street, Oxford Street, Bond Street |
| DARK_BLUE | Park Lane, Mayfair |

### Rent level structure

All 22 properties use **5 rent levels**. The card front displays `RENT LEVEL` indicators (1–5) and corresponding `RENT` amounts. Example (PRP_01 Old Kent Road):

| Level | Rent |
| ----- | ---- |
| 1 | 70 |
| 2 | 130 |
| 3 | 220 |
| 4 | 370 |
| 5 | 750 |

Full values for all properties are in `data/editions/uk/properties.json`.

### Purchase prices

Purchase prices are **not printed on property card fronts** but were confirmed from provided game board data:

| propertyId | Name | purchasePrice |
| ---------- | ---- | ------------- |
| PRP_01 | Old Kent Road | 60 |
| PRP_02 | Whitechapel Road | 60 |
| PRP_03 | The Angel Islington | 100 |
| PRP_04 | Euston Road | 100 |
| PRP_05 | Pentonville Road | 120 |
| PRP_06 | Pall Mall | 140 |
| PRP_07 | Whitehall | 140 |
| PRP_08 | Northumberland Avenue | 160 |
| PRP_09 | Bow Street | 180 |
| PRP_10 | Marlborough Street | 180 |
| PRP_11 | Vine Street | 200 |
| PRP_12 | Strand | 220 |
| PRP_13 | Fleet Street | 220 |
| PRP_14 | Trafalgar Square | 240 |
| PRP_15 | Leicester Square | 260 |
| PRP_16 | Coventry Street | 260 |
| PRP_17 | Piccadilly | 280 |
| PRP_18 | Regent Street | 300 |
| PRP_19 | Oxford Street | 300 |
| PRP_20 | Bond Street | 320 |
| PRP_21 | Park Lane | 350 |
| PRP_22 | Mayfair | 400 |

Full values in `data/editions/uk/properties.json`.

---

## Event Data Model

Each event record includes:

| Field | Description |
| ----- | ----------- |
| `eventId` | Stable ID (`EVT_01`–`EVT_23`) |
| `name` | Event name from card |
| `sequence` | Event sequence |
| `qrPayload` | QR lookup value |
| `frontAsset` / `qrAsset` | Image paths |
| `eventSubtitle` | Short flavour text from the event card |
| `eventDescription` | Full gameplay instruction transcribed from card |
| `printedTextValidated` | Whether card text has been confirmed against source |
| `printedRuleStatus` | Whether the printed rule text is resolved (`RESOLVED` / `NEEDS_REVIEW`) |
| `engineImplementationStatus` | Whether executable engine behaviour is defined (`COMPLETE` / `NEEDS_REVIEW`) |
| `effectClassification` | Tags for game-engine behaviour |
| `parsedEffect` | Structured action representation |
| `requiresPlayerScan` | Whether a Player QR scan is needed |
| `requiresPropertyScan` | Whether a Property QR scan is needed |
| `extractionStatus` | Overall extraction status (reflects engine implementation where applicable) |
| `notes` | Ambiguities or caveats |

Printed rule confirmation and engine implementation are tracked **separately**. All 23 events have `printedRuleStatus = RESOLVED`. Engine behaviour is `COMPLETE` for 17 events and `NEEDS_REVIEW` for 6.

### EVT_21 Total Gridlock — Version 1 architectural principle

The physical board and physical tokens remain authoritative for player position in Version 1.

The Android app must **not** attempt to become a virtual board.

For Total Gridlock, the app can:

- process the Event
- display the required instruction
- prevent any GO payment caused by this movement
- preserve any relevant Jail state
- tell players to physically move eligible tokens to Free Parking

Actual token movement is performed manually by the players. Digital board movement is out of scope for Version 1.

### Scan requirements summary

| requiresPropertyScan | Count | Event IDs |
| -------------------- | ----- | --------- |
| `true` | 17 | EVT_01–06, 08–10, 12, 15–20, 22 |
| `false` | 6 | EVT_07, 11, 13, 14, 21, 23 |

| requiresPlayerScan | Count | Event IDs |
| ------------------ | ----- | --------- |
| `true` | 6 | EVT_06, 07, 09, 11, 14, 23 |
| `false` | 17 | All others |

Some events require **multiple** player or property scans (e.g. EVT_06 swap requires two property scans; EVT_11 requires two player scans). See `eventDescription` and `notes` per event.

### Events modifying rent levels

Events with rent-level effects include: EVT_01, 02, 03, 04, 05, 08, 10, 12, 15, 16, 17, 18, 19, 20, 22. Full classifications are in `data/editions/uk/events.json`.

### Events with bank credits/debits

| Event | Effect |
| ----- | ------ |
| EVT_07 Highway Tax | Pay M50 per owned property |
| EVT_11 Love Is In The Air | Both players collect M200 |
| EVT_23 Wibble Wobble | Both players collect M200 |

---

## Extraction Status (Step 3)

### Properties

| Status | Count |
| ------ | ----- |
| COMPLETE | 22 |
| NEEDS_REVIEW | 0 |
| UNREADABLE | 0 |

All properties have rent levels, color groups, and purchase prices.

### Events

| Metric | Count |
| ------ | ----- |
| Printed instructions validated | 23 / 23 |
| Printed rule resolved | 23 / 23 |
| Engine behaviour complete | 17 / 23 |
| Engine behaviour needs review | 6 / 23 |

Events needing engine implementation review:

| Event | Issue |
| ----- | ----- |
| EVT_08 House Party | Meaning of "neighbours" not defined on card |
| EVT_10 Its A Boy | Meaning of "neighbours" not defined on card |
| EVT_13 On The Run | "Next two rent payments" requires runtime duration tracking |
| EVT_15 Pong What A Stinker | "That side of the board" not defined on card |
| EVT_21 Total Gridlock | Application behaviour while physical token movement remains manual |
| EVT_22 What A Ride | "That side of the board" not defined on card |

---

## Static vs Runtime Rule

`properties.json` and `events.json` contain **static master data only**.

These values must **never** appear in static definition files:

```text
current owner
current rent level
current game ID
player balances
```

Those belong to `GameSession` / `PropertyState` / `PlayerState` at runtime.

---

## Development Tools

| Tool | Purpose |
| ---- | ------- |
| `tools/extract_card_data.py` | Build properties/events JSON and CSV from registry + verified extractions |
| `tools/game_data_extractions.py` | Verified extraction data from card front images |
| `tools/validate_game_data.py` | Validate structure, registry alignment, and content completeness |

Re-run after extraction updates:

```bash
cd monopoly-ultimate-banking-qr/tools
python extract_card_data.py
python validate_game_data.py
```

---

## Validation Summary

```text
STRUCTURAL RESULT: PASS
CONTENT RESULT:    REVIEW REQUIRED
```

- 22 / 22 properties structurally valid
- 23 / 23 events structurally valid
- 22 / 22 properties have rent levels, color groups, and purchase prices
- 17 / 23 events fully complete; 6 need review

See `data/game_data_validation.txt` for the full report.
