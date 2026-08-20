# Game Completion Workflow

Bankruptcy sets `GameStatus.FINISHED`. `WinnerCalculator` determines winner by cash + property purchase prices; tie-break by highest property purchase price.

## UI

`GameOverScreen` shows winner, wealth breakdown, and rankings. Normal gameplay (scan card, bank actions) is disabled when finished.

## Persistence

Finished games remain saved. **RETURN HOME** or **NEW GAME** from game over screen.
