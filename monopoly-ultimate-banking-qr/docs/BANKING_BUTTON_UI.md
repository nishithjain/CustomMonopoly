# Banking Button UI

The Android app uses contextual controls inspired by the original Ultimate Banking hardware unit. Android remains clearer and more explicit than the physical device — there is no permanent fake hardware panel on every screen.

## Physical Unit Reference

```text
LEFT:   ✓  Confirm / Start
MIDDLE: M  Mainly auction bid / context-specific banking action
RIGHT:  ✕  Cancel / Exit
```

## Android Semantics

| Symbol | Meaning | Typical labels |
|--------|---------|----------------|
| ✓ | Confirm / start / accept a valid action | `✓ START GAME`, `✓ BUY`, `✓ COLLECT M200`, `✓ APPLY EVENT`, `✓ UNDO`, `✓ DONE` |
| M | Context-specific middle action (primarily auction bidding) | `M BID +M20` |
| ✕ | Cancel / back out of an uncommitted workflow | `✕ CANCEL`, `✕ BACK`, `✕ CANCEL GAME` |

Symbols provide familiarity. Trailing text communicates the actual Android action.

## Reusable Component

`BankingActionBar` in `com.boardbanker.app.ui.components` renders only the actions supplied:

- `confirmLabel` / `onConfirm` — ✓ actions
- `middleLabel` / `onMiddle` — optional M action (omitted when null)
- `cancelLabel` / `onCancel` — ✕ actions
- `extraActions` — explicit non-hardware buttons such as `AUCTION` or `RAISE RENT LEVEL`

The component does **not** appear on every screen. It is shown only when a workflow has meaningful confirm, middle, and/or cancel actions.

## Contextual Examples

### Property purchase decision

```text
✓ BUY
AUCTION
✕ CANCEL
```

`AUCTION` stays a separate explicit button — it is not replaced by `M`.

### Auction (inside auction screen)

```text
M BID +M20
✕ CANCEL        (only before first bid)
```

### GO confirmation

```text
✓ COLLECT M200
✕ CANCEL
```

### Completed transaction

```text
✓ DONE
```

No unused `M` or `✕` buttons.

## Rules Preserved

- Cancel is **not** shown where rules do not allow escape (for example mandatory debt resolution).
- Cancel, Back, and Done are legitimate operations — they do **not** play `Error.mp3`.
- Invalid operations still flow through existing ViewModel / Game Engine validation and centralized error audio.
- Double-submit protection remains via existing `commandInFlight` / command locks — disabled buttons cannot bypass validation.

## Accessibility

Every banking action button includes visible text (symbol + label) and a semantic content description derived from the action text.

## Implementation Note

`BankingActionBar` triggers existing ViewModel callbacks only. It does not execute `GameCommand`, mutate `GameSession`, or play audio directly.
