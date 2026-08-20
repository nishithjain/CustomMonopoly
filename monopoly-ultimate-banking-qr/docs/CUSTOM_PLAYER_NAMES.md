# Custom Player Names

## Identity model

Monopoly Ultimate Banking QR keeps three separate player identity concepts:

| Concept | Example | Purpose |
|---------|---------|---------|
| `playerId` | `USR_01` | Stable internal identity for rules, QR, transactions, audio, icons |
| `tokenName` | `Car` | Physical User card / token name from master definitions |
| `playerName` | `Nishith` | User-entered, game-specific display name stored in `PlayerState` |

Physical mapping remains unchanged:

```text
USR_01 → Car
USR_02 → Helicopter
USR_03 → Ship
USR_04 → Aeroplane
```

## Registration flow

During **NEW GAME / Player Setup**:

```text
Scan USER card → token audio (Car.mp3) → enter player name → confirm → RegisterPlayer(playerId, playerName)
```

Scanning a card does **not** register the player until the name is confirmed.

Cancel before confirm:

- No player registration
- No `GameSession` mutation
- No persistence write
- No error audio

## Validation

Custom names are validated in **game-core** (`PlayerNameRules`):

- Trim leading/trailing whitespace
- Minimum length after trim: **1**
- Maximum length: **10**
- Duplicate custom names across different cards are allowed
- Duplicate scans of the same physical card use existing duplicate-player handling

Structured errors:

- `InvalidPlayerName`
- `PlayerNameTooLong`

## UI display

After registration, UI shows:

```text
[Car icon] Nishith
```

`PlayerIdentity` renders icon from `playerId` and text from `playerName`.

Token name (`Car`) may appear secondarily during setup name entry, but is not the primary runtime label after registration.

## Setup-only rename

`RenamePlayer(playerId, playerName)` is allowed only while `GameSession.status = SETUP`.

After **START GAME**, names are locked for Version 1.

Rename does not reset balance or change registration order.

## Persistence

Custom names are stored inside the serialized `GameSession` snapshot (`PlayerState.playerName`).

Saved-game schema version:

- **v2** — includes custom player names
- **v1** saves still load; missing `playerName` deserializes as `""` and UI falls back to token name

No separate Room table was added for player names.

## Unchanged by custom names

| Area | Still based on `playerId` |
|------|---------------------------|
| QR payloads | Yes |
| Player icons | Yes |
| User-card audio | Yes |
| Game rules (ownership, rent, events, auctions, debt, jail, winner) | Yes |
| Transaction identity in domain | Yes (`playerId` only) |

Transaction history resolves display names at presentation time from current `GameSession` player state.

## Physical device checklist

1. Scan Car → enter `Nishith` → `[Car icon] Nishith` with M1500
2. Scan Helicopter → enter `Aditya`
3. Start game → dashboard shows custom names
4. Scan Car during active game → Car.mp3 + `[Car icon] Nishith`
5. Close app → resume → names preserved
