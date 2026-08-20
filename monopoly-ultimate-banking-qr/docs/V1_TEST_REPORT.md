# V1 Test Report

**Version:** 1.0.0  
**Report date:** 2026-08-20

## Automated regression (Cursor-completed)

| Suite | Result | Count |
|-------|--------|------:|
| `:game-core:test` | PASS | 114 / 114 |
| `:app:testDebugUnitTest` | PASS | 40 / 40 |
| `assembleDebug` | PASS | — |
| `assembleRelease` | PASS | unsigned APK |

## Structural validators (7 + source data)

| Validator | Result |
|-----------|--------|
| `validate_android_foundation.py` | PASS |
| `validate_game_engine.py` | PASS |
| `validate_qr_scanner.py` | PASS |
| `validate_persistence.py` | PASS |
| `validate_player_setup_integration.py` | PASS |
| `validate_gameplay_integration.py` | PASS |
| `validate_advanced_banking.py` | PASS |
| `validate_card_registry.py` | _run in Step 12_ |
| `validate_game_data.py` | _run in Step 12_ |
| `validate_rule_spec.py` | _run in Step 12_ |
| `validate_v1_release.py` | PASS (37/37) |

## Card registry (automated)

| Type | Expected | Automated |
|------|---------:|----------:|
| USER | 4 | 4 |
| PROPERTY | 22 | 22 |
| EVENT | 23 | 23 |
| **Total** | **49** | **49** |

CardResolver unit tests: **49/49** payloads (not equivalent to physical camera scans).

## Physical QR validation (user-required)

See [PHYSICAL_QR_TEST_CHECKLIST.md](PHYSICAL_QR_TEST_CHECKLIST.md).

| Category | Physically tested (PASS) | Target |
|----------|-------------------------:|-------:|
| User QR | _pending_ | 4 |
| Property QR | _pending_ | 22 |
| Event QR | _pending_ | 23 |
| **Total** | **_pending_** | **49** |

### Prior partial validation (Steps 9–10)

- USR_01 Car, USR_02 Helicopter — setup flow tested
- Representative Property/Event scans — basic gameplay flow tested
- Duplicate QR protection, unknown QR — PASS

**Step 12 requires full 49-card physical pass/fail recording.**

## Physical gameplay (user-required)

See [PHYSICAL_GAMEPLAY_TEST_CHECKLIST.md](PHYSICAL_GAMEPLAY_TEST_CHECKLIST.md) and [FULL_GAME_TEST_LOG.md](FULL_GAME_TEST_LOG.md).

| Workflow | Status |
|----------|--------|
| New Game / registration | Partial (Steps 9–10) |
| Property purchase / rent | Partial (Step 10) |
| All 23 Events | NOT RUN (full physical) |
| GO / Location / Jail / Auction / Debt / Undo | NOT RUN (Step 11 physical) |
| 2-player full game to winner | NOT RUN |
| 4-player full game | NOT RUN |
| Persistence stress / GR-SAVE-002 | NOT RUN |
| Offline / airplane mode | NOT RUN |
| Release APK smoke test | NOT RUN |

## Bug summary

| Severity | Count | Notes |
|----------|------:|-------|
| BLOCKER | 0 | — |
| HIGH | 0 | — |
| MEDIUM | 0 | — |
| LOW | 0 | — |

_Update after physical testing._

## Compatibility

See [ORIGINAL_UNIT_COMPATIBILITY_TEST.md](ORIGINAL_UNIT_COMPATIBILITY_TEST.md) — NOT RUN unless user performs side-by-side tests.
