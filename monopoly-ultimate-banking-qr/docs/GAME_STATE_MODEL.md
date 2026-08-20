# Game State Model

This document defines the **conceptual data model** for the Monopoly Ultimate Banking QR Scanner Replacement application.

No Kotlin classes or database schemas are defined here. This is the source-of-truth specification for what data exists and how it relates.

---

## CardDefinition

Represents any scannable card in the physical game.

```text
cardId
qrValue
cardType
```

`cardType` is one of:

```text
PLAYER
PROPERTY
EVENT
```

The QR code on a physical card should resolve to a **stable internal identifier** (`cardId`). The scanner decodes `qrValue`, looks up the matching `CardDefinition`, and passes `cardId` and `cardType` to the Game Engine.

The QR code must not encode runtime game state. See [PROJECT_SCOPE.md](PROJECT_SCOPE.md) — Architectural Principles.

---

## PlayerDefinition

Static master data for a Player card.

```text
playerId
qrValue
displayName
displayColor
```

- `playerId` — stable internal identifier
- `qrValue` — raw value encoded in the physical QR code
- `displayName` — human-readable label shown in the UI (e.g. card color or printed name)
- `displayColor` — visual identifier for UI theming

PlayerDefinition is immutable master data loaded from the `data/` directory (format TBD).

---

## PlayerState

Game-specific mutable data for a player within an active GameSession.

```text
gameId
playerId
balance
active
bankrupt
jailStatus
```

| Field | Description |
| ----- | ----------- |
| `gameId` | Foreign key to the active GameSession |
| `playerId` | References PlayerDefinition |
| `balance` | Current account balance |
| `active` | Whether the player is participating in the current game |
| `bankrupt` | Whether the player has been eliminated |
| `jailStatus` | Jail-related state if required by original rules |

Fields marked as dependent on original rules (e.g. `jailStatus`, exact semantics of `active` after bankruptcy) are **provisional** until confirmed. Use `TO_BE_CONFIRMED_FROM_ORIGINAL_RULES` during implementation when exact behavior is unknown.

---

## PropertyDefinition

Static master data for a Property card.

```text
propertyId
qrValue
displayName
purchasePrice
rentStructure
maximumRentLevel
```

| Field | Description |
| ----- | ----------- |
| `propertyId` | Stable internal identifier |
| `qrValue` | Raw QR-encoded value |
| `displayName` | Property name as printed on the physical card |
| `purchasePrice` | Cost to purchase when unowned |
| `rentStructure` | Rent amounts per rent level (structure TBD) |
| `maximumRentLevel` | Highest achievable rent level for this property |

Exact values are **not populated** in Step 1. They will be supplied from original game data or confirmed from the manual.

---

## PropertyState

Mutable game-specific data for a property within an active GameSession.

```text
gameId
propertyId
ownerPlayerId
currentRentLevel
otherRuntimeState
```

| Field | Description |
| ----- | ----------- |
| `gameId` | Foreign key to the active GameSession |
| `propertyId` | References PropertyDefinition |
| `ownerPlayerId` | `null` if unowned; otherwise references `playerId` |
| `currentRentLevel` | Current rent tier (e.g. after owner improvements) |
| `otherRuntimeState` | Any additional runtime flags required by original rules |

> **PropertyDefinition is immutable master data. PropertyState changes during a game.**

At game start, PropertyState records are created for every property with default values (no owner, initial rent level). Ownership and rent level change only through Game Engine actions.

---

## EventDefinition

Static master data for an Event card.

```text
eventId
qrValue
displayText
actionType
parameters
targetType
```

| Field | Description |
| ----- | ----------- |
| `eventId` | Stable internal identifier |
| `qrValue` | Raw QR-encoded value |
| `displayText` | Text printed on the physical Event card |
| `actionType` | Category of effect (e.g. bank credit, rent change, player transfer) |
| `parameters` | Event-specific configuration (amounts, directions, etc.) |
| `targetType` | What the Event may require: `NONE`, `PLAYER`, `PROPERTY`, `MULTIPLE`, etc. |

Exact original Event-card effects will be supplied or confirmed later. Do not invent effect values during implementation.

---

## GameSession

Represents one in-progress or saved game.

```text
gameId
status
createdAt
updatedAt
currentPhase
scannerState
```

| Field | Description |
| ----- | ----------- |
| `gameId` | Unique session identifier |
| `status` | e.g. `ACTIVE`, `PAUSED`, `COMPLETED` |
| `createdAt` | Session creation timestamp |
| `updatedAt` | Last modification timestamp (used for auto-save) |
| `currentPhase` | High-level game phase if tracked |
| `scannerState` | Current scanner workflow state (see [SCAN_WORKFLOWS.md](SCAN_WORKFLOWS.md)) |

A GameSession aggregates all PlayerState, PropertyState, and Transaction records for one play session.

---

## Transaction

Immutable record of a completed (or undone) banking operation.

```text
transactionId
gameId
timestamp
transactionType
fromEntity
toEntity
playerId
propertyId
eventId
amount
stateBefore
stateAfter
reversible
```

| Field | Description |
| ----- | ----------- |
| `transactionId` | Unique transaction identifier |
| `gameId` | Foreign key to GameSession |
| `timestamp` | When the transaction was executed |
| `transactionType` | Action ID or category (e.g. `PURCHASE`, `RENT`, `BANK_CREDIT`) |
| `fromEntity` | Source of funds (player ID, `BANK`, etc.) |
| `toEntity` | Destination of funds |
| `playerId` | Primary player involved, if applicable |
| `propertyId` | Property involved, if applicable |
| `eventId` | Event involved, if applicable |
| `amount` | Monetary amount, if applicable |
| `stateBefore` | Snapshot or delta reference for undo |
| `stateAfter` | Snapshot or delta reference for audit |
| `reversible` | Whether this transaction can be undone |

### Why transactions matter

- **History** — players and operators can review what happened
- **Debugging** — developers can trace incorrect behavior
- **Undo** — the last reversible transaction can restore `stateBefore`
- **Auditing** — every monetary change has a paper trail
- **Compatibility testing** — final state after a scenario can be compared against the original banking unit

All monetary changes must flow through the Game Engine and produce a Transaction record. UI and scanner layers must never modify balances directly.

---

## Entity Relationships (Conceptual)

```text
GameSession
  ├── PlayerState[]     → PlayerDefinition
  ├── PropertyState[]   → PropertyDefinition
  └── Transaction[]

CardDefinition
  ├── may reference PlayerDefinition  (cardType = PLAYER)
  ├── may reference PropertyDefinition (cardType = PROPERTY)
  └── may reference EventDefinition   (cardType = EVENT)
```

Master definitions live outside any GameSession. State records are created when a new game starts and persist until the session ends or is deleted.
