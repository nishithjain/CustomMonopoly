# Version 1 Feature Freeze

**Version:** 1.0.0  
**Status:** FROZEN — no major new features after this document.

Banking QR is a digital replacement for the Monopoly Ultimate Banking unit. The physical board, tokens, dice, and printed QR cards remain authoritative for position and movement.

## Supported in V1

| Area | Capability |
|------|------------|
| Players | 2–4 players via 4 User QR cards (Car, Helicopter, Ship, Aeroplane) |
| Cards | 49 QR cards: 4 User, 22 Property, 23 Event |
| Setup | New Game, player registration, M1500 starting balance, Start Game |
| Property | Purchase, rent, rent levels 1–5, owner landing, color-set completion bonus |
| Events | All 23 Event Engine rules with UI workflows |
| Banking | GO (M200), Location (M100), Jail, Auction (M20 bids), Debt, Bankruptcy |
| End game | Winner by cash + property purchase prices; tie-break by highest property value |
| Undo | Single-depth undo for eligible banking actions only |
| Persistence | Autosave on commit, Resume, transaction history |
| Operation | Offline-first; camera QR scanning only |

## Explicitly NOT supported in V1

- Virtual board or token positions
- Virtual dice
- Online multiplayer, cloud accounts, Firebase
- Custom cities, custom boards, custom properties
- Player trading, mortgages, house selling (classic Monopoly rules)
- iOS
- Restoring in-progress scanner workflows after app kill (GR-SAVE-002)
- Exact hardware auction timer parity (app uses 30s configurable timer)
- Engine-persisted jail failed-doubles counter (physical dice tracking)

## Architecture constraints (unchanged)

- `:game-core` — pure Kotlin rules; no Android
- Android UI — collect input → `GameCommand` → engine → persist → display
- No rule duplication in Compose/ViewModels

## Release scope

Step 12 may fix bugs and improve necessary UX only. No Phase 2 features.
