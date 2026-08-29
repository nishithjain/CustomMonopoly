# Audio Feedback

Centralized application-layer audio for QR scans, invalid user actions, and committed gameplay outcomes.

## Architecture

Audio lives in the Android `:app` module only. `:game-core` remains pure Kotlin with no `android.media.*`, `SoundPool`, `MediaPlayer`, or `Context`.

```text
Game Engine success + persistence success
    ↓
GameplayOutcomeAudio (semantic cue from GameResult delta)
    ↓
GameAudioFeedback
    ↓
SoundPoolGameAudioFeedback (res/raw via GameSoundRegistry)
```

Primary types:

- `GameSound` / `GameSoundRegistry` — authoritative semantic → raw resource mapping (23 sounds)
- `GameAudioFeedback` — scan, error, and gameplay semantic playback methods
- `SoundPoolGameAudioFeedback` — SoundPool implementation
- `ScanAudioFeedback` — scan-result audio (never blocks workflow delivery)
- `InvalidUserActionAudio` — invalid user action error trigger
- `GameplayOutcomeAudio` — maps committed `GameResult` outcomes to one primary gameplay cue
- `ScanPromptAudio` — one-shot ScanCard prompt per scan session token
- `GameEndAudioCoordinator` — LostGame → Winner sequencing; no replay on resume

## One Primary Sound Policy

For a normal successful operation, play **at most one primary gameplay sound**. Examples:

- Rent payment → `RentTransfer.mp3` only (not also `RentLevelIncreased.mp3`)
- Color set completion purchase → `ColorSetComplete.mp3` only (not also `PropertyPurchased.mp3`)
- GO collection → `Go.mp3` only (not `KaChing.mp3`)

Audio reacts to committed engine outcomes — it never decides game rules.

## Scan / User Card Audio

Unchanged from prior integration:

| Trigger | Sound |
|---------|-------|
| Accepted USER scan | User identification (`Car.mp3`, etc.) once |
| Wrong USER at scanner filter | User sound → `Error.mp3` once each |
| Unknown QR | `Error.mp3` |
| Scan prompt (enter waiting-for-scan) | `ScanCard.mp3` once per scan session |

User scan audio remains independent of workflow delivery and never blocks it.

## Gameplay Audio Mapping

| Sound | Semantic trigger | Committed state required | Notes |
|-------|------------------|--------------------------|-------|
| `GameStarts.mp3` | `StartGame` success | Yes | After persistence |
| `PropertyPurchased.mp3` | Direct property purchase | Yes | Not auction win |
| `ColorSetComplete.mp3` | First color-set completion bonus | Yes | Beats `PropertyPurchased` for same transaction |
| `RentTransfer.mp3` | Normal rent payment | Yes | Beats rent-level increase on same landing |
| `RentLevelIncreased.mp3` | Owner lands on own property (+1) or rent-increase Events | Yes | Only if level actually rises |
| `RentLevelDecreased.mp3` | Rent-decrease / reset Events | Yes | Only if level actually falls |
| `Go.mp3` | GO salary collection | Yes | Banking GO workflow |
| `GoToJail.mp3` | Player newly sent to Jail | Yes | Valid outcome, not Error |
| `Jail.mp3` | Enter Jail banking workflow for jailed player | No | Once per workflow entry |
| `KaChing.mp3` | Bank→Player credits without dedicated sound | Yes | EVT_11, EVT_23, Jail fee payment |
| `SomeoneJustTookYourMoney.mp3` | Mandatory cash loss without dedicated sound | Yes | EVT_07, Location fee, debt cash payment |
| `AuctionBegins.mp3` | Auction enters RUNNING | Yes | On `StartAuction` commit |
| `AuctionEnding.mp3` | Auction timer expires | N/A (UI transition) | Once; no PropertyPurchased after auction win |
| `UndoLastAction.mp3` | Successful authorized `UndoLastAction` | Yes | After every registered player approves; not `Undo.mp3` |
| `LostGame.mp3` | Bankruptcy commits / game FINISHED | Yes | Not Error |
| `Winner.mp3` | Fresh game-over screen after bankruptcy path | Yes | Queued after LostGame; skipped on resume |
| `ScanCard.mp3` | Enter waiting-for-scan state | No | Token-gated |
| `Error.mp3` | Invalid user action | No | Not bankruptcy/debt |

### Auction completion policy

When an auction completes: `AuctionEnding.mp3` plays at timer expiry. After the winner's ownership commits, **no** additional `PropertyPurchased.mp3` is played (avoids back-to-back purchase audio).

### Jail fee policy

Successful M100 jail release uses **`KaChing.mp3`** as the primary banking transaction sound (not `GoToJail.mp3` or `Jail.mp3`).

### Game end sequence

```text
Bankruptcy committed → LostGame.mp3 → (2.5s) → Winner.mp3
```

Only after a fresh bankruptcy navigation mark. Resuming a finished game does not replay historical sounds.

## Event Audio (committed effect)

| Event | Primary sound (when effect occurs) |
|-------|-------------------------------------|
| EVT_01, EVT_03, EVT_18 | PropertyPurchased / RentLevelIncreased (outcome-dependent) |
| EVT_02, EVT_05, EVT_12, EVT_17, EVT_22 | RentLevelIncreased |
| EVT_04, EVT_15, EVT_19, EVT_20, EVT_16 | RentLevelDecreased |
| EVT_08, EVT_10 | RentLevelIncreased (mixed neighbor decreases suppressed) |
| EVT_07 | SomeoneJustTookYourMoney |
| EVT_11, EVT_23 | KaChing |
| EVT_14 | GoToJail |
| EVT_06, EVT_09, EVT_13, EVT_21 | No dedicated sound |

No rent-level sound when engine leaves level unchanged (e.g. Level 5 +1 stays Level 5).

## Invalid User Action

`Error.mp3` for invalid interactions only — not valid negative outcomes (bankruptcy, debt, jail entry, rent loss).

## Media Sync

Authoritative sources:

```text
Resources/Common/Sounds/UserCardSounds/  (4 files)
Resources/Common/Sounds/Other/           (19 files)
TOTAL: 23
```

```bash
python tools/sync_android_media.py
```

Android copies under `app/src/main/res/raw/` are generated — do not edit directly.

## Throttling / Replay Protection

- Scan prompts: `ScanPromptAudio` token per scan session
- Gameplay sounds: triggered only after engine + persistence success in ViewModels
- Game end: `GameEndAudioCoordinator` fresh-bankruptcy gate
- Resume game: no replay of historical transaction audio

## Validation

```bash
python tools/validate_audio_feedback.py
```

Report: `data/audio_feedback_validation.txt`
