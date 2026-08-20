# V1 Known Limitations

Honest limitations for Version 1.0.0. Do not treat this app as commercial/store-ready.

## Physical game required

- Board position and token movement are **manual** on the physical board.
- Dice are **physical**; the app does not roll dice.
- Property and Event cards are **physical**; the app records ownership and rent state only.

## No digital board

- No virtual Monopoly board, token tracking, or GO inference from movement.
- GO salary is a deliberate banking action when the physical move qualifies.

## Auction timing

- App auction uses a **30-second** countdown (resets on bid).
- Original Ultimate Banking unit timing is **not** claimed to match exactly.

## App restart (GR-SAVE-002)

- Only **committed** game state is restored.
- In-progress scans, auction countdown, and partial debt selection are **discarded**.
- Unfinished auctions revert to last committed property ownership.

## Jail failed doubles

- Physical dice track failed jail attempts.
- Engine does not persist a jail-turn counter; third-failed-turn flow relies on physical play + M100 jail payment.

## Location workflow

- `PayLocationFee` combines fee payment with destination property landing in one engine command.
- UI collects player + destination property QR before committing.

## Scope limits

- No custom boards or cities
- No player trading
- No online multiplayer or cloud sync
- No iOS build
- Portrait orientation locked for reliable tabletop layout

## Signing

- Release APK may be **unsigned** (`SIGNING REQUIRED` for distribution outside debug installs).

## Testing status

Automated tests and structural validators pass. **Full physical 49-card and complete-game validation** must be completed by the user — see [V1_TEST_REPORT.md](V1_TEST_REPORT.md) and physical checklists.
