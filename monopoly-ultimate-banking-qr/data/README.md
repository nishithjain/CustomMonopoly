# Data Directory

This directory will later contain **master data** for the Monopoly Ultimate Banking QR Scanner Replacement application.

Planned content includes:

```text
players
properties
events
qr mappings
```

## Step 1 Status

**No real game data should be created during Step 1.**

Exact property prices, rent structures, Event effects, player card mappings, and QR values must be confirmed from the original physical game or provided project data before populating this directory.

## Future Format Decision

Later we will decide whether master data will initially be represented as JSON, CSV, Kotlin resources, or another format.

**That architecture decision has not been made yet.**

When data is added, each file should map cleanly to the conceptual models defined in [docs/GAME_STATE_MODEL.md](../docs/GAME_STATE_MODEL.md):

- `PlayerDefinition`
- `PropertyDefinition`
- `EventDefinition`
- `CardDefinition` (QR → card ID mappings)
