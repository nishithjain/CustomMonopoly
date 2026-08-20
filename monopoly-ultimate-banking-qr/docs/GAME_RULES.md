# Game Rules

Master rule catalogue for the Monopoly Ultimate Banking QR Scanner Replacement.

Status values: `RESOLVED`, `NEEDS_CONFIRMATION`, `NOT_APPLICABLE`, `BOARD_LAYOUT_DERIVED`.

**Source:** Confirmed Ultimate Banking manual rules (Step 4 update).

---

## Setup Rules

### GR-SETUP-001 — New Game Workflow

| Field | Value |
| ----- | ----- |
| **Status** | RESOLVED |
| **Workflow** | NEW GAME → Scan Player QR → Resolve USR_xx → Reject duplicate → Register Player → Repeat → Validate 2–4 players → Initialize M1500 balances → Initialize properties unowned at rent level 1 → Initialize ColorGroupState → Start GameSession |

### GR-SETUP-002 — Minimum Players

| Field | Value |
| ----- | ----- |
| **Status** | RESOLVED |
| **Calculation** | `minimumPlayers = 2` |

### GR-SETUP-003 — Maximum Players

| Field | Value |
| ----- | ----- |
| **Status** | RESOLVED |
| **Calculation** | `maximumPlayers = 4` |

### GR-SETUP-004 — Starting Balance

| Field | Value |
| ----- | ----- |
| **Status** | RESOLVED |
| **Calculation** | `startingBalance = 1500` |
| **State Changes** | `PlayerState.balance = 1500` per registered player |
| **Source / Basis** | Original banking unit credits M1500 per Bank card at game start |

### GR-SETUP-005 — Duplicate Player Card

| Field | Value |
| ----- | ----- |
| **Status** | RESOLVED |
| **Validation** | `duplicatePlayerCardAllowed = false` |

### GR-SETUP-006 — Initial Property Ownership

| Field | Value |
| ----- | ----- |
| **Status** | RESOLVED |
| **Calculation** | All properties unowned (`ownerPlayerId = null`) |

### GR-SETUP-007 — Initial Rent Level

| Field | Value |
| ----- | ----- |
| **Status** | RESOLVED |
| **Calculation** | `currentRentLevel = 1` for all properties at start and after purchase/auction win |

---

## Player Rules

### GR-PLAYER-001 — Balance Authority

| Field | Value |
| ----- | ----- |
| **Status** | RESOLVED |
| **Rule** | Only Game Engine may mutate `player.balance` |

### GR-PLAYER-002 — Check Balance

| Field | Value |
| ----- | ----- |
| **Status** | RESOLVED |

### GR-PLAYER-003 — Player Active / Bankrupt Status

| Field | Value |
| ----- | ----- |
| **Status** | RESOLVED |
| **Rule** | Bankruptcy sets `bankrupt = true`; game ends immediately |

---

## Banking Rules

### GR-BANK-001 through GR-BANK-005

| Field | Value |
| ----- | ----- |
| **Status** | RESOLVED |
| **Operations** | CREDIT_PLAYER, DEBIT_PLAYER, TRANSFER_PLAYER_TO_PLAYER, TRANSFER_PLAYER_TO_BANK, TRANSFER_BANK_TO_PLAYER |
| **Rule** | Amount positive; atomic; validate before mutate |

---

## Property Rules

### GR-PROPERTY-001 — Property Purchase Flow

| Field | Value |
| ----- | ----- |
| **Status** | RESOLVED |
| **Trigger** | Land on unowned property (normal or Location movement) |
| **Validation** | Unowned; buyer active; `balance >= purchasePrice` or debt flow |
| **State Changes** | Debit buyer; assign owner; `currentRentLevel = 1`; check colour-set completion bonus |
| **Transaction Type** | PROPERTY_PURCHASE |

### GR-PROPERTY-002 — Runtime Property State

| Field | Value |
| ----- | ----- |
| **Status** | RESOLVED |
| **Fields** | `propertyId`, `ownerPlayerId`, `currentRentLevel` only |

### GR-PROPERTY-003 — Owner Lands on Own Property

| Field | Value |
| ----- | ----- |
| **Status** | RESOLVED |
| **Trigger** | Owner lands on owned property |
| **Calculation** | No rent; `currentRentLevel = min(currentRentLevel + 1, 5)` |
| **Transaction Type** | PROPERTY_RENT_LEVEL_CHANGE |
| **Notes** | If owner is in Jail, normal landing-based rent increases do not apply (see GR-JAIL) |

### GR-PROPERTY-004 — Property Swap (Event / Debt)

| Field | Value |
| ----- | ----- |
| **Status** | RESOLVED |
| **Contexts** | EVT_06, EVT_09 (Event swap); debt to another player |
| **State Changes** | Swap `ownerPlayerId` only; retain `currentRentLevel`; physical cards exchanged |

### GR-PROPERTY-005 — No Normal Player Trading

| Field | Value |
| ----- | ----- |
| **Status** | RESOLVED |
| **Rule** | No `TradeProperty`, `NegotiateTrade`, or `OfferCashForProperty` in V1 |
| **Ownership changes** | Event swap, debt settlement to player, auction win, purchase only |

### GR-PROPERTY-006 — Property Liquidation (Debt)

| Field | Value |
| ----- | ----- |
| **Status** | RESOLVED |
| **Valuation** | `purchasePrice` only (not rent level) |
| **Debt to player** | Transfer ownership; retain rent level; physical deed to creditor |
| **Debt to bank** | `owner = null`; house removed; repurchase starts at level 1 |
| **Overpayment** | Change returned to player balance |

---

## Colour Set Bonus

### GR-COLORSET-001 — Completion Bonus

| Field | Value |
| ----- | ----- |
| **Status** | RESOLVED |
| **Trigger** | Final unowned property of a colour group purchased |
| **One-time** | `ColorGroupState.completionBonusApplied` — never repeats on later ownership changes |
| **Single owner of complete set** | All properties in group: `rentLevel += 2`, clamp to 5 |
| **Multiple owners of complete set** | All properties in group: `rentLevel += 1`, clamp to 5 |
| **Transaction Type** | PROPERTY_RENT_LEVEL_CHANGE |

---

## Rent Rules

### GR-RENT-001 — Visitor Rent Payment Flow

| Field | Value |
| ----- | ----- |
| **Status** | RESOLVED |
| **Trigger** | Non-owner lands on owned property |
| **Preconditions** | Property owner NOT in Jail |
| **Calculation** | Charge `rentLevels[currentRentLevel].amount`; then `currentRentLevel = min(currentRentLevel + 1, 5)` |
| **Transaction Type** | RENT_PAYMENT |
| **Notes** | If owner in Jail: no rent collected, no rent level increase, do not process normal rent |

### GR-RENT-002 — Rent Lookup Formula

| Field | Value |
| ----- | ----- |
| **Status** | RESOLVED |
| **Calculation** | `currentRent = rentLevels[currentRentLevel].amount` |

### GR-RENT-003 — Owner on Own Property (No Rent)

| Field | Value |
| ----- | ----- |
| **Status** | RESOLVED |
| **Rule** | No rent transfer; apply GR-PROPERTY-003 level increase |

### GR-RENT-004 — Maximum Rent Level

| Field | Value |
| ----- | ----- |
| **Status** | RESOLVED |
| **Calculation** | `maximumRentLevel = 5`; clamp all increases |

### GR-RENT-005 — Minimum Rent Level

| Field | Value |
| ----- | ----- |
| **Status** | RESOLVED |
| **Calculation** | `minimumRentLevel = 1`; clamp all decreases |

### GR-RENT-006 through GR-RENT-010 — Property / Colour Set Operations

| Field | Value |
| ----- | ----- |
| **Status** | RESOLVED |
| **Rule** | Event changes apply to owned properties only |

### GR-RENT-011 — Neighbour Rent Adjustment

| Field | Value |
| ----- | ----- |
| **Status** | RESOLVED |
| **Definition** | PRP_01–PRP_22 circular chain; see `data/board_relationships.json` |
| **Rule** | EVT_08/10: selected owned +1; owned neighbours -1; unowned neighbours unchanged |

### GR-RENT-012 — Board Side Rent Adjustment

| Field | Value |
| ----- | ----- |
| **Status** | RESOLVED |
| **Rule** | EVT_15: -1 owned properties on same physical side, min 1; EVT_22: +1, max 5 |
| **Source** | `data/board_relationships.json` boardSides (confirmed physical board layout) |

### GR-RENT-013 — RESET_PROPERTY_RENT_LEVEL

| Field | Value |
| ----- | ----- |
| **Status** | RESOLVED |
| **Rule** | Set to level 1 (EVT_04, 16, 20) |

---

## Event Rules

### GR-EVENT-001 — Event Processing Pipeline

| Field | Value |
| ----- | ----- |
| **Status** | RESOLVED |
| **Rule** | QR → Card Registry → EventDefinition → GameCommand → Game Engine |

### GR-EVENT-002 — Owned Properties Only

| Field | Value |
| ----- | ----- |
| **Status** | RESOLVED |
| **Rule** | Event rent changes apply only to owned properties; incomplete action → do nothing |

### GR-EVENT-003 — EVT_13 Temporary Effect

| Field | Value |
| ----- | ----- |
| **Status** | RESOLVED |
| **Model** | GLOBAL `FORCE_LEVEL_1_RENT`, `remainingUses = 2` |
| **Rule** | Charge Level 1 rent; do not reset stored level; owner landing does not consume |

### GR-EVENT-004 — EVT_21 Total Gridlock

| Field | Value |
| ----- | ----- |
| **Status** | RESOLVED |
| **Rule** | physicalActionRequired; no GO; Jail preserved |

---

## GO Rules

### GR-GO-001 — Normal GO Salary

| Field | Value |
| ----- | ----- |
| **Status** | RESOLVED |
| **Calculation** | `goSalary = 200` for passing or landing on GO during normal dice movement |
| **Transaction Type** | BANK_CREDIT |

### GR-GO-002 — GO Suppression

| Field | Value |
| ----- | ----- |
| **Status** | RESOLVED |
| **No GO for** | Event movement, Location movement, Go To Jail movement, three doubles to Jail, Total Gridlock |

---

## Jail Rules

### GR-JAIL-001 — Track jailStatus

| Field | Value |
| ----- | ----- |
| **Status** | RESOLVED |
| **Rule** | App MUST track `jailStatus` — affects rent, auction, banking |

### GR-JAIL-002 — Entering Jail

| Field | Value |
| ----- | ----- |
| **Status** | RESOLVED |
| **State Changes** | `jailStatus = true`; no GO salary; turn ends; physical token to Jail |

### GR-JAIL-003 — While in Jail

| Field | Value |
| ----- | ----- |
| **Status** | RESOLVED |
| **Cannot** | Collect rent; gain landing rent increases; bid in auctions |
| **Can still receive** | Event-card rent changes on owned properties |

### GR-JAIL-004 — Leaving Jail

| Field | Value |
| ----- | ----- |
| **Status** | RESOLVED |
| **Option 1** | Pay M100 to Bank at turn start; `jailStatus = false`; roll normally |
| **Option 2** | Roll doubles (up to 3 attempts); on doubles: exit and move; on 3rd fail: pay M100, exit, use that roll |
| **Insufficient M100** | Normal debt-resolution process |

### GR-JAIL-005 — Total Gridlock Preserves Jail

| Field | Value |
| ----- | ----- |
| **Status** | RESOLVED |

---

## Location Rules

### GR-LOCATION-001 — Location Space

| Field | Value |
| ----- | ----- |
| **Status** | RESOLVED |
| **Choices** | DO NOTHING or PAY M100 to Bank and move to property space |
| **No GO** | Location movement does not collect GO salary |
| **Unowned destination** | Purchase per property rules; rent level 1 |
| **Own property destination** | `currentRentLevel += 1`, clamp to 5 |
| **Display** | "Move your token to the selected property." |
| **Rule** | Physical movement manual; not virtual board |

---

## Auction Rules

### GR-AUCTION-001 — Auction Flow

| Field | Value |
| ----- | ----- |
| **Status** | RESOLVED |
| **Required** | Unowned property landing: BUY or AUCTION |
| **Bidders** | Any active player except those in Jail |
| **Increment** | M20 fixed (0 → 20 → 40 → 60 …); no arbitrary amounts |
| **Winner** | Last bid before timer ends; pays final amount; `currentRentLevel = 1` |
| **No bids** | Auction cancelled; property remains unowned |
| **Timer expires no bid** | Allow RESTART AUCTION or CANCEL if all decline |

---

## Debt Rules

### GR-DEBT-001 — Insufficient Funds

| Field | Value |
| ----- | ----- |
| **Status** | RESOLVED |
| **Flow** | Use all cash first; then properties at `purchasePrice` |
| **Debt to player** | Transfer ownership; retain rent level |
| **Debt to bank** | Return to unowned; reset on repurchase |
| **Overpayment** | Change returned |
| **Blocks undo** | Yes — debt resolution in progress |

---

## Bankruptcy Rules

### GR-BANKRUPTCY-001 — Bankruptcy

| Field | Value |
| ----- | ----- |
| **Status** | RESOLVED |
| **Trigger** | `cash + propertyPurchasePriceValue < amountOwed` |
| **Result** | `gameStatus = FINISHED`; no further turns |
| **Creditor shortfall** | Bank makes up unpaid amount for final wealth calculation |

---

## End Game Rules

### GR-ENDGAME-001 — Winner

| Field | Value |
| ----- | ----- |
| **Status** | RESOLVED |
| **End** | First player bankruptcy (not last-player-standing) |
| **Wealth** | `cash + sum(purchasePrice of owned properties)` — NOT rent level |
| **Tie breaker** | Highest-value owned property by purchasePrice |

---

## Undo Rules

### GR-UNDO-001 — Undo Last Action

| Field | Value |
| ----- | ----- |
| **Status** | RESOLVED |
| **Depth** | `undoDepth = 1` |
| **Eligible** | Rent payment, property purchase, GO salary, Jail payment, Location fee |
| **Ineligible** | Event-card transactions |
| **Blocked** | During debt resolution |

---

## Save Rules

### GR-SAVE-001 — Persist Game State

| Field | Value |
| ----- | ----- |
| **Status** | RESOLVED |
| **Persist** | GameSession, Players, balances, PropertyState, ColorGroupState, temporary effects, jailStatus, transaction history |

### GR-SAVE-002 — Restore In-Progress Workflow

| Field | Value |
| ----- | ----- |
| **Status** | RESOLVED |
| **Rule** | On app termination/restart during an in-progress scan workflow, do **not** restore the partial workflow |
| **Behaviour** | Restore only the last **committed** GameSession state; discard transient/uncommitted scan state; return scanner/workflow to `READY` |
| **Examples** | Property scanned → waiting for Player → app terminates → on restart: restore committed session, discard `PROPERTY_IDENTIFIED` / `WAITING_FOR_PLAYER`, return to `READY`. Event scanned → waiting for Property → app terminates → restart from `READY`, not mid-Event |
| **Rationale** | Follows atomic transaction rule — no balance/ownership change until commit |
| **Transaction Type** | N/A |
| **Notes** | Future test item: verify restart behaviour matches this rule |

---

## Related Documents

- [EVENT_ENGINE_RULES.md](EVENT_ENGINE_RULES.md)
- [TRANSACTION_RULES.md](TRANSACTION_RULES.md)
- [GAME_ENGINE_DESIGN.md](GAME_ENGINE_DESIGN.md)
- [RULE_GAPS.md](RULE_GAPS.md)
- [RULE_TEST_SCENARIOS.md](RULE_TEST_SCENARIOS.md)
- [data/game_rules.json](../data/game_rules.json)
