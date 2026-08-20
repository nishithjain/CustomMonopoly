# QR Scanner Architecture

Step 7 implements offline QR card recognition for the physical Monopoly Ultimate Banking game. The scanner resolves printed card QR codes to internal card identities. It does **not** execute game rules.

## Pipeline

```text
CameraX Preview + ImageAnalysis
        ↓
ML Kit Barcode Scanning (FORMAT_QR_CODE only)
        ↓
Decoded QR payload (String)
        ↓
ScanGate (duplicate suppression + workflow lock)
        ↓
CardResolver (:game-core, pure Kotlin)
        ↓
ResolvedCard (cardId, cardType, displayName)
        ↓
Scanner UI (validation screen)
```

## Module Boundaries

| Layer | Module | Responsibility |
|-------|--------|----------------|
| Camera + ML Kit | `:app` (`scanner.camera`) | Decode QR strings from camera frames |
| QR abstraction | `:app` (`QrCodeSource`) | Decouple UI from camera/ML Kit |
| Duplicate protection | `:game-core` (`ScanGate`) | Accept one scan per visible card |
| Resolution | `:game-core` (`CardResolver`) | Map payload → registry card |
| Game data load | `:app` (`AndroidGameDataLoader`) | Read assets, delegate to `GameDefinitionLoader` |
| Validation UI | `:app` (`ScannerScreen`) | Debug/test screen for physical cards |

## Important Constraints

- **Scanner DOES NOT modify `GameSession`.**
- **CardResolver DOES NOT execute `GameCommands`.**
- No rent, purchase, event execution, or balance changes occur in Step 7.
- Resolution uses the master Card Registry (`cards.json` → `GameDefinitions.cardsByQrPayload`).
- Unknown payloads return `CardResolution.UnknownQr` without side effects.

## Privacy / Camera

- Camera frames are analyzed in memory only.
- No photographs, video recordings, or frame uploads are stored or transmitted.
- No `INTERNET` permission is required or used for scanning or resolution.
- QR decoding and card lookup are fully offline via `app/src/main/assets/game/`.

## Duplicate Scan Protection

`ScanGate` accepts the first detection of a payload, ignores repeats while the same QR remains visible, and re-arms after sustained no-detection frames. After a successful resolve, the workflow lock remains until the user taps **SCAN ANOTHER CARD**.

## Testing Without Camera

`FakeQrCodeSource` implements `QrCodeSource` for unit tests. `:game-core` tests cover `CardResolver` (49/49 registry payloads) and `ScanGate` duplicate behavior.

## Legacy Barcodes

Physical `*_Back.png` hardware barcodes are not supported. Only `*_Back_QR.png` payloads (`MUB:PL:*`, `MUB:E:E##`, `MUB:P:##`) are recognized.

## Physical QR validation (device tested)

Basic physical validation completed on a real Android device:

| Check | Result |
|-------|--------|
| User card | PASS |
| Property card | PASS |
| Event card | PASS |
| Duplicate protection | PASS |
| Unknown QR | PASS |

Full 49-card physical checklist remains `NOT_TESTED` unless each printed card is individually scanned (`docs/PHYSICAL_QR_TEST_CHECKLIST.md`).
