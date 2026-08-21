# Bugfix: Active Game State Refresh

## Observed issues

During physical-device gameplay:

1. **GO** — After COLLECT M200, balance did not update on Active Game until leaving/reopening the screen.
2. **Location** — After PAY M100, balance did not update immediately on Active Game.
3. **Jail fee** — Same stale balance/jail display after Get Out of Jail.

## Root cause

Money **was committed correctly** before this fix.

Flow that worked:

```
Bank Action → GameEngine → BankingCommandExecutor → CommittedGameSessionStore → Room
```

Flow that failed for UI:

```
GameViewModel.loadSession() / updateFromSession()  (once on init)
GameScreen renders _uiState.players snapshot
User pops back from Bank Actions → same ViewModel instance → no refresh
```

`GameStatusScreen` read `sessionManager.currentSession()` at compose time, so it could show correct balances while Active Game showed stale values.

There was no reactive link from the committed session to `GameViewModel`. `RoomGameSessionRepository.observeLatestActive()` existed but was not wired to gameplay UI.

## Fix

1. `CommittedGameSessionStore` exposes `committedSession: StateFlow<GameSession?>`, updated on every persist/load/delete.
2. `ActiveGameSessionManager.committedSession` forwards that flow.
3. `GameViewModel` subscribes and calls `refreshDashboardFromSession()` on every emission.
4. `PlayerDetailsViewModel` uses the same pattern.

All committed commands (GO, Location, Jail, Auction, Debt, Undo, property gameplay, etc.) now share one update path.

## Regression tests

| Test | File |
|------|------|
| Session flow emits after GO | `ActiveGameSessionRefreshTest` |
| GameViewModel balance after external banking commit | `ActiveGameSessionRefreshTest` |
| Location fee refresh | `ActiveGameSessionRefreshTest` |
| Undo refresh | `ActiveGameSessionRefreshTest` |
| Player Details GO / Jail / properties | `PlayerDetailsViewModelTest` |
| SendPlayerToJail persistence | `AdvancedBankingWorkflowTest` |

## Related UI work (same release)

- Renamed ambiguous **JAIL** button to **GET OUT OF JAIL**
- Added **GO TO JAIL** bank action
- Added **Player Details** screen with owned properties and preselected bank actions

See [BANK_ACTIONS_WORKFLOW.md](BANK_ACTIONS_WORKFLOW.md) and [PLAYER_DETAILS.md](PLAYER_DETAILS.md).
