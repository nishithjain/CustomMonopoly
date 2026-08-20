# Full Game Test Log

Record issues found during physical full-game testing.

| Issue ID | Game stage | Action | Expected | Actual | Reproducible? | Severity | Fix | Retest |
|----------|------------|--------|----------|--------|---------------|----------|-----|--------|
| _example_ | Property | Buy PRP_01 | M1440 balance | — | — | — | — | — |

## Severity guide

- **BLOCKER** — game cannot continue / wrong money / corrupt save
- **HIGH** — incorrect rule / wrong ownership / duplicate transaction
- **MEDIUM** — confusing workflow but recoverable
- **LOW** — cosmetic

## Test sessions

### Session 1 — 2-player full game (Car + Helicopter)

| Field | Value |
|-------|-------|
| Date | _pending_ |
| Result | NOT RUN |
| Notes | Target: NEW GAME → bankruptcy → winner |

### Session 2 — 4-player game (Car, Helicopter, Ship, Aeroplane)

| Field | Value |
|-------|-------|
| Date | _pending_ |
| Result | NOT RUN / PARTIAL |
| Notes | Preferred full game; document if partial scenario only |

## Reference scenario — Old Kent Road (PRP_01)

| Step | Expected |
|------|----------|
| Car buys Old Kent Road | Car M1440 |
| Helicopter lands | Pays M70; rent → Level 2 |
| Car lands on own property | No payment; rent → Level 3 |

Record actual results in issues above.
