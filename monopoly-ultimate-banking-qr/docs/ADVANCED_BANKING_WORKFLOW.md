# Advanced Banking Workflow

Android banking actions route through `AdvancedBankingViewModel` → `BankingCommandExecutor` → `ActiveGameSessionManager` → Game Engine → Room persistence.

## Actions

- **GO** — scan player, confirm, `PayGoSalary`
- **Location** — scan player, scan destination property, `PayLocationFee`
- **Jail** — scan jailed player; pay fee, doubles release, or physical failed-doubles guidance
- **Undo** — `UndoLastAction` when engine eligibility allows
- **Game Status / Recent Banking** — read-only views from committed `GameSession`

## Physical board

Token movement and dice remain physical. The app does not infer GO collection or jail doubles.

## GR-SAVE-002

Only committed `GameSession` is persisted. Banking scan steps and auction countdown timer are transient UI state.
