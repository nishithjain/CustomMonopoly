# Player Setup Workflow

Step 9 connects the QR scanner, Game Engine, and Room persistence for **player registration only**.

## End-to-End Flow

```text
NEW GAME
    ↓
CreateGame (Game Engine)
    ↓
Persist committed SETUP session
    ↓
Player Setup screen
    ↓
SCAN PLAYER CARD
    ↓
CameraX + ML Kit (FORMAT_QR_CODE)
    ↓
ScanGate (duplicate protection)
    ↓
CardResolver (:game-core)
    ↓
expectedCardType = USER enforced by setup workflow
    ↓
RegisterPlayer (Game Engine)
    ↓
Persist committed session (autosave)
    ↓
Repeat for 2–4 players
    ↓
StartGame (Game Engine)
    ↓
status = ACTIVE
    ↓
Persist committed session
    ↓
Game Dashboard
```

## Module Responsibilities

| Layer | Module | Responsibility |
|-------|--------|----------------|
| UI | `:app` (`PlayerSetupScreen`, `GameScreen`) | Setup and dashboard presentation |
| Coordinator | `:app` (`ActiveGameSessionManager`) | Single authoritative committed session |
| Persistence | `:app` (`CommittedGameSessionStore` + Room) | Durable snapshots after engine success |
| Rules | `:game-core` (`DefaultGameEngine`) | CreateGame, RegisterPlayer, StartGame |
| QR | `:app` scanner + `:game-core` resolver | Decode and identify cards |

## Player Card Rules

- Only `CardType.USER` cards are accepted during Player Setup.
- Property and Event cards are rejected with **PLAYER CARD EXPECTED** — no `GameCommand` is executed.
- Unknown QR payloads show **UNKNOWN GAME CARD** — no session mutation.
- Duplicate scans of the same visible QR are suppressed by `ScanGate` (one registration attempt per card presentation).

## Balances

Starting balance **M1500** comes from `GameRulesConfig.startingBalance` in the Game Engine. Android UI renders `PlayerState.balance` only.

## Persistence (GR-SAVE-002)

- QR decode alone does **not** persist.
- Only successful committed `GameResult` values from `RegisterPlayer` and `StartGame` are saved.
- Transient scanner workflow state is not persisted; after restart the scanner returns to READY while committed players remain.

## Resume

| Saved status | Home action | Destination |
|--------------|-------------|-------------|
| SETUP | RESUME SETUP | Player Setup (committed players restored) |
| ACTIVE | RESUME GAME | Game Dashboard |

## Out of Scope (Step 10+)

Player Setup does **not** interpret Property or Event cards for gameplay. No purchase, rent, GO, Jail, auction, debt, or winner flows are integrated in Step 9.
