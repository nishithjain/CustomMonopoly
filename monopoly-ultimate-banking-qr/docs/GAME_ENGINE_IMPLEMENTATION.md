# Game Engine Implementation

Pure Kotlin game engine for Monopoly Ultimate Banking, implemented in the `game-core` Android module.

## Package layout

| Package | Responsibility |
| ------- | -------------- |
| `com.boardbanker.core.model` | Immutable definitions, mutable session state, transactions |
| `com.boardbanker.core.validation` | JSON loading (`GameDefinitionLoader`) and structural validation |
| `com.boardbanker.core.command` | Sealed `GameCommand` types issued by UI/scanner |
| `com.boardbanker.core.engine` | `GameEngine` interface and `DefaultGameEngine` orchestration |
| `com.boardbanker.core.rules` | Property, rent, colour set, GO, jail, debt, bankruptcy, undo |
| `com.boardbanker.core.event` | `EventEngine` — handlers keyed by `actionType` (not per-eventId) |
| `com.boardbanker.core.transaction` | `TransactionFactory` for auditable records |
| `com.boardbanker.core.error` | Sealed `GameError` types |

## Source of truth

- Rules: `docs/GAME_RULES.md`, `EVENT_ENGINE_RULES.md`, `TRANSACTION_RULES.md`, `RULE_TEST_SCENARIOS.md`
- Data: `data/*.json` (cards, properties, events, `event_engine_rules`, `board_relationships`, `game_rules`)

`GameDefinitionLoader.loadAll()` merges cards, properties, events, engine rules, board relationships, and game rules into `GameDefinitions`.

## Processing pipeline

```text
GameCommand → DefaultGameEngine → rule modules / EventEngine → atomic state commit → Transaction(s) → GameResult
```

`GameResult` carries the new `GameSession`, `GameOutcome`, transactions, optional `PhysicalAction` instructions, and `GameError` on rejection.

## Confirmed rule highlights

| Rule | Implementation |
| ---- | -------------- |
| Players | 2–4, starting balance M1500 |
| Rent | `rentLevels[currentRentLevel]`; never derived from purchase price |
| Visitor landing | Pay rent, then level +1 (clamp 5) |
| Owner landing | No rent; level +1 (clamp 5); jailed owner skips increase |
| Jailed owner | No rent collected on their properties |
| Colour set bonus | +2 single owner / +1 multi-owner, once per group |
| EVT_13 | Global `FORCE_LEVEL_1_RENT`, `remainingUses = 2`; owner landing does not consume |
| EVT_21 | Physical move non-jailed to Free Parking; no GO; jail preserved |
| GO | M200 on normal dice movement only |
| Location | M100 to bank; no GO; unowned → pending purchase |
| Auction | M20 fixed increments; jailed cannot bid; winner rent level 1 |
| Debt | Properties at `purchasePrice`; bankruptcy ends game |
| Undo | Depth 1; events not undoable; blocked during debt resolution |

## Event engine

`EventEngine` dispatches on `EventEngineRule.actionType`:

- `MOVE_THEN_PROPERTY_CHOICE`, `INCREASE_COLOR_SET_RENT_LEVEL`, `DECREASE_COLOR_SET_RENT_LEVEL`
- `RESET_PROPERTY_RENT_LEVEL`, `SET_PROPERTY_RENT_LEVEL`, `SWAP_PROPERTIES`
- `PAY_PER_OWNED_PROPERTY`, `CREDIT_BOTH_PLAYERS`, `TEMPORARY_RENT_CAP`
- `SEND_PLAYER_TO_JAIL`, `ADJUST_SELECTED_AND_NEIGHBOUR_RENT_LEVELS`
- `DECREASE_BOARD_SIDE_RENT_LEVEL`, `INCREASE_BOARD_SIDE_RENT_LEVEL`, `TOTAL_GRIDLOCK_V1`

Neighbour and board-side targeting uses `BoardRelationships` from `data/board_relationships.json` — no hard-coded event/property pairs in engine code.

## Commands

Key `GameCommand` types: `CreateGame`, `RegisterPlayer`, `StartGame`, `ProcessPropertyLanding`, `PurchaseProperty`, `ApplyEvent`, `EventPropertyChoice`, `PayGoSalary`, `PayLocationFee`, jail commands, auction commands, `ResolveDebt`, `CheckBankruptcy`, `UndoLastAction`.

## Tests

Unit tests in `game-core/src/test/kotlin/com/boardbanker/core/` cover `RULE_TEST_SCENARIOS.md` cases and all 23 events (`EventTests.all23EventsApplySuccessfully`).

Run:

```bash
cd android-app
./gradlew :game-core:test
```

Validate data + tests:

```bash
python tools/validate_game_engine.py
```

## Android integration

The `app` module depends on `game-core`. Scanner/UI layers resolve QR → card ID → `GameCommand` and call `DefaultGameEngine.process(session, command)`. Only the engine mutates balances and ownership.
