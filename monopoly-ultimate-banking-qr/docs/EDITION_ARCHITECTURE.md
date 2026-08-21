# Edition Architecture

Regional Monopoly editions share one Game Engine and one physical QR identity. Edition-specific names, prices, artwork, and banking amounts live in isolated data folders.

## Layout

```text
data/
  common/
    card_registry.json      # stable cardId / cardType / qrPayload
    game_rules.json         # shared rule configuration
    event_engine_rules.json # ID-based Event Engine behaviour
  editions/
    uk/
    india/
      edition.json
      properties.json
      banking_values.json
      events.json
      board_relationships.json

Resources/
  Common/
    Icons/
    Sounds/
    UserCards/
  Editions/
    uk/
      PropertyCards/
      EventCards/
    india/
      PropertyCards/
      EventCards/
```

## Responsibilities

`data/common/` is shared identity and rules. It must not hard-code `Resources/Editions/uk/...` for Property/Event artwork.

`data/editions/<editionId>/` holds Property names/prices/rents, Event text, board relationships (by Property ID), and banking values.

`Resources/Common/` holds Player icons, sounds, and the four shared User cards.

`Resources/Editions/<editionId>/` holds that edition’s Property/Event artwork.

## Runtime

```text
EditionRepository.load("uk" | "india")
        ↓
GameDefinitions
        ↓
GameEngine
```

The Game Engine does not branch on country. It only consumes `GameDefinitions`.

Default edition for New Game is `uk`. `GameSession.editionId` is persisted. Saves without `editionId` are treated as `uk`.

## Adding a future edition

1. Create `data/editions/<id>/`
2. Add `edition.json`
3. Add `properties.json`
4. Add `banking_values.json`
5. Add `events.json`
6. Add `board_relationships.json`
7. Add `Resources/Editions/<id>/PropertyCards` and `EventCards`
8. Run `python tools/validate_editions.py`

No Game Engine rule code should normally need modification.

India is data-complete but artwork-incomplete. It is not exposed in New Game and is not READY_FOR_PLAY until original artwork exists.
