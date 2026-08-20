# Physical Gameplay Test Checklist

Mark **PASS** only after completing each item on a physical device with printed QR cards.

## Setup and core

- [ ] New Game → 2 players → M1500 → Start Game
- [ ] New Game → 4 players → M1500 → Start Game
- [ ] Resume after force-stop (committed state)
- [ ] GR-SAVE-002: kill during scan workflow → READY, no half-commit
- [ ] Offline / airplane mode — full session works
- [ ] Unknown / wrong card — no crash, clear message
- [ ] Double-tap protection — one committed action

## Property matrix

- [ ] Unowned property → BUY
- [ ] Unowned property → AUCTION
- [ ] Visitor rent payment
- [ ] Owner landing (rent level +1, no rent)
- [ ] Rent Level 5 clamp
- [ ] Jailed owner — no rent, no landing increase
- [ ] Color set — single owner +2 levels (once only)
- [ ] Color set — split owners +1 level (once only)
- [ ] Bonus does not re-fire after swap/debt transfer

### Old Kent Road reference (PRP_01)

- [ ] Car buys → M1440
- [ ] Helicopter pays M70 → Level 2
- [ ] Car lands own → Level 3, no payment

## Board relationships

- [ ] Neighbours (e.g. PRP_05 ↔ PRP_04, PRP_06)
- [ ] Wrap (PRP_01 ↔ PRP_22, PRP_02)
- [ ] Board sides via EVT_15 / EVT_22

## Advanced banking

- [ ] GO M200 + persistence + Undo
- [ ] GO not from Event/Location/Jail/Gridlock
- [ ] Location M100 + physical move instruction + no GO
- [ ] Jail send / status / no rent while jailed
- [ ] Jail fee M100 / doubles release
- [ ] Auction 2-player (M20 bids, winner, Level 1)
- [ ] Auction 4-player / jailed bidder blocked
- [ ] Auction no bids → unowned
- [ ] Auction app kill → no ghost winner
- [ ] Debt to player (transfer, rent preserved, change)
- [ ] Debt to bank (unowned, Level 1 on repurchase)
- [ ] Bankruptcy → FINISHED, gameplay locked
- [ ] Winner wealth formula (cash + purchase prices)
- [ ] Undo: purchase, rent, GO, jail, location
- [ ] Event not undoable; debt blocks prior undo
- [ ] Transaction history matches actions

## Events (23 / 23)

| Event ID | Name | QR Scan | Workflow | Engine | Physical | Persist | Result |
|----------|------|---------|----------|--------|----------|---------|--------|
| EVT_01 | Boom Town | | | | | | NOT_TESTED |
| EVT_02 | Crime Down | | | | | | NOT_TESTED |
| EVT_03 | Deal Of The Week | | | | | | NOT_TESTED |
| EVT_04 | Demolished | | | | | | NOT_TESTED |
| EVT_05 | Grand Designs | | | | | | NOT_TESTED |
| EVT_06 | Haunted House | | | | | | NOT_TESTED |
| EVT_07 | Highway Tax | | | | | | NOT_TESTED |
| EVT_08 | House Party | | | | | | NOT_TESTED |
| EVT_09 | In The Money | | | | | | NOT_TESTED |
| EVT_10 | It's A Boy! | | | | | | NOT_TESTED |
| EVT_11 | Love Is In The Air | | | | | | NOT_TESTED |
| EVT_12 | On The Map | | | | | | NOT_TESTED |
| EVT_13 | On The Run | | | | | | NOT_TESTED |
| EVT_14 | Pick Your Own | | | | | | NOT_TESTED |
| EVT_15 | Pong! What A Stinker | | | | | | NOT_TESTED |
| EVT_16 | Rover's Revenge | | | | | | NOT_TESTED |
| EVT_17 | Stargazing | | | | | | NOT_TESTED |
| EVT_18 | Stop The Presses | | | | | | NOT_TESTED |
| EVT_19 | 'Tis The Season | | | | | | NOT_TESTED |
| EVT_20 | Tornado Alley | | | | | | NOT_TESTED |
| EVT_21 | Total Gridlock | | | | | | NOT_TESTED |
| EVT_22 | What A Ride! | | | | | | NOT_TESTED |
| EVT_23 | Wibble Wobble | | | | | | NOT_TESTED |

### Complex events — extra attention

- [ ] EVT_06 / EVT_09 — property swaps
- [ ] EVT_08 / EVT_10 — neighbours
- [ ] EVT_13 — Level-1 rent ×2, level not reset
- [ ] EVT_15 — board side −1
- [ ] EVT_21 — Total Gridlock physical move, no GO
- [ ] EVT_22 — board side +1

## Full games

- [ ] 2-player complete game to winner — NOT RUN
- [ ] 4-player complete or broad scenario — NOT RUN

## Release smoke (release APK if signed)

- [ ] Clean install → New Game → scan 2 → Start → one transaction → Resume
- [ ] Debug-only buttons hidden in release build
