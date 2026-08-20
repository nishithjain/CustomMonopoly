# Property Workflow

Step 10 connects Property QR scans to the Game Engine through a transient workflow controller.

## Flow

```text
SCAN CARD (active game)
    ↓
Property QR resolved
    ↓
GameplayWorkflowController.onPropertyScanned
    ↓
If unowned:
    BUY / AUCTION choice
        ↓
    Scan purchasing Player
        ↓
    PurchaseProperty command
If owned:
    Show owner + rent info
        ↓
    Scan landing Player
        ↓
    ProcessPropertyLanding command
    ↓
Game Engine
    ↓
CommittedGameSessionStore
    ↓
Result UI
```

## Rules

- Rent amounts, level changes, jail-owner behavior, and EVT_13 overrides come from the Game Engine only.
- Workflow states are not persisted (GR-SAVE-002).
- Auction bidding UI is deferred to Step 11.
