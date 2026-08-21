# Location Workflow

Location lets a player pay **M100** to move physically to any Property, then resolve landing through the **normal Property workflow**.

## Flow

```
BANK ACTIONS (or Player Details)
    ↓
LOCATION
    ↓
Select / preselect Player
    ↓
Confirm PAY M100
    ↓
Game Engine PayLocationFee (fee-only sentinel property id)
    ↓
Persist committed GameSession
    ↓
Balance updates immediately on Active Game
    ↓
Transient state: LOCATION_WAITING_FOR_DESTINATION_PROPERTY
    ↓
Physical move instruction (no GO salary)
    ↓
SCAN DESTINATION PROPERTY
    ↓
GameplayWorkflowController.beginLocationDestinationProperty
    ↓
Existing Property landing workflow (ProcessPropertyLanding / Purchase / Auction)
```

## Rules

| Destination | Behaviour |
|-------------|-----------|
| Unowned | BUY / AUCTION UI; landing player is preselected (no Player re-scan) |
| Own property | `ProcessPropertyLanding` — rent level increases, no rent |
| Opponent property | `ProcessPropertyLanding` — rent transfer + level progression |
| Jailed owner | Engine rule: no rent, no level increase |
| EVT_13 active | Engine `FORCE_LEVEL_1_RENT` applies normally |
| Insufficient rent cash | Existing Debt Resolution workflow |

## Fee charged once

`PayLocationFee` commits when M100 is confirmed. Wrong Property / User / Event scans while waiting do **not** charge again.

Cancelling BUY/AUCTION after Location does **not** refund M100.

## No GO during Location

Location movement must not award GO salary. UI instructs players not to collect M200 if passing GO.

## GR-SAVE-002 (app restart)

Persisted: Location fee transaction and updated balance.

**Not** persisted: `LocationWorkflowHolder` / `LOCATION_WAITING_FOR_DESTINATION_PROPERTY`.

After restart, Active Game returns to **READY** with M100 already deducted. Players may continue physically and scan the destination through normal Property flow if needed. The app does **not** auto-refund M100.

## Architecture

- `LocationWorkflowHolder` — transient landing player after fee
- `LocationWorkflowConstants.FEE_ONLY_PROPERTY_ID` — sentinel for fee-only `PayLocationFee`
- `GameplayWorkflowController.beginLocationDestinationProperty` — handoff to normal Property workflow
- No duplicate rent/ownership logic in Android

## Root cause (prior bug)

Bank Actions scanned Property **before** fee confirmation and called `PayLocationFee(player, property)` in one step, then showed a banking result and returned to Hub. Destination Property was never handed to `GameplayWorkflowController`, so BUY/AUCTION/rent UI never ran.
