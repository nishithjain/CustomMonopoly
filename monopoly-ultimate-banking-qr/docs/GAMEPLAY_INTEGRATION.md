# Gameplay Integration Architecture

Step 10 connects the QR scanner to Property and Event gameplay.

```text
QR Scanner
    ↓
ResolvedCard
    ↓
GameplayWorkflowController (transient)
    ↓
GameCommand
    ↓
DefaultGameEngine (:game-core)
    ↓
GameResult
    ↓
ActiveGameSessionManager / CommittedGameSessionStore
    ↓
Room
    ↓
GameplayResultMapper
    ↓
Game UI
```

## Boundaries

| Layer | Responsibility |
|-------|----------------|
| Scanner | Decode QR, resolve card identity, duplicate protection |
| Workflow | Collect required scans, build commands, track transient state |
| Game Engine | All Monopoly rules |
| Persistence | Committed `GameSession` snapshots only |
| UI | Display engine results; no rule calculation |

## Not in Step 10

- Auction bidding UI
- GO / Location bank actions
- Debt / bankruptcy resolution UI
- Winner screen
- Virtual board positions
