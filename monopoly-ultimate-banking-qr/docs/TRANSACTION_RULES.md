# Transaction Rules

All monetary and ownership-changing Game Engine operations must produce auditable transaction records.

See [GAME_STATE_MODEL.md](GAME_STATE_MODEL.md) for the conceptual `Transaction` entity.

---

## Principles

1. All balance modifications go through the Game Engine.
2. UI and scanner code must never directly alter `player.balance`.
3. Every successful state-changing command creates a transaction record.
4. Failed validation produces **no** partial state mutation.
5. Reversible actions must store sufficient `stateBefore` data for Undo.

---

## Transaction Types

| Type | Description |
| ---- | ----------- |
| `GAME_START` | New game initialized |
| `PROPERTY_PURCHASE` | Player buys unowned property |
| `RENT_PAYMENT` | Rent transferred payer → owner |
| `BANK_CREDIT` | Bank credits player |
| `BANK_DEBIT` | Player pays bank |
| `PROPERTY_RENT_LEVEL_CHANGE` | One or more properties' rent level changed (including colour-set completion bonus) |
| `PROPERTY_OWNERSHIP_CHANGE` | Ownership assigned or cleared |
| `PROPERTY_SWAP` | Two properties exchange owners |
| `COLOR_SET_COMPLETION_BONUS` | One-time rent bonus when colour group completed (may share record with PROPERTY_RENT_LEVEL_CHANGE) |
| `LOCATION_FEE` | M100 paid to Bank for Location movement |
| `AUCTION_WIN` | Property acquired via timed auction |
| `EVENT_APPLIED` | Event processed (may wrap sub-effects) |
| `TEMPORARY_EFFECT_CREATED` | Global/timed modifier added |
| `TEMPORARY_EFFECT_CONSUMED` | Modifier use consumed |
| `JAIL_STATUS_CHANGE` | Jail-related state update |
| `AUCTION_PURCHASE` | Property acquired via auction |
| `BANKRUPTCY` | Player eliminated / assets resolved |
| `UNDO` | Prior action reversed |

---

## Atomicity Rule

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

### Rent payment example

| Outcome | Allowed |
| ------- | ------- |
| Payer debited AND owner credited | Yes |
| Payer debited, owner NOT credited | **No** |
| Neither changed after failed validation | Yes |

---

## Required Transaction Fields (Conceptual)

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

---

## Banking Operation Mapping

| Operation | Typical Transaction Type |
| --------- | ------------------------ |
| `CREDIT_PLAYER` | `BANK_CREDIT` |
| `DEBIT_PLAYER` | `BANK_DEBIT` |
| `TRANSFER_PLAYER_TO_PLAYER` | `RENT_PAYMENT` or `EVENT_APPLIED` |
| `TRANSFER_PLAYER_TO_BANK` | `BANK_DEBIT` |
| `TRANSFER_BANK_TO_PLAYER` | `BANK_CREDIT` |

`amount` must be positive. Direction is expressed by `fromEntity` / `toEntity`.

---

## Multi-Property / Multi-Player Events

A single `EVENT_APPLIED` transaction may reference:

- `eventId`
- multiple affected `propertyId` values in `stateBefore` / `stateAfter`
- multiple `playerId` values when applicable

Sub-effects (e.g. colour-set rent changes) should still be committed atomically.

---

## Undo Requirements

Undo requires `stateBefore` sufficient to restore:

- balances
- ownership
- rent levels
- temporary effects
- jail state (if tracked)
- bankruptcy state

Undo is supported with `undoDepth = 1` for: rent payment, property purchase, GO salary, Jail payment, Location fee. Event transactions are **not** undoable. Debt resolution blocks undo. See GR-UNDO-001 in `docs/GAME_RULES.md`.

---

## Non-Reversible Actions

Actions that may be unsafe or non-reversible must require explicit confirmation before execution. Exact list: **NEEDS_CONFIRMATION**.
