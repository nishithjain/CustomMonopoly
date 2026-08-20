# Rule Test Scenarios

Implementation-independent Given/When/Then scenarios. Updated with confirmed Ultimate Banking rules.

---

## Setup

### TS-SETUP-001 — Register Players with M1500

```text
GIVEN
no active GameSession

WHEN
USR_01 and USR_02 are scanned during New Game setup
and setup is confirmed

THEN
GameSession.status = ACTIVE
and Player 1 balance = 1500
and Player 2 balance = 1500
and all 22 PropertyState records have ownerPlayerId = null
and one GAME_START transaction is recorded
```

**Status:** RESOLVED

### TS-SETUP-002 — Two-Player Game Valid

```text
GIVEN
setup in progress

WHEN
exactly 2 players are registered and setup confirmed

THEN
game starts successfully
```

**Status:** RESOLVED

### TS-SETUP-003 — Reject Duplicate Player

**Status:** RESOLVED (unchanged)

---

## Property Purchase

### TS-PROPERTY-001 — Buy Unowned Property

```text
GIVEN
PRP_01 is unowned
and Player 1 balance = 1500

WHEN
Player 1 purchases PRP_01

THEN
Player 1 balance = 1440
and PRP_01 owner = Player 1
and PRP_01 currentRentLevel = 1
and one PROPERTY_PURCHASE transaction is recorded
```

**Status:** RESOLVED

---

## Rent Payment

### TS-RENT-001 — Visitor Pays Then Level Increases

```text
GIVEN
PRP_01 belongs to Player 1 (not in Jail)
and currentRentLevel = 3
and Player 2 lands there

WHEN
rent is processed

THEN
rent charged = 220 (Level 3)
and Player 2 debited 220
and Player 1 credited 220
and PRP_01 currentRentLevel = 4
and one RENT_PAYMENT transaction is recorded
```

**Status:** RESOLVED

### TS-RENT-002 — Owner Lands, No Rent, Level Increases

```text
GIVEN
PRP_01 belongs to Player 1
and currentRentLevel = 3

WHEN
Player 1 lands on PRP_01

THEN
no RENT_PAYMENT transaction
and PRP_01 currentRentLevel = 4
```

**Status:** RESOLVED

### TS-RENT-003 — Jailed Owner, No Rent Collected

```text
GIVEN
PRP_01 belongs to Player 1
and Player 1 jailStatus = true
and currentRentLevel = 3

WHEN
Player 2 lands on PRP_01

THEN
no rent collected
and PRP_01 currentRentLevel remains 3
```

**Status:** RESOLVED

### TS-RENT-004 — Maximum Level Clamp

```text
GIVEN
PRP_01 currentRentLevel = 5

WHEN
owner lands or visitor pays rent

THEN
after level increase attempt, currentRentLevel = 5
```

**Status:** RESOLVED

---

## Colour Set Bonus

### TS-COLORSET-001 — Single Owner Completion Bonus

```text
GIVEN
Player 1 owns PRP_01 and PRP_02 (BROWN)
and PRP_02 currentRentLevel = 2

WHEN
Player 1 purchases PRP_02 (completing BROWN set for Player 1)

THEN
PRP_01 currentRentLevel = 4 (2 + 2, clamped)
and PRP_02 currentRentLevel = 4
and ColorGroupState.BROWN.completionBonusApplied = true
```

**Status:** RESOLVED

### TS-COLORSET-002 — Multi-Owner Completion Bonus

```text
GIVEN
Player 1 owns PRP_06, PRP_07
and Player 2 owns PRP_08 (completing PINK set)
and all at rent level 2

WHEN
Player 2 purchases PRP_08

THEN
PRP_06, PRP_07, PRP_08 each become rent level 3 (2 + 1)
and completion bonus applied once only
```

**Status:** RESOLVED

---

## GO

### TS-GO-001 — Normal Dice Movement Passes GO

```text
GIVEN
Player 1 passes GO via normal dice roll

WHEN
GO salary is processed

THEN
Player 1 credited M200
```

**Status:** RESOLVED

### TS-GO-002 — Location Movement No GO

```text
GIVEN
Player 1 uses Location and moves past GO

WHEN
movement completes

THEN
no GO salary credited
```

**Status:** RESOLVED

---

## Jail

### TS-JAIL-001 — Pay M100 to Exit

```text
GIVEN
Player 1 jailStatus = true
and balance >= 100

WHEN
Player 1 pays Jail fee at turn start

THEN
Player 1 debited 100
and jailStatus = false
```

**Status:** RESOLVED

### TS-JAIL-002 — Jailed Player Cannot Bid

```text
GIVEN
auction in progress
and Player 2 jailStatus = true

WHEN
Player 2 attempts to bid

THEN
bid rejected
```

**Status:** RESOLVED

---

## Location

### TS-LOCATION-001 — Pay M100 Move to Unowned Property

```text
GIVEN
Player 1 chooses Location
and selects unowned PRP_10

WHEN
Location fee processed

THEN
Player 1 debited 100 to Bank
and no GO salary
and purchase flow available for PRP_10
```

**Status:** RESOLVED

### TS-LOCATION-002 — Pay M100 Move to Own Property

```text
GIVEN
Player 1 owns PRP_05 at rent level 2

WHEN
Player 1 uses Location to move to PRP_05

THEN
Player 1 debited 100
and PRP_05 currentRentLevel = 3
and no GO salary
```

**Status:** RESOLVED

---

## Auction

### TS-AUCTION-001 — Fixed M20 Increments

```text
GIVEN
PRP_03 unowned
and auction started

WHEN
bids proceed 0 → 20 → 40 → 60

THEN
only M20 increments accepted
and last bidder before timer wins at M60
and winner currentRentLevel = 1
```

**Status:** RESOLVED

### TS-AUCTION-002 — No Bids Cancels

```text
GIVEN
auction started for PRP_03

WHEN
all players decline to bid

THEN
auction cancelled
and PRP_03 remains unowned
```

**Status:** RESOLVED

---

## Debt

### TS-DEBT-001 — Transfer Property to Creditor

```text
GIVEN
Player A owes Player B M200
and Player A owns PRP_11 (purchasePrice 200) at rent level 4

WHEN
Player A selects PRP_11 for debt settlement

THEN
PRP_11 owner = Player B
and PRP_11 currentRentLevel = 4 (unchanged)
```

**Status:** RESOLVED

### TS-DEBT-002 — Return Property to Bank

```text
GIVEN
Player A owes Bank M150
and selects PRP_11 (purchasePrice 200)

WHEN
debt settled

THEN
PRP_11 owner = null
and M50 change returned to Player A
```

**Status:** RESOLVED

---

## Bankruptcy and End Game

### TS-BANKRUPTCY-001 — Insufficient Assets

```text
GIVEN
Player A owes M500
and cash + property values at purchasePrice < 500

WHEN
debt resolution attempted

THEN
Player A bankrupt
and gameStatus = FINISHED
```

**Status:** RESOLVED

### TS-ENDGAME-001 — Winner by Wealth

```text
GIVEN
Player A bankrupts
and wealth calculated for all players

WHEN
winner determined

THEN
winner = player with highest (cash + sum purchasePrice of owned properties)
and tie broken by highest single property purchasePrice
```

**Status:** RESOLVED

---

## Events — RESOLVED

### TS-EVT-008 — House Party

```text
GIVEN
Player 1 owns PRP_05 (level 2), PRP_04 (level 3), PRP_06 unowned

WHEN
EVT_08 applied with PRP_05 scanned

THEN
PRP_05 currentRentLevel = 3
and PRP_04 currentRentLevel = 2
and PRP_06 unchanged (unowned)
```

**Status:** RESOLVED

### TS-EVT-013 — On The Run

```text
GIVEN
EVT_13 active, remainingUses = 2
and PRP_20 currentRentLevel = 4

WHEN
Player 2 lands and pays rent

THEN
rent charged = Level 1 amount (not Level 4)
and PRP_20 currentRentLevel = 5 after normal post-rent increase
and remainingUses = 1
```

**Status:** RESOLVED

### TS-EVT-013-OWNER — Owner Landing Does Not Consume

```text
GIVEN
EVT_13 active, remainingUses = 2

WHEN
owner lands on own property

THEN
remainingUses = 2 (unchanged)
```

**Status:** RESOLVED

### TS-EVT-021 — Total Gridlock

**Status:** RESOLVED (unchanged from prior spec)

### TS-EVT-015 — Board Side Decrease (EVT_15)

```text
GIVEN
PRP_03 on SIDE_1_GO_TO_JAIL
and Player 1 owns PRP_01 (level 3), PRP_05 (level 2)
and PRP_06 owned by Player 2 (SIDE_2)

WHEN
EVT_15 applied with PRP_03 scanned

THEN
PRP_01 currentRentLevel = 2
and PRP_05 currentRentLevel = 1
and PRP_06 unchanged
and unowned properties on SIDE_1 ignored
```

**Status:** RESOLVED

### TS-EVT-022 — Board Side Increase (EVT_22)

```text
GIVEN
PRP_18 on SIDE_4_GO_TO_JAIL_TO_GO
and Player 1 owns PRP_20 (level 4), PRP_22 (level 5)
and PRP_19 unowned

WHEN
EVT_22 applied with PRP_18 scanned

THEN
PRP_20 currentRentLevel = 5
and PRP_22 currentRentLevel = 5
and PRP_19 unchanged
```

**Status:** RESOLVED

---

## Undo

### TS-UNDO-001 — Undo Last Rent Payment

```text
GIVEN
last transaction was RENT_PAYMENT

WHEN
Undo confirmed

THEN
balances and rent level restored to stateBefore
and one UNDO transaction recorded
```

**Status:** RESOLVED

### TS-UNDO-002 — Event Not Undoable

```text
GIVEN
last transaction was EVENT_APPLIED

WHEN
Undo attempted

THEN
Undo rejected
```

**Status:** RESOLVED

### TS-UNDO-003 — Debt Blocks Undo

```text
GIVEN
player entered debt resolution

WHEN
Undo attempted

THEN
Undo rejected
```

**Status:** RESOLVED

---

## Atomicity and Duplicate Scan

### TS-BANK-002 — Failed Transfer No Partial State

**Status:** RESOLVED (unchanged)

### TS-SCAN-001 — Duplicate QR During Workflow

**Status:** RESOLVED (unchanged)

---

## Save

### TS-SAVE-001 — Persist Game State

**Status:** RESOLVED (unchanged)

### TS-SAVE-002 — Discard In-Progress Workflow on Restart

```text
GIVEN
active GameSession with committed state
and an in-progress scan workflow (e.g. PROPERTY_IDENTIFIED, WAITING_FOR_PLAYER)

WHEN
the app is terminated and restarted

THEN
the last committed GameSession state is restored
and transient scan workflow state is discarded
and scanner/workflow returns to READY
and no uncommitted balance/ownership/rent changes exist
```

**Status:** RESOLVED
