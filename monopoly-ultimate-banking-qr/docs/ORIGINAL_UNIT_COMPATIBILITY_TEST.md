# Original Unit Compatibility Test

Side-by-side comparison with the physical Ultimate Banking banking unit where practical.

**Status:** NOT RUN — template for user testing.

| Scenario | Original unit | Android app | Match? | Notes |
|----------|---------------|-------------|--------|-------|
| Starting balance M1500 | | | | |
| Property purchase | | | | |
| Rent Level 1 | | | | |
| Rent progression | | | | |
| GO M200 | | | | |
| Location M100 | | | | |
| Jail fee M100 | | | | |
| Auction M20 increments | | | | |
| Sample Event (e.g. EVT_05) | | | | |

## Discrepancy process

1. Reproduce on both devices  
2. Check [GAME_RULES.md](GAME_RULES.md) and frozen Step 4 spec  
3. Check engine test in `:game-core`  
4. Classify: **bug** | **manual interpretation** | **hardware-specific**

## Known intentional differences

| Item | Notes |
|------|-------|
| Auction timer | App 30s; hardware timing not verified |
| Jail failed-doubles counter | Physical only in V1 |
| Workflow restart | App discards transient UI on kill (GR-SAVE-002) |

Do not change frozen rules without documented specification conflict.
