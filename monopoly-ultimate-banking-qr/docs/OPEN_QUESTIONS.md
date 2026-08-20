# Open Questions

Checklist of items requiring confirmation. Updated after Step 4 rule confirmation from the original Ultimate Banking manual.

See [RULE_GAPS.md](RULE_GAPS.md) and [data/rule_spec_validation.txt](../data/rule_spec_validation.txt).

---

## Player Setup

- [x] Exact starting player balance — **M1500**; GR-SETUP-004; `data/game_rules.json`
- [x] Minimum players — **2**; GR-SETUP-002
- [x] Maximum players — **4**; GR-SETUP-003
- [x] Player card QR mapping — Step 2; `data/cards.json`
- [x] Duplicate player rejection — GR-SETUP-005

## Property Cards

- [x] Property QR mappings, prices, rents, colour groups — Step 3
- [x] Initial rent level = 1 — Step 3
- [x] Maximum rent level = 5 — Step 3
- [x] Owner lands on own property — **rent level +1, no rent**; GR-PROPERTY-003
- [x] Visitor landing — **pay current rent, then level +1**; GR-RENT-001
- [x] Minimum rent level = 1 — GR-RENT-005
- [x] Colour-set completion bonus — GR-COLORSET-001
- [x] Property liquidation at purchasePrice — GR-PROPERTY-006
- [x] No normal player-to-player trading — GR-PROPERTY-005
- [x] Mortgage — not supported; `mortgageSupported = false`

## Event Cards

- [x] All Event QR mappings and printed text — Step 3
- [x] Game Engine specification — Step 4; **23/23 RESOLVED**
- [x] EVT_08 / EVT_10 neighbours — circular PRP chain; `data/board_relationships.json`
- [x] EVT_13 temporary effect — global FORCE_LEVEL_1_RENT, 2 uses; GR-EVENT-003
- [x] EVT_21 Total Gridlock — GR-EVENT-004
- [x] EVT_15 / EVT_22 board sides — confirmed from supplied physical board layout; `data/board_relationships.json`
- [x] Owned-properties-only Event rule — GR-EVENT-002
- [x] Scan requirements — Step 3 `requiresPlayerScan` / `requiresPropertyScan`

## Board-Related Banking

- [x] GO payment M200 pass and land — GR-GO-001
- [x] GO suppression (Event, Location, Jail) — GR-GO-002
- [x] Jail banking — GR-JAIL-001–004
- [x] Jail rent exception — GR-RENT-001, GR-JAIL-003
- [x] Location space M100 — GR-LOCATION-001
- [x] Board neighbours — `data/board_relationships.json`

## Transactions and Disputes

- [x] Auction rules — GR-AUCTION-001
- [x] Insufficient funds / debt — GR-DEBT-001
- [x] Bankruptcy — GR-BANKRUPTCY-001
- [x] Undo — GR-UNDO-001 (depth 1; Events not undoable)
- [x] Transaction types — TRANSACTION_RULES.md

## Game Flow

- [x] End game on first bankruptcy — GR-BANKRUPTCY-001
- [x] Winner by total wealth — GR-ENDGAME-001
- [x] Tie breaker — highest purchasePrice property
- [x] Game Engine architecture — GAME_ENGINE_DESIGN.md
- [x] Restore in-progress scan workflow on restart — GR-SAVE-002: discard transient scan state, restore committed session only, return to READY

## QR and Card Data

- [x] All QR mappings — Step 2

## Compatibility Testing

- [ ] Access to original banking unit for side-by-side comparison
- [x] Test scenarios — RULE_TEST_SCENARIOS.md
- [ ] Documented edge cases from original manual (ongoing)

---

When an item is confirmed, update `GAME_RULES.md`, `data/game_rules.json`, and check the box here.
