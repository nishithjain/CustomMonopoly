# V1 Release Status

**Version:** 1.0.0 (versionCode 1)  
**Display name:** Banking QR  
**Build date:** 2026-08-20  
**Overall status:** **BLOCKED** — pending physical validation

## Automated validation

| Check | Status |
|-------|--------|
| Game Core tests (114) | PASS |
| App unit tests (40) | PASS |
| assembleDebug | PASS |
| assembleRelease | PASS (unsigned) |
| Foundation validator | PASS |
| Game Engine validator | PASS |
| QR Scanner validator | PASS |
| Persistence validator | PASS |
| Player Setup validator | PASS |
| Gameplay validator | PASS |
| Advanced Banking validator | PASS |
| V1 release validator | PASS (37/37) |
| Card registry (49) | PASS |
| game-core Android-free | PASS |

## Physical QR (user)

| Category | Tested PASS | Total |
|----------|------------:|------:|
| User | 0 | 4 |
| Property | 0 | 22 |
| Event | 0 | 23 |
| **Total** | **0** | **49** |

_Prior Steps 9–10 covered partial flows; full 49-card checklist not complete._

## Physical gameplay (user)

| Area | Status |
|------|--------|
| Step 11 banking workflows | NOT RUN |
| 23 Events physical | NOT RUN |
| 2-player full game | NOT RUN |
| 4-player game | NOT RUN |
| Original unit comparison | NOT RUN |

## Release artifacts

| Artifact | Path | Status |
|----------|------|--------|
| Debug APK | `android-app/app/build/outputs/apk/debug/app-debug.apk` | Built |
| Release APK | `app-release-unsigned.apk` | Built (SIGNING_REQUIRED) |
| Release signing | — | No production keystore in repo |

## Configuration

- `minifyEnabled = false` (R8 off — safer for V1)
- Portrait orientation locked
- INTERNET permission stripped from merged manifest
- Debug screens gated with `BuildConfig.DEBUG`

## Known issues

None recorded in automated testing. See [FULL_GAME_TEST_LOG.md](FULL_GAME_TEST_LOG.md) after physical runs.

## Readiness

| Criterion | Met? |
|-----------|------|
| Automated tests PASS | Yes |
| Validators PASS | Yes |
| No BLOCKER/HIGH bugs | Yes (automated scope) |
| Physical 49 QR cards | **No** |
| Full game physical | **No** |
| Release APK | Unsigned |

**Cannot claim READY_FOR_PERSONAL_USE until user completes physical checklists.**

When physical testing passes with no BLOCKER/HIGH issues:

→ Update status to **READY_FOR_PERSONAL_USE** or **READY_WITH_KNOWN_LIMITATIONS**

## Important files

- [V1_FEATURE_FREEZE.md](V1_FEATURE_FREEZE.md)
- [V1_TEST_REPORT.md](V1_TEST_REPORT.md)
- [V1_RELEASE_NOTES.md](V1_RELEASE_NOTES.md)
- [V1_KNOWN_LIMITATIONS.md](V1_KNOWN_LIMITATIONS.md)
- [USER_GUIDE.md](USER_GUIDE.md)
- [ORIGINAL_UNIT_COMPATIBILITY_TEST.md](ORIGINAL_UNIT_COMPATIBILITY_TEST.md)
- [PHYSICAL_QR_TEST_CHECKLIST.md](PHYSICAL_QR_TEST_CHECKLIST.md)
- [PHYSICAL_GAMEPLAY_TEST_CHECKLIST.md](PHYSICAL_GAMEPLAY_TEST_CHECKLIST.md)
- [FULL_GAME_TEST_LOG.md](FULL_GAME_TEST_LOG.md)
- [data/v1_release_validation.txt](../data/v1_release_validation.txt)
- [tools/validate_v1_release.py](../tools/validate_v1_release.py)

No Phase 2 functionality has been started.
