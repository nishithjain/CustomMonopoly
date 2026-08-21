# Bank Actions Workflow

Bank Actions are manual records of physical board outcomes. The app never rolls dice or moves tokens virtually.

All bank commands go through `GameCommand` → `GameEngine` → Room persistence → `committedSession` StateFlow.

## Hub actions (Advanced Banking)

| Action | Purpose |
|--------|---------|
| **COLLECT GO** | Record normal pass/land GO salary (`PayGoSalary`, M200) |
| **LOCATION** | Pay M100 (`PayLocationFee`), scan destination Property, normal landing workflow |
| **GO TO JAIL** | Record that physical rules sent a player to Jail (`SendPlayerToJail`) |
| **GET OUT OF JAIL** | Manage release of a player already in Jail |
| **UNDO LAST ACTION** | Global undo (also available here) |
| **GAME STATUS** | Read-only balances |
| **RECENT BANKING** | Transaction history |

## GO TO JAIL vs GET OUT OF JAIL

These are separate actions — the old ambiguous **JAIL** label was removed.

### GO TO JAIL

Use when the physical game sends a player to Jail (for example landing on Go To Jail or three doubles).

Flow: scan player → confirm → `SendPlayerToJail`.

On success: `jailStatus = true`, `GoToJail.mp3` once.

If the player is already in Jail: show **PLAYER ALREADY IN JAIL** and play `Error.mp3` (invalid user operation).

### GET OUT OF JAIL

Use only when `jailStatus = IN_JAIL`.

Options:

- Pay M100 to leave (`PayJailFee`)
- Release after doubles (`ReleasePlayerFromJailByDoubles`)
- Record failed doubles (guidance only)

If the scanned player is not in Jail: show **PLAYER IS NOT IN JAIL** — no session mutation.

## Location

See [LOCATION_WORKFLOW.md](LOCATION_WORKFLOW.md) for full detail.

1. Select player (or preselect from Player Details)
2. Confirm **PAY M100** — engine `PayLocationFee` with fee-only sentinel (M100 once)
3. Return to Active Game — transient **LOCATION_WAITING_FOR_DESTINATION_PROPERTY**
4. Scan destination Property card
5. Normal Property landing workflow (`ProcessPropertyLanding`, BUY, AUCTION, rent, debt, etc.)

Landing player is preselected — no Player QR re-scan after Location.

No GO salary during Location movement. Cancelling BUY/AUCTION does not refund M100.

On success after fee: balance updates immediately on Active Game; audio uses money-lost mapping.

## Collect GO

1. Scan player (or preselect from Player Details)
2. Confirm **COLLECT M200**
3. Engine executes `PayGoSalary`

On success: balance updates immediately; `Go.mp3` once.

## Active Game refresh

Previously, Active Game snapshot player balances only when `GameViewModel` loaded. Returning from Bank Actions left stale balances even though Room and the in-memory committed session were correct.

Fix: `CommittedGameSessionStore` publishes `committedSession` after every successful save/load/delete. `GameViewModel` and `PlayerDetailsViewModel` subscribe and refresh dashboard state on every emission.

No polling. No manual balance arithmetic in Compose.

## Player Details bank actions

Same commands as Advanced Banking, with the tapped player preselected. See [PLAYER_DETAILS.md](PLAYER_DETAILS.md).
