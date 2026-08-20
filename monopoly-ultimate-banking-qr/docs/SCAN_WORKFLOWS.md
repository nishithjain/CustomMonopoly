# Scan Workflows

This document describes the scanner as a **conceptual state machine**. State names and transitions may change during Android implementation, but the behavioral rules defined here must be preserved.

---

## Scanner States

High-level states the scanner/UI layer may occupy:

```text
READY
PROCESSING_SCAN
PROPERTY_IDENTIFIED
EVENT_IDENTIFIED
WAITING_FOR_PLAYER
WAITING_FOR_PROPERTY
WAITING_FOR_CONFIRMATION
PROCESSING_TRANSACTION
SHOWING_RESULT
ERROR
```

| State | Meaning |
| ----- | ------- |
| `READY` | Scanner is idle; awaiting next scan or user action |
| `PROCESSING_SCAN` | QR detected; decoding and resolving card identity |
| `PROPERTY_IDENTIFIED` | Property card recognized; Game Engine determining next step |
| `EVENT_IDENTIFIED` | Event card recognized; Game Engine determining required inputs |
| `WAITING_FOR_PLAYER` | A Player QR scan is required to continue |
| `WAITING_FOR_PROPERTY` | A Property QR scan is required to continue |
| `WAITING_FOR_CONFIRMATION` | User must confirm before transaction executes |
| `PROCESSING_TRANSACTION` | Game Engine executing state change |
| `SHOWING_RESULT` | Displaying outcome to user before returning to `READY` |
| `ERROR` | Recoverable or fatal scan/validation error |

These names are conceptual and can change during implementation.

---

## Generic QR Flow

```text
Camera detects QR
        ↓
Ignore if scanner locked
        ↓
Decode QR
        ↓
Resolve CardDefinition
        ↓
Validate expected card type
        ↓
Send card identity to Game Engine
        ↓
Game Engine decides next state/action
        ↓
Update UI
```

### Core Rule

> The QR scanner must never directly modify player balances, ownership, rent levels, or other game state.

Only the Game Engine can request state changes. The scanner's responsibility ends at:

1. Decoding the QR value
2. Resolving it to a `cardId` and `cardType`
3. Reporting the result to the Game Engine
4. Reflecting the Game Engine's instructed UI/scanner state

---

## Duplicate Scan Protection

Physical cards may remain in the camera view for multiple frames. The scanner must not process the same scan repeatedly.

```text
QR detected
    ↓
Lock scanner for current action
    ↓
Process only once
    ↓
Wait for card to leave camera / workflow to advance
    ↓
Unlock scanner when appropriate
```

### Conceptual variables (not implemented in Step 1)

```text
lastScannedQr
lastScanTimestamp
scannerLocked
currentExpectedCardType
```

### Behavioral requirements

- A scan of the same `qrValue` within a short debounce window must be ignored.
- After a successful scan advances the workflow, re-scanning the same card should not re-trigger the same action unless the workflow explicitly returns to a state expecting that card again.
- The scanner lock must be released when the workflow returns to `READY` or when the user cancels the current action.

---

## Wrong Card Handling

When the user scans a card type that does not match what the current workflow expects:

```text
App expects PLAYER

User scans EVENT
        ↓
Do NOT alter game state
        ↓
Show:
"Player card expected"
        ↓
Remain WAITING_FOR_PLAYER
```

### Rules

- Wrong-card scans must never modify game state.
- The error message should indicate what card type is expected.
- The workflow remains in its current waiting state; the user can scan the correct card without restarting the action.
- Unknown or unresolvable QR codes show an appropriate error and do not advance the workflow.

---

## Workflow Examples (Conceptual)

### Property purchase (A003)

```text
READY
  → User initiates property interaction (or scans Property QR first)
  → PROCESSING_SCAN
  → PROPERTY_IDENTIFIED
  → Game Engine: property unowned → WAITING_FOR_PLAYER
  → PROCESSING_SCAN (Player QR)
  → WAITING_FOR_CONFIRMATION (if required)
  → PROCESSING_TRANSACTION
  → SHOWING_RESULT
  → READY
```

### Check balance (A002)

```text
READY
  → PROCESSING_SCAN (Player QR)
  → Game Engine: lookup balance
  → SHOWING_RESULT
  → READY
```

### Event processing (A006)

```text
READY
  → PROCESSING_SCAN (Event QR)
  → EVENT_IDENTIFIED
  → Game Engine: determine required additional inputs
  → WAITING_FOR_PLAYER and/or WAITING_FOR_PROPERTY (as required)
  → WAITING_FOR_CONFIRMATION (if required)
  → PROCESSING_TRANSACTION
  → SHOWING_RESULT
  → READY
```

Exact scan order for each action is defined in [ULTIMATE_BANKING_ACTIONS.md](ULTIMATE_BANKING_ACTIONS.md). Where the original banking unit's scan order is unknown, it is marked `TO_BE_CONFIRMED_FROM_ORIGINAL_RULES`.

---

## Separation of Concerns

| Layer | Responsibility |
| ----- | -------------- |
| Camera / QR decoder | Capture image, extract raw QR string |
| Card Resolver | Map `qrValue` → `CardDefinition` |
| Scanner state machine | Enforce expected card types, debounce, lock/unlock |
| Game Engine | Validate rules, compute amounts, mutate state, create transactions |
| UI | Display prompts, confirmations, results, errors |

The scanner and UI layers communicate with the Game Engine through a defined interface. They never bypass it for state mutations.
