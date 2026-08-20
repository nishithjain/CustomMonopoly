# Bugfix: User Card Scan Delivery

## Observed symptom

On a physical device, scanning a User card (for example Car / `USR_01`) sometimes played the identification sound (`Car.mp3`) but did not register in the active gameplay or setup workflow. The user had to scan the same card a second time.

Because audio is triggered only after ScanGate acceptance and CardResolver success, the failure was **after** card resolution and **before** reliable workflow consumption.

## Root cause

Scan results were returned to parent screens through **Navigation `savedStateHandle` + `LaunchedEffect` collectors**.

Typical sequence on success:

1. Scanner resolves card and plays User audio.
2. `ScannerScreen` writes `cardId` into the previous destination's `savedStateHandle`.
3. Scanner route pops immediately via `popBackStack()`.

During that navigation transition, the parent composable could temporarily leave composition or restart its collector. A one-shot value written to `savedStateHandle` could be missed before the parent workflow ViewModel consumed it.

Audio and workflow delivery were also sequenced in the same scan handler. Although audio was not intentionally blocking, the unreliable handoff meant the user could hear success while the workflow never received the card.

## Affected pipeline

```text
Camera frame
→ ML Kit
→ ScanGate
→ CardResolver
→ ScannerViewModel
→ (previously) savedStateHandle + popBackStack
→ LaunchedEffect collector on parent screen
→ workflow ViewModel
```

All scanner entry points were affected because they shared the same navigation-result pattern:

- Player registration
- Game scanner (GO, Location, Jail, Event target, etc.)
- Advanced banking (buyer, rent payer, player info)
- Auction bidder
- Debt resolution property scan

## Fix

Introduced a process-scoped **`ScanResultDeliverer`** with:

- `prepareConsumer(...)` before opening a scanner route
- `stageResolvedCard(...)` in `ScannerViewModel` **before** audio and **before** navigation pop
- `CollectScanResults(...)` on parent screens using a `SharedFlow(replay = 1)` collector
- exactly-once consumption via `tryConsume(scanAttemptId, consumer)`

Delivery priority in `ScannerViewModel`:

```text
1. Resolve card
2. Stage result in ScanResultDeliverer
3. Play audio (non-blocking, exceptions swallowed)
4. Update scanner UI / pop scanner route
5. Parent collector delivers to workflow ViewModel
```

`ScanAudioFeedback` remains a side effect only; audio failure cannot cancel scan delivery.

DEBUG-only trace logging (`ScanDeliveryTrace`) records stages such as `RESULT_STAGED`, `RESULT_EMITTED`, `RESULT_RECEIVED_BY_CALLER`, and `WORKFLOW_CONSUMED` with a per-scan `scanAttemptId`.

## Why sound could play while workflow missed the card

User audio is triggered immediately after a successful resolve + accepted card-type validation. The old path then attempted to pass the card through navigation state during `popBackStack()`. That handoff was not replay-safe and could race with Compose lifecycle/recomposition. Audio therefore proved resolution succeeded, not that the workflow had consumed the card.

## Regression tests added

- `ScanResultDelivererTest`
  - single delivery
  - delayed / slow caller / replay survival
  - consumer targeting
  - duplicate consume rejection
- `ScanDeliveryPipelineTest`
  - `userCardDoesNotRequireSecondPhysicalScan()`
  - repeated camera frames while ScanGate locked
  - navigation timing simulation
  - wrong-type scanner filter vs workflow delivery
  - audio failure does not block delivery

## Not changed

- QR payloads
- Card registry / game engine rules
- Scan debounce timings (no debounce workaround)
- GR-SAVE-002 persistence behavior
- User / Error sound file mapping
