# Event Engine Rules

Per-Event Game Engine specification for all 23 Event cards (`EVT_01`–`EVT_23`).

Machine-readable source: `data/event_engine_rules.json`

---

## Summary

| Status | Count | Event IDs |
| ------ | ----- | --------- |
| RESOLVED | 23 | EVT_01–EVT_23 |

**General rule (RESOLVED):** Event rent-level changes apply only to **owned** properties. Unowned properties are not modified. If an Event action cannot be completed, do nothing.

---

## Action Types

| actionType | Description |
| ---------- | ----------- |
| `MOVE_THEN_PROPERTY_CHOICE` | Physical move; then BUY, AUCTION, or RAISE_RENT_LEVEL +1 |
| `INCREASE_COLOR_SET_RENT_LEVEL` | +1 all owned properties in colour set |
| `DECREASE_COLOR_SET_RENT_LEVEL` | -1 all owned properties in colour set |
| `RESET_PROPERTY_RENT_LEVEL` | Set owned/any target to level 1 |
| `SET_PROPERTY_RENT_LEVEL` | Set to explicit level |
| `SWAP_PROPERTIES` | Exchange ownership between two players |
| `PAY_PER_OWNED_PROPERTY` | Debit M{amount} × owned property count |
| `CREDIT_BOTH_PLAYERS` | Credit M{amount} to two players |
| `TEMPORARY_RENT_CAP` | Global rent charge cap (EVT_13) |
| `SEND_PLAYER_TO_JAIL` | Send selected player to Jail |
| `ADJUST_SELECTED_AND_NEIGHBOUR_RENT_LEVELS` | +1 selected; -1 owned neighbours |
| `DECREASE_BOARD_SIDE_RENT_LEVEL` | -1 owned properties on board side |
| `INCREASE_BOARD_SIDE_RENT_LEVEL` | +1 owned properties on board side |
| `TOTAL_GRIDLOCK_V1` | Physical move to Free Parking; no GO; Jail preserved |

---

## EVT_08 — House Party (RESOLVED)

| Field | Value |
| ----- | ----- |
| actionType | ADJUST_SELECTED_AND_NEIGHBOUR_RENT_LEVELS |
| targetType | NEIGHBOURS_OF_SELECTED_PROPERTY |
| parameters | selected +1; neighbours -1; ownedOnly: true |

Neighbours = property immediately before/after in PRP_01–PRP_22 circular chain (`data/board_relationships.json`). Unowned neighbours unchanged.

---

## EVT_10 — It's A Boy! (RESOLVED)

Same engine pattern as EVT_08.

---

## EVT_13 — On The Run (RESOLVED)

| Field | Value |
| ----- | ----- |
| actionType | TEMPORARY_RENT_CAP |
| temporaryEffect | true |
| scope | GLOBAL |
| remainingUses | 2 |

**Behaviour:**

- Next two **completed rent payments** charge Level 1 rent regardless of stored `currentRentLevel`
- Stored `currentRentLevel` is **not** reset
- Owner landing on own property does **not** consume a use
- Completed rent debt resolution **does** consume a use
- Bankruptcy ends game; remaining uses irrelevant

---

## EVT_15 — Pong! What A Stinker (RESOLVED)

| Field | Value |
| ----- | ----- |
| actionType | DECREASE_BOARD_SIDE_RENT_LEVEL |
| engineStatus | RESOLVED |

Determine physical board side of scanned property. Apply -1 to every **owned** property on that side. Unowned properties ignored. Clamp minimum level 1. Side groups confirmed from supplied physical board layout (`data/board_relationships.json`).

---

## EVT_21 — Total Gridlock (RESOLVED)

| Field | Value |
| ----- | ----- |
| actionType | TOTAL_GRIDLOCK_V1 |
| physicalActionRequired | true |

All non-Jail players physically move to Free Parking. No GO. Jail players remain. `jailStatus` preserved.

---

## EVT_22 — What A Ride! (RESOLVED)

| Field | Value |
| ----- | ----- |
| actionType | INCREASE_BOARD_SIDE_RENT_LEVEL |
| engineStatus | RESOLVED |

Determine physical board side of scanned property. Apply +1 to every **owned** property on that side. Unowned properties ignored. Clamp maximum level 5. Side groups confirmed from supplied physical board layout.

---

## All Other Events

EVT_01, 03, 18 — `MOVE_THEN_PROPERTY_CHOICE` (RESOLVED)  
EVT_02, 12, 17 — `INCREASE_COLOR_SET_RENT_LEVEL` (RESOLVED)  
EVT_04, 16, 20 — `RESET_PROPERTY_RENT_LEVEL` (RESOLVED)  
EVT_05 — `SET_PROPERTY_RENT_LEVEL` level 5 (RESOLVED)  
EVT_06, 09 — `SWAP_PROPERTIES`, physicalActionRequired (RESOLVED)  
EVT_07 — `PAY_PER_OWNED_PROPERTY` M50 (RESOLVED)  
EVT_11, 23 — `CREDIT_BOTH_PLAYERS` M200 (RESOLVED)  
EVT_14 — `SEND_PLAYER_TO_JAIL` (RESOLVED)  
EVT_19 — `DECREASE_COLOR_SET_RENT_LEVEL` (RESOLVED)

---

## Relationship Targeting

```text
EVT_08/10 → NEIGHBOURS_OF(selectedProperty) from board_relationships.json
EVT_15/22 → BOARD_SIDE_OF(selectedProperty) from board_relationships.json
```

Do not hard-code Event/property combinations in scanner or UI code.
