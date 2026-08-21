# Game Engine Design

Implementation-independent architecture for the Monopoly Ultimate Banking QR Scanner Replacement.

No Kotlin or Android code exists in this step. This document defines how the future application must be structured.

---

## Architectural Boundaries

| Layer | Responsibility |
| ----- | -------------- |
| **CardDefinition** | What physical card was scanned (`qrPayload` → `cardId`) |
| **PropertyDefinition / EventDefinition** | Static master data (`data/editions/uk/properties.json`, `data/editions/uk/events.json`) |
| **GameRules** | What actions mean (`docs/GAME_RULES.md`, `data/common/game_rules.json`) |
| **GameSession State** | What is happening in this particular game |
| **QR Scanner** | Input only — never mutates balances, ownership, or rent |
| **Game Engine** | Only component allowed to decide and change game state |

---

## Processing Pipeline

```text
Physical game action
        ↓
Required scans / button action
        ↓
Validation
        ↓
GameCommand
        ↓
Game Engine
        ↓
Rule Validation
        ↓
State Transition
        ↓
Transaction
        ↓
New Game State
        ↓
User-visible result
```

---

## Component Flow

```text
UI / QR Scanner
       ↓
GameCommand
       ↓
Game Engine
       ↓
Rule Validation
       ↓
State Transition
       ↓
Transaction
       ↓
New Game State
```

---

## QR Resolution Flow

```text
QR Scanner
    ↓
qrPayload
    ↓
Card Registry (data/common/card_registry.json)
    ↓
cardId + cardType
    ↓
PropertyDefinition / EventDefinition / PlayerDefinition
    ↓
GameCommand
    ↓
Game Engine
```

### Hard rule

Scanner code must **never** contain logic such as:

```text
if QR == EVT_05:
    property.rentLevel = 5
```

Correct flow:

```text
QR Scanner → EVT_05 → Card Registry → EventDefinition → GameCommand → Game Engine → SET_PROPERTY_RENT_LEVEL(5)
```

---

## Conceptual Commands

Future commands (names may change during Android implementation):

| Command | Purpose |
| ------- | ------- |
| `RegisterPlayer` | Add player to active game |
| `PurchaseProperty` | Buy unowned property |
| `PayRent` | Transfer rent payer → owner |
| `IncreaseRentLevel` | Single property level change |
| `DecreaseRentLevel` | Single property level change |
| `SetRentLevel` | Set property to explicit level |
| `ApplyEvent` | Execute Event engine rule |
| `CreditPlayer` | Bank → player |
| `DebitPlayer` | Player → bank |
| `SwapProperties` | Exchange ownership between players |
| `SendPlayerToJail` | Jail-related banking/state update |
| `StartAuction` | Begin auction flow |
| `CompleteAuction` | Finalize winning bid |
| `DeclareBankruptcy` | Eliminate player / resolve assets |
| `UndoLastAction` | Restore prior reversible state |
| `CreateTemporaryEffect` | Apply global/timed rule modifier |
| `ConsumeTemporaryEffect` | Decrement/use temporary effect |

Commands are issued by UI/workflow layers. Only the Game Engine executes them.

---

## Atomicity

```text
Validate everything first
        ↓
Calculate proposed state
        ↓
If validation fails:
    change nothing
        ↓
If validation succeeds:
    commit all related state changes together
        ↓
create transaction/action record
```

Example: rent payment must never debit payer without crediting owner.

---

## Physical Board Principle (Version 1)

```text
Physical board + physical tokens remain authoritative for physical position.
```

The Android app is **not** a virtual board.

The app may:

- process banking operations
- display instructions
- maintain balances, ownership, rent levels, temporary effects
- suppress GO collection when rules require it

The app must **not** digitally move tokens except where explicitly defined as a display/instruction requirement (e.g. EVT_21 Total Gridlock).

---

## Relationship Targeting

Events must target **relationships**, not hard-coded property IDs:

```text
EVT_08 → target relationship = NEIGHBOURS_OF(selectedProperty)
```

not:

```text
if event == EVT_08 and property == PRP_07: change PRP_06 and PRP_08
```

Relationship definitions live in `data/editions/uk/board_relationships.json`.

---

## Save and Resume (GR-SAVE-002)

On app termination or restart during an **in-progress scan workflow**:

```text
DO NOT restore the partially completed workflow
```

Instead:

```text
Restore only the LAST COMMITTED GameSession state
        ↓
Discard transient/uncommitted scan state
        ↓
Return scanner/workflow state to READY
```

Examples:

- Property scanned → waiting for Player → app terminates → on restart: committed session restored, partial workflow discarded, `READY`
- Event scanned → waiting for Property → app terminates → on restart: `READY`, not mid-Event

No balance, ownership, or rent changes occur until a transaction commits. This follows the atomic transaction rule.

---

## Related Documents

| Document | Purpose |
| -------- | ------- |
| [GAME_RULES.md](GAME_RULES.md) | Rule catalogue with stable IDs |
| [EVENT_ENGINE_RULES.md](EVENT_ENGINE_RULES.md) | Per-Event engine specification |
| [TRANSACTION_RULES.md](TRANSACTION_RULES.md) | Transaction types and audit requirements |
| [RULE_TEST_SCENARIOS.md](RULE_TEST_SCENARIOS.md) | Given/When/Then test scenarios |
| [RULE_GAPS.md](RULE_GAPS.md) | Implementation blockers |
