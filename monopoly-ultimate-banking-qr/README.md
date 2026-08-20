# Monopoly Ultimate Banking QR Scanner Replacement

## Project Name

Monopoly Ultimate Banking QR Scanner Replacement

## Objective

This project will create an **Android application** that replaces the physical **Ultimate Banking electronic unit** used with the existing **Monopoly Ultimate Banking** board game.

The physical game board, dice, tokens, Property cards, and Event cards remain in use. Only the electronic banking/scanner unit is replaced by a single Android phone or tablet acting as the banker.

The application will eventually:

- scan Player QR cards
- scan Property QR cards
- scan Event QR cards
- maintain player balances
- maintain property ownership
- maintain property/rent state
- process purchases
- process rent
- process Events
- process bank transactions
- save and resume a game
- maintain transaction history

## Architecture Overview

```text
Physical Card
    ↓
QR Code
    ↓
Android Camera
    ↓
QR/Card Resolver
    ↓
Game Engine
    ↓
Game State
```

## Architectural Rule

> QR scanning identifies a card. QR scanning must not contain Monopoly game logic. Game rules belong in the Game Engine.

The QR code resolves to a stable card identity. All game rules, monetary calculations, ownership changes, and state transitions are handled exclusively by the Game Engine.

## Documentation

| Document | Purpose |
| -------- | ------- |
| [docs/PROJECT_SCOPE.md](docs/PROJECT_SCOPE.md) | Version 1 scope, in/out of scope, design principles |
| [docs/ULTIMATE_BANKING_ACTIONS.md](docs/ULTIMATE_BANKING_ACTIONS.md) | Action catalogue — behavioral reference for all banking operations |
| [docs/GAME_STATE_MODEL.md](docs/GAME_STATE_MODEL.md) | Conceptual data model for cards, players, properties, events, sessions |
| [docs/SCAN_WORKFLOWS.md](docs/SCAN_WORKFLOWS.md) | Scanner state machine and scan-handling rules |
| [docs/OPEN_QUESTIONS.md](docs/OPEN_QUESTIONS.md) | Unconfirmed rules and data still required from the original game |
| [data/README.md](data/README.md) | Placeholder for future master data (players, properties, events, QR mappings) |

## Status

**Step 1 — Functional specification and project documentation.** No Android implementation has been started.
