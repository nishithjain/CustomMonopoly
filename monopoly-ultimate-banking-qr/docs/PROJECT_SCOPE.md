# Project Scope

## In Scope — Version 1

- Android application
- single Android phone/tablet acts as the banker
- offline gameplay
- Player QR scanning
- Property QR scanning
- Event QR scanning
- player balances
- property ownership
- property state/rent level
- purchases
- rent transfers
- Event processing
- bank credits
- bank debits
- auctions if required by the original rules
- jail-related banking actions if required
- GO-related banking actions if required
- Location-space banking actions if required
- bankruptcy handling
- transaction history
- undo-last-action
- automatic save
- resume game

## Out of Scope — Version 1

- custom geographical editions
- board generation
- custom property names
- user-generated games
- online multiplayer
- multiple synchronized phones
- user accounts
- cloud database
- leaderboards
- virtual dice
- virtual game board
- AI-generated Events
- iOS
- web application

## Key Design Principle

The Android application must be capable of running the original physical game without requiring the original electronic banking unit.

The app is a behavioral replacement for the banking/scanner hardware. Players continue to use the physical board, tokens, dice, and cards. The phone handles only the banking operations that the original unit performed.

## Architectural Principles

These principles apply across all Version 1 development:

### Rule 1 — QR identifies a card, not game state

```text
QR value
   ↓
Card ID
   ↓
Card Definition
```

QR code identifies a card. It does NOT contain runtime game state.

### Rule 2 — No runtime state in QR codes

Never store information such as this inside a QR:

```text
owner=player1
currentBalance=1500
currentRentLevel=3
```

Those values belong to the active GameSession.

### Rule 3 — Static data and runtime state are separate

```text
PropertyDefinition
        +
PropertyState
```

Master data (definitions) is immutable during a game. Mutable game-specific data lives in state records tied to a GameSession.

### Rule 4 — All monetary changes go through the Game Engine

All monetary changes must eventually go through one transaction/game-engine mechanism.

Avoid allowing UI or scanner code to directly do:

```text
player.balance -= amount
```

### Rule 5 — Offline-first

The app must eventually be fully usable without Internet connectivity.

### Rule 6 — Physical board is the source of player position

The physical board remains the source of player position in Version 1.

We are NOT digitally tracking token movement unless a banking operation specifically requires related state.

### Rule 7 — Original banking unit is the behavioral reference

The original physical banking unit is our behavioral reference during compatibility testing.

Later development should allow scenarios to be compared:

```text
Same physical game action

Original Banking Unit
        VS
Android Application

Expected final banking/game state must match.
```
