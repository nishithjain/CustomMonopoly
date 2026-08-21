# Player Details

Active Game and Player Details both observe the same authoritative committed `GameSession` through `ActiveGameSessionManager.committedSession`.

## Opening Player Details

Tap any player row on **Active Game**. Navigation uses `playerId` (for example `USR_01`), not the custom player name.

Each row shows:

- Player icon and custom name
- Balance
- Property count and jail summary (`3 Properties • Active` or `IN JAIL`)

## Player Details header

- Custom player name (primary)
- Token/card identity (Car, Helicopter, etc.)
- Balance
- Property count
- Jail status (`No` or `IN JAIL`)

## Owned properties

Properties are resolved from `GameSession.propertyStates` where `ownerPlayerId == selectedPlayerId`, joined to `PropertyDefinition` for static data.

Sorted by board sequence (`PRP_01` … `PRP_22`).

Each row shows property name, color group, rent level, current rent, and purchase price.

Tap a property row for a read-only card-front preview. No purchase, rent, or landing workflow is triggered from this screen.

If the player owns no properties, the screen shows **NO PROPERTIES OWNED**.

## Player-specific bank actions

The **BANK ACTIONS** section on Player Details is contextual:

| Player state | Actions |
|--------------|---------|
| Not in Jail | COLLECT GO, LOCATION, GO TO JAIL |
| In Jail | GET OUT OF JAIL |

The selected player is preselected — no rescan is required for GO, Location confirm, Go To Jail, or Get Out of Jail entry.

Confirmation is still required before any state-changing command.

After a successful commit, balance and jail status update immediately on Player Details and on Active Game when you navigate back, because both screens observe the same committed session flow.

## Live updates

If ownership or balance changes while Player Details is open (for example through an Event or debt workflow elsewhere), returning to Player Details shows the latest committed session values.
