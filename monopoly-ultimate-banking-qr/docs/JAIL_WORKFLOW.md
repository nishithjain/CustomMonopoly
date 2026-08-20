# Jail Workflow

`PlayerState.jailStatus` is shown on the game dashboard and game status screen.

## Entry

Events and engine commands may set jail via `SendPlayerToJail`. Physical-action instructions are shown from engine results.

## Manual banking

**BANK ACTIONS → JAIL** scans a player. If not in jail, no mutation occurs.

## Exit options

- **Pay M100** — `PayJailFee`
- **Doubles release** — physical confirmation, then `ReleasePlayerFromJailByDoubles`
- **Failed doubles** — physical tracking only; engine does not persist attempt count (see specification note in advanced banking validation)

## Jailed owner rule

No rent collection or landing rent increases while owner is in jail (engine enforced).
