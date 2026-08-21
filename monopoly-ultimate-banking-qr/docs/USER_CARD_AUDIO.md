# User Card Identification Audio

User card scans play a short identification clip mapped by stable player ID only.

## Mapping

| Player ID | Display Name | Source | Android Raw |
|-----------|--------------|--------|-------------|
| USR_01 | Car | `Resources/Common/Sounds/UserCardSounds/Car.mp3` | `res/raw/user_car.mp3` |
| USR_02 | Helicopter | `Resources/Common/Sounds/UserCardSounds/Helicopter.mp3` | `res/raw/user_helicopter.mp3` |
| USR_03 | Ship | `Resources/Common/Sounds/UserCardSounds/Ship.mp3` | `res/raw/user_ship.mp3` |
| USR_04 | Aeroplane | `Resources/Common/Sounds/UserCardSounds/Aeroplane.mp3` | `res/raw/user_aeroplane.mp3` |

Mapping is defined once in `UserCardSoundRegistry`. Sound selection never uses QR payload text, filename parsing, or display-name substring matching.

## Trigger Flow

```text
Accepted QR (ScanGate)
      ↓
CardResolver
      ↓
ResolvedCard
      ↓
ScanAudioFeedback
      ↓
USER card → playUserCard(playerId)
```

This runs in `ScannerViewModel` immediately after `ScanProcessorResult.CardResolved` and before workflow validation UI updates.

## Wrong USER Card in Scanner

When the scanner expects a different card type:

```text
Wrong USER card
      ↓
User identification sound
      ↓
Error sound
      ↓
Existing workflow error message
```

Implemented as `playUserCardThenError(playerId)` with sequenced playback (not overlapping).

## Duplicate Camera Frames

ScanGate duplicate protection ensures one accepted scan produces one user sound. Holding the same QR in view does not replay identification audio.

Rearming via **Scan Another** allows intentional rescan of the same card.

## Playback Policy

Rapid different user scans: the latest intentional user scan takes priority; pending sequenced error playback is cancelled when a new user clip starts.

## Sound Default

Sound is enabled by default. `GameAudioFeedback.enabled` can be wired to a future settings preference without changing call sites.

## Non-Goals

- Audio does not modify `GameSession`.
- Audio does not execute `GameCommand`.
- Audio does not define game rules.
