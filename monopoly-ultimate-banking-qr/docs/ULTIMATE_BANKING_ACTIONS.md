# Ultimate Banking Actions

This document is the **primary behavioral reference** for the Monopoly Ultimate Banking QR Scanner Replacement application.

It catalogues every banking operation the Android app must eventually support. Each action mirrors functionality performed by the original physical Ultimate Banking electronic unit.

**Do not invent exact values or rules.** Where an exact Monopoly rule or value is unknown, this document uses:

```text
TO_BE_CONFIRMED_FROM_ORIGINAL_RULES
```

---

## Action Summary Table

| Action ID | Situation                   | Trigger               | First Input       | Additional Input                 | Validation                            | State Change                                      | User Result                  |
| --------- | --------------------------- | --------------------- | ----------------- | -------------------------------- | ------------------------------------- | ------------------------------------------------- | ---------------------------- |
| A001      | Start Game                  | User selects New Game | Player QR scans   | Additional Player QR scans       | Valid unique players                  | Create GameSession and PlayerState records        | Game started                 |
| A002      | Check Player Balance        | Player QR scanned     | Player QR         | None                             | Known active player                   | None                                              | Show balance                 |
| A003      | Buy Unowned Property        | Property interaction  | Property QR       | Player QR                        | Property unowned and purchase allowed | Debit player, set owner                           | Show purchase confirmation   |
| A004      | Pay Property Rent           | Property interaction  | Property QR       | Player QR                        | Property owned by another player      | Transfer rent payer → owner                       | Show rent transaction        |
| A005      | Owner Lands on Own Property | Property interaction  | Property QR       | Owner Player QR                  | Player matches owner                  | Update property state according to original rules | Show new property/rent state |
| A006      | Process Event               | Event card drawn      | Event QR          | Depends on Event                 | Valid Event and required targets      | Execute Event action                              | Show Event result            |
| A007      | Bank Credit                 | Banking action        | Player QR         | Amount/action context            | Valid player                          | Increase balance                                  | Show updated balance         |
| A008      | Bank Debit                  | Banking action        | Player QR         | Amount/action context            | Sufficient/valid funds handling       | Decrease balance                                  | Show updated balance         |
| A009      | Pass/Land on GO             | Board action          | Player QR         | None                             | Confirm original rule                 | Apply original banking rule                       | Show transaction             |
| A010      | Jail Payment                | Board action          | Player QR         | None                             | Confirm original rule                 | Debit/modify state                                | Show result                  |
| A011      | Location Space              | Board action          | Player QR         | Possibly Property/Location input | Confirm original rule                 | Apply appropriate transaction/state change        | Show result                  |
| A012      | Auction                     | Banking action        | Players/bids      | Winning player                   | Original auction rules                | Transfer money and ownership                      | Show result                  |
| A013      | Insufficient Funds          | Transaction attempt   | Player QR         | Property/assets if required      | Balance insufficient                  | Begin debt-resolution flow                        | Show options                 |
| A014      | Bankruptcy                  | Debt-resolution flow  | Player            | Creditor/context                 | Original rules satisfied              | Mark player bankrupt and resolve assets           | Show bankruptcy              |
| A015      | Undo Last Action            | User action           | None              | Confirmation                     | Reversible action exists              | Restore previous state                            | Show restored state          |
| A016      | Save Game                   | Automatic             | None              | None                             | Active game exists                    | Persist state                                     | Silent/indicator             |
| A017      | Resume Game                 | User action           | Saved GameSession | None                             | Valid saved game                      | Restore state                                     | Show game dashboard          |

---

## A001 — Start Game

### Preconditions

* No active GameSession exists, or user has explicitly chosen to abandon/save the current session
* At least one Player QR can be resolved to a known PlayerDefinition
* Minimum and maximum player counts per original rules: `TO_BE_CONFIRMED_FROM_ORIGINAL_RULES`

### Scan Sequence

```text
READY
  ↓
User selects New Game
  ↓
WAITING_FOR_PLAYER
  ↓
Scan Player QR (first player)
  ↓
Validate unique player
  ↓
Prompt: add another player or start
  ↓
[Repeat Player QR scans until player count satisfied or user confirms start]
  ↓
Create GameSession, PlayerState, PropertyState records
  ↓
Apply starting balances per original rules
  ↓
READY (game dashboard)
```

### State Changes

Conceptually:

```text
GameSession created (status = ACTIVE)
PlayerState created for each scanned player
  balance = TO_BE_CONFIRMED_FROM_ORIGINAL_RULES
  active = true
  bankrupt = false
PropertyState created for each property (unowned, initial rent level)
Transaction: optional game-start audit record
```

### Error Conditions

* Unknown QR
* Wrong QR type (Property or Event scanned when Player expected)
* Duplicate player scanned
* Player count below minimum or above maximum
* User cancels before confirming start

### Information Still Required

* Exact starting player balance
* Exact minimum and maximum player count
* Whether player scan order matters
* Whether all player cards must be scanned or only active participants

---

## A002 — Check Player Balance

### Preconditions

* Active GameSession exists
* Player QR resolves to a known PlayerDefinition
* Player has an active PlayerState in the current game

### Scan Sequence

```text
READY
  ↓
Scan Player QR
  ↓
Validate known active player
  ↓
Lookup PlayerState.balance
  ↓
SHOWING_RESULT
  ↓
READY
```

### State Changes

None. Read-only operation.

### Error Conditions

* Unknown QR
* Wrong QR type
* Player not part of current game
* Player marked bankrupt (display behavior: `TO_BE_CONFIRMED_FROM_ORIGINAL_RULES`)
* No active game

### Information Still Required

* Whether bankrupt players can check balance
* Whether original unit displays additional info (property count, etc.)

---

## A003 — Buy Unowned Property

### Preconditions

* Active game exists
* Property QR resolves to a known property
* Property currently has no owner (`ownerPlayerId` is null)
* Player QR resolves to an active player
* Exact original purchase rules must be confirmed
* Player has sufficient balance (or insufficient-funds flow per A013 applies)

### Scan Sequence

```text
READY
  ↓
Scan Property QR
  ↓
PROPERTY_IDENTIFIED
  ↓
Determine that property is unowned
  ↓
WAITING_FOR_PLAYER
  ↓
Scan Player QR
  ↓
Validate
  ↓
Confirmation if required
  ↓
Execute transaction
  ↓
READY
```

### State Changes

Conceptually:

```text
Player balance -= purchase price (from PropertyDefinition)
Property owner = player
Property state = initial owned state (rent level: TO_BE_CONFIRMED_FROM_ORIGINAL_RULES)
Transaction created (type = PURCHASE, reversible per original rules)
```

Do not put an exact purchase price here. Price comes from PropertyDefinition.

### Error Conditions

* Unknown QR
* Wrong QR type
* Property already owned
* Player not active
* Duplicate scan
* Insufficient balance → trigger A013
* Transaction cancelled by user

### Information Still Required

* Exact purchase price per property
* Initial rent level after purchase
* Whether confirmation step is required on original unit
* Scan order on original unit (Property first vs Player first): `TO_BE_CONFIRMED_FROM_ORIGINAL_RULES`
* Whether purchase is reversible (undo)

---

## A004 — Pay Property Rent

### Preconditions

* Active game exists
* Property QR resolves to a known property
* Property is owned by a player other than the scanning payer
* Player QR resolves to the rent-paying active player
* Rent amount determinable from PropertyDefinition + PropertyState

### Scan Sequence

```text
READY
  ↓
Scan Property QR
  ↓
PROPERTY_IDENTIFIED
  ↓
Determine owner ≠ scanning player
  ↓
WAITING_FOR_PLAYER
  ↓
Scan Player QR (payer)
  ↓
Validate payer is not owner
  ↓
Calculate rent from PropertyDefinition.rentStructure at PropertyState.currentRentLevel
  ↓
Confirmation if required
  ↓
Execute transaction
  ↓
READY
```

### State Changes

Conceptually:

```text
Payer balance -= rent amount
Owner balance += rent amount
Transaction created (type = RENT)
```

Rent amount: `TO_BE_CONFIRMED_FROM_ORIGINAL_RULES` (derived from property data).

### Error Conditions

* Unknown QR
* Wrong QR type
* Property unowned (may redirect to A003)
* Payer is the owner (may redirect to A005)
* Player not active
* Insufficient balance → trigger A013
* Duplicate scan
* Transaction cancelled

### Information Still Required

* Exact rent structure per property and rent level
* Whether rent differs for mortgaged/improved states (if applicable)
* Original unit scan order
* Confirmation requirement

---

## A005 — Owner Lands on Own Property

### Preconditions

* Active game exists
* Property QR resolves to a known property
* Property is owned by the scanning player
* Player QR resolves to the property owner
* Original rules define what happens when owner lands on own property (typically rent level increase)

### Scan Sequence

```text
READY
  ↓
Scan Property QR
  ↓
PROPERTY_IDENTIFIED
  ↓
Determine player matches owner
  ↓
WAITING_FOR_PLAYER
  ↓
Scan Owner Player QR
  ↓
Validate owner match
  ↓
Apply property improvement per original rules
  ↓
Confirmation if required
  ↓
Execute state change (may involve debit if improvement costs money)
  ↓
READY
```

### State Changes

Conceptually:

```text
PropertyState.currentRentLevel += 1 (if below maximumRentLevel)
Possible player debit if improvement has a cost
Transaction created if monetary change occurs
```

Exact behavior: `TO_BE_CONFIRMED_FROM_ORIGINAL_RULES`.

### Error Conditions

* Unknown QR
* Wrong QR type
* Property unowned
* Scanning player is not owner
* Property already at maximum rent level
* Insufficient balance if improvement costs money → A013
* Duplicate scan

### Information Still Required

* Exact rule when owner lands on own property
* Whether a monetary cost is involved
* Maximum rent level per property
* Whether owner can choose not to improve

---

## A006 — Process Event

### Preconditions

* Active game exists
* Event QR resolves to a known EventDefinition
* Event is valid for current game state
* Any required additional targets (Player, Property) are available per `EventDefinition.targetType`

### Scan Sequence

```text
READY
  ↓
Scan Event QR
  ↓
EVENT_IDENTIFIED
  ↓
Game Engine reads EventDefinition (actionType, parameters, targetType)
  ↓
[If targetType requires PLAYER → WAITING_FOR_PLAYER → Scan Player QR]
  ↓
[If targetType requires PROPERTY → WAITING_FOR_PROPERTY → Scan Property QR]
  ↓
[Repeat additional scans as required by specific Event]
  ↓
Validate all targets
  ↓
Confirmation if required
  ↓
Execute Event action via Game Engine
  ↓
READY
```

### State Changes

Depends on Event. Examples (all values `TO_BE_CONFIRMED_FROM_ORIGINAL_RULES`):

```text
Bank credit to player
Bank debit from player
Transfer between players
Modify PropertyState.currentRentLevel
Force sale or auction
No monetary change (informational Event)
Transaction created reflecting effect
```

### Error Conditions

* Unknown QR
* Wrong QR type when additional scan expected
* Missing required target scan
* Invalid target (e.g. bankrupt player, unowned property when ownership required)
* Insufficient funds if Event causes debit → A013
* Duplicate scan
* Transaction cancelled

### Information Still Required

* Complete list of all Event cards and QR mappings
* Exact effect of each Event
* Which Events require Player scan
* Which Events require Property scan
* Which Events modify rent levels
* Scan order for multi-target Events
* Whether any Event triggers A012 (Auction) or A014 (Bankruptcy)

---

## A007 — Bank Credit

### Preconditions

* Active game exists
* Player QR resolves to an active player
* Credit amount and reason are known (from user input, board action, or Event context)
* Original rules allow the credit

### Scan Sequence

```text
READY
  ↓
[Context established: GO, Event, manual bank action, etc.]
  ↓
WAITING_FOR_PLAYER
  ↓
Scan Player QR
  ↓
Validate player
  ↓
Apply credit amount (from context or user entry)
  ↓
Confirmation if required
  ↓
Execute transaction
  ↓
READY
```

### State Changes

Conceptually:

```text
Player balance += credit amount
Transaction created (type = BANK_CREDIT, fromEntity = BANK)
```

Credit amount: `TO_BE_CONFIRMED_FROM_ORIGINAL_RULES` unless supplied by context (e.g. Event parameters).

### Error Conditions

* Unknown QR
* Wrong QR type
* Player not active
* Invalid or missing amount context
* Duplicate scan
* Transaction cancelled

### Information Still Required

* Which situations trigger bank credits outside of Events
* GO collection amount (may overlap with A009)
* Whether manual bank credit is supported on original unit
* Exact amounts for each credit scenario

---

## A008 — Bank Debit

### Preconditions

* Active game exists
* Player QR resolves to an active player
* Debit amount and reason are known
* Original rules allow the debit
* Sufficient funds or insufficient-funds handling per A013

### Scan Sequence

```text
READY
  ↓
[Context established: tax, fine, Event, manual bank action, etc.]
  ↓
WAITING_FOR_PLAYER
  ↓
Scan Player QR
  ↓
Validate player
  ↓
Apply debit amount (from context or user entry)
  ↓
Confirmation if required
  ↓
Execute transaction (or A013 if insufficient funds)
  ↓
READY
```

### State Changes

Conceptually:

```text
Player balance -= debit amount
Transaction created (type = BANK_DEBIT, toEntity = BANK)
```

Debit amount: `TO_BE_CONFIRMED_FROM_ORIGINAL_RULES` unless supplied by context.

### Error Conditions

* Unknown QR
* Wrong QR type
* Player not active
* Insufficient balance → A013
* Invalid or missing amount context
* Duplicate scan
* Transaction cancelled

### Information Still Required

* Which situations trigger bank debits outside of Events
* Jail fine amount (may overlap with A010)
* Tax amounts for Location spaces (may overlap with A011)
* Whether partial payment is allowed

---

## A009 — Pass/Land on GO

### Preconditions

* Active game exists
* Player QR resolves to an active player
* Physical board indicates player passed or landed on GO
* Original GO banking rules confirmed

### Scan Sequence

```text
READY
  ↓
User indicates GO action (passed or landed)
  ↓
WAITING_FOR_PLAYER
  ↓
Scan Player QR
  ↓
Validate player
  ↓
Apply GO banking rule
  ↓
Confirmation if required
  ↓
Execute transaction (typically A007 Bank Credit)
  ↓
READY
```

### State Changes

Conceptually:

```text
Player balance += GO amount
Transaction created (type = GO_COLLECTION or BANK_CREDIT)
```

GO amount: `TO_BE_CONFIRMED_FROM_ORIGINAL_RULES`.

Whether passing GO vs landing on GO differs: `TO_BE_CONFIRMED_FROM_ORIGINAL_RULES`.

### Error Conditions

* Unknown QR
* Wrong QR type
* Player not active
* Duplicate scan
* No active game

### Information Still Required

* Exact GO collection amount
* Whether passing GO and landing on GO pay the same amount
* Whether original unit distinguishes pass vs land
* Scan sequence on original unit

---

## A010 — Jail Payment

### Preconditions

* Active game exists
* Player QR resolves to an active player
* Player is in jail or paying jail-related fine per original rules
* Jail banking behavior confirmed

### Scan Sequence

```text
READY
  ↓
User initiates jail payment action
  ↓
WAITING_FOR_PLAYER
  ↓
Scan Player QR
  ↓
Validate player and jail status
  ↓
Apply jail payment per original rules
  ↓
Confirmation if required
  ↓
Execute transaction
  ↓
READY
```

### State Changes

Conceptually:

```text
Player balance -= jail fine/payment amount
PlayerState.jailStatus updated per original rules
Transaction created (type = JAIL_PAYMENT)
```

All values: `TO_BE_CONFIRMED_FROM_ORIGINAL_RULES`.

### Error Conditions

* Unknown QR
* Wrong QR type
* Player not in jail (if jail status is tracked)
* Insufficient balance → A013
* Duplicate scan
* Transaction cancelled

### Information Still Required

* Exact jail fine amount
* Whether jail payment uses bank debit only or also updates jail status digitally
* Whether "Get Out of Jail Free" cards affect banking (physical card only in V1?)
* Whether multiple jail payment options exist (pay vs card)

---

## A011 — Location Space

### Preconditions

* Active game exists
* Player QR resolves to an active player
* Location identified (via user selection, Property QR, or board context)
* Location-space banking behavior confirmed from original rules

### Scan Sequence

```text
READY
  ↓
User indicates location space action
  ↓
WAITING_FOR_PLAYER
  ↓
Scan Player QR
  ↓
[If required: WAITING_FOR_PROPERTY or location selection]
  ↓
Validate
  ↓
Apply location banking rule (credit, debit, or other)
  ↓
Confirmation if required
  ↓
Execute transaction
  ↓
READY
```

### State Changes

Depends on location. Conceptually:

```text
Player balance +=/-= location amount
Possible PropertyState change
Transaction created (type = LOCATION)
```

All amounts and behaviors: `TO_BE_CONFIRMED_FROM_ORIGINAL_RULES`.

### Error Conditions

* Unknown QR
* Wrong QR type
* Player not active
* Insufficient balance for debit locations → A013
* Invalid location context
* Duplicate scan

### Information Still Required

* Complete list of location spaces with banking actions
* Exact amounts for each location
* Whether Property QR scan is required for any location
* Income tax, luxury tax, and similar space behaviors

---

## A012 — Auction

### Preconditions

* Active game exists
* Auction triggered per original rules (unowned property, bankruptcy, Event, etc.)
* Participating players identified
* Original auction rules confirmed

### Scan Sequence

```text
READY
  ↓
Auction initiated (context from A003 failure, Event, bankruptcy, etc.)
  ↓
Property identified (if property auction)
  ↓
Bidding recorded per original unit behavior
  ↓
WAITING_FOR_PLAYER
  ↓
Scan winning Player QR
  ↓
Validate winner and bid amount
  ↓
Confirmation
  ↓
Execute transaction
  ↓
READY
```

### State Changes

Conceptually:

```text
Winner balance -= winning bid
Property owner = winner
Property state = initial owned state
Transaction created (type = AUCTION)
```

Auction mechanics (bid increments, all-pay vs winner-pay, timing): `TO_BE_CONFIRMED_FROM_ORIGINAL_RULES`.

### Error Conditions

* Unknown QR
* Wrong QR type
* Winner insufficient funds → A013
* Property no longer available
* Invalid bid amount
* Auction cancelled

### Information Still Required

* Exact auction rules from original game/manual
* How original banking unit records bids
* Minimum bid and bid increment
* Whether all players must scan or only winner
* Whether auction is required when player declines purchase

---

## A013 — Insufficient Funds

### Preconditions

* Active game exists
* A transaction attempt (A003, A004, A006, A008, A010, A011, A012, etc.) failed due to insufficient balance
* Player QR resolves to the affected player
* Original insufficient-funds rules confirmed

### Scan Sequence

```text
TRANSACTION_ATTEMPT fails (insufficient balance)
  ↓
SHOWING_RESULT (insufficient funds)
  ↓
WAITING_FOR_PLAYER
  ↓
Scan Player QR
  ↓
Present debt-resolution options per original rules
  ↓
[Possible: sell property, mortgage, raise funds, or proceed to A014]
  ↓
User selects resolution path
  ↓
Execute resolution or escalate to A014
  ↓
READY or retry original transaction
```

### State Changes

Depends on resolution path:

```text
Possible property sale (ownership change, credit to player)
Possible mortgage state change
Possible partial payment
If unresolvable → trigger A014 Bankruptcy
Transaction(s) created for each resolution step
```

All options: `TO_BE_CONFIRMED_FROM_ORIGINAL_RULES`.

### Error Conditions

* Unknown QR
* Wrong QR type
* No resolution options available
* User cancels
* Resolution still leaves insufficient funds

### Information Still Required

* Exact insufficient-funds flow on original banking unit
* Whether property selling is supported
* Order of asset liquidation
* Whether player can retry payment after raising funds
* Time limits or turn constraints (physical game may handle; banking unit behavior TBD)

---

## A014 — Bankruptcy

### Preconditions

* Active game exists
* Player cannot resolve debt via A013
* Original bankruptcy rules satisfied
* Creditor/context identified (player, bank, or property owner)

### Scan Sequence

```text
A013 exhausted or bankruptcy declared
  ↓
Identify bankrupt player and creditor
  ↓
Scan Player QR (bankrupt player) if required
  ↓
[Scan creditor Player QR or Property QR if required]
  ↓
Apply bankruptcy resolution per original rules
  ↓
Confirmation
  ↓
Execute state changes
  ↓
READY
```

### State Changes

Conceptually:

```text
PlayerState.bankrupt = true
PlayerState.active = false (per original rules)
Transfer remaining assets to creditor or bank
Property ownership changes
Transaction(s) created (type = BANKRUPTCY)
```

All details: `TO_BE_CONFIRMED_FROM_ORIGINAL_RULES`.

### Error Conditions

* Unknown QR
* Wrong QR type
* Player already bankrupt
* Invalid creditor context
* Transaction cancelled

### Information Still Required

* Exact bankruptcy rules
* Asset transfer order
* Whether properties go to creditor or bank
* Whether bankrupt player can rejoin
* End-game implications

---

## A015 — Undo Last Action

### Preconditions

* Active game exists
* At least one reversible Transaction exists
* Last transaction marked `reversible = true`
* User requests undo

### Scan Sequence

```text
READY
  ↓
User selects Undo Last Action
  ↓
WAITING_FOR_CONFIRMATION
  ↓
User confirms
  ↓
Game Engine restores state from last Transaction.stateBefore
  ↓
Mark transaction as undone (or create compensating record)
  ↓
SHOWING_RESULT
  ↓
READY
```

### State Changes

Conceptually:

```text
Restore PlayerState, PropertyState from stateBefore snapshot
Mark last transaction undone
Possibly create UNDO transaction for audit trail
```

Which actions are reversible: `TO_BE_CONFIRMED_FROM_ORIGINAL_RULES`.

### Error Conditions

* No reversible transaction exists
* Last action marked non-reversible (e.g. game start, bankruptcy)
* User cancels confirmation
* State restoration fails (data integrity error)

### Information Still Required

* Whether original banking unit supports undo
* Which actions are reversible on original unit
* Whether only last action or multiple undo steps allowed

---

## A016 — Save Game

### Preconditions

* Active GameSession exists
* Game state has changed since last save (or save forced on exit)

### Scan Sequence

```text
[Automatic trigger: after transaction, on app pause, on interval, etc.]
  ↓
Serialize GameSession, PlayerState, PropertyState, Transaction history
  ↓
Persist to local storage
  ↓
Update GameSession.updatedAt
  ↓
Silent completion or brief indicator
```

No QR scan required.

### State Changes

None to game logic state. Persistence layer updated.

### Error Conditions

* Storage full or write failure
* Serialization error
* No active game to save

### Information Still Required

* Save trigger frequency (after every transaction, on pause, manual)
* Whether original unit had save/resume (for compatibility reference)
* Maximum number of saved games
* Storage format (decision deferred to implementation)

---

## A017 — Resume Game

### Preconditions

* Valid saved GameSession exists in local storage
* No conflicting active session (or user chooses to resume over current)
* Saved data passes integrity validation

### Scan Sequence

```text
App launch or user selects Resume
  ↓
List available saved games (if multiple)
  ↓
User selects saved game
  ↓
Load and validate persisted state
  ↓
Restore GameSession, PlayerState, PropertyState, Transactions
  ↓
Set scanner to READY
  ↓
SHOWING_RESULT (game dashboard)
```

No QR scan required to resume (unless original unit required re-scan: `TO_BE_CONFIRMED_FROM_ORIGINAL_RULES`).

### State Changes

Conceptually:

```text
GameSession.status = ACTIVE
All state records restored from persistence
Scanner state = READY
```

### Error Conditions

* No saved game found
* Corrupt or incomplete save data
* Version mismatch (future schema migrations)
* User cancels

### Information Still Required

* Whether resume requires player re-identification scans
* How many games can be stored simultaneously
* Whether auto-resume on app relaunch is desired

---

## Action Cross-References

| Action | May trigger | May be triggered by |
| ------ | ----------- | ------------------- |
| A003   | A013        | User, board play    |
| A004   | A013        | User, board play    |
| A006   | A007, A008, A012, A013, A014 | Event card draw |
| A007   | —           | A009, A006, A011    |
| A008   | A013        | A006, A010, A011    |
| A009   | A007        | Board play          |
| A010   | A008, A013  | Board play          |
| A011   | A007, A008, A013 | Board play       |
| A012   | A013        | A003 decline, A006, A014 |
| A013   | A014        | A003–A008, A010–A012 |
| A014   | —           | A013                |
| A015   | —           | User                |
| A016   | —           | Automatic           |
| A017   | —           | User, app launch    |

---

## Compatibility Testing Note

For each action above, a test scenario should eventually be defined comparing:

```text
Same physical game action

Original Banking Unit  →  expected final state
Android Application    →  expected final state

Must match.
```

See [PROJECT_SCOPE.md](PROJECT_SCOPE.md) — Rule 7.
