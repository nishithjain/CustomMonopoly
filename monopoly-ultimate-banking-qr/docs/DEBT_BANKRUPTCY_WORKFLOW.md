# Debt and Bankruptcy Workflow

When any command returns `DEBT_RESOLUTION_REQUIRED`, the app navigates to `DebtResolutionScreen`.

## Rules (engine authoritative)

- All cash applied first
- Remaining debt covered by properties at **purchase price**
- Debt to player transfers ownership; rent level unchanged
- Debt to bank returns property unowned; rent resets on repurchase
- Overpayment returns change to debtor

## Bankruptcy

`CheckBankruptcy` or debt resolution may end the game with `GameStatus.FINISHED` and `GameOutcome.BANKRUPTCY`.

## UI

Property selection via checklist and/or property QR scan. `ResolveDebt(propertyId)` commits settlement.
