# Event Workflow

Step 10 connects Event QR scans to the Game Engine using data-driven workflow planning from `EventEngineRule`.

## Flow

```text
SCAN CARD
    ↓
Event QR resolved
    ↓
EventWorkflowPlanner.plan(eventId, rule)
    ↓
Collect acting player / target player / property target(s)
    ↓
Optional confirmation
    ↓
ApplyEvent (or EventPropertyChoice for MOVE_THEN events)
    ↓
Game Engine
    ↓
Persist committed session
    ↓
Result UI
```

## Patterns

| Pattern | Events |
|---------|--------|
| EVENT_ONLY | EVT_13, EVT_21 |
| ACTING_PLAYER_ONLY | EVT_07 |
| PLAYER_TARGET | EVT_14 |
| TWO_PLAYER_TARGET | EVT_11, EVT_23 |
| PROPERTY_TARGET | Most property-affecting events |
| TWO_PLAYER_TWO_PROPERTY | EVT_06, EVT_09 |
| MOVE_THEN_PROPERTY_CHOICE | EVT_01, EVT_03, EVT_18 |

Printed Event text comes from validated `events.json` master data.
