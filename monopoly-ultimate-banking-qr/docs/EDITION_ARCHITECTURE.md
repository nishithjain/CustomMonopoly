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
    index.json              # catalogue for New Game edition discovery
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
EditionRepository.loadEditionCatalog()
        ↓
New Game UI (enabled editions only)
        ↓
EditionRepository.load(editionId)
        ↓
GameDefinitions
        ↓
GameEngine
```

The Game Engine does not branch on country. It only consumes `GameDefinitions`.

`data/editions/index.json` is the source of truth for editions available to New Game. The catalogue selects an edition package; it does not contain gameplay definitions.

Default edition for New Game comes from `defaultEditionId` in the catalogue (currently `uk`). `GameSession.editionId` is persisted with every saved game.

**Legacy migration:** saved games created before `editionId` was persisted deserialize with a missing or blank edition ID treated as `"uk"` only. This rule lives in `EditionIds.normalize()` and `GameSession`'s default parameter; it does not rewrite stored saves.

Disabled catalogue entries cannot be selected for new games, but an existing saved game remains resumable even if its edition is later disabled.

## Adding a future edition

1. Add `data/editions/<editionId>/` with the required edition JSON files.
2. Add the edition to `data/editions/index.json`.
3. Define its edition metadata, card registry, board layout, properties, Events, banking values, board relationships, and game rules.
4. Add edition-specific card and board artwork under the matching edition artwork path.
5. Run asset synchronization, edition validation, and tests (`python tools/sync_android_assets.py`, `python tools/validate_editions.py`).
6. No Kotlin source change should be necessary when the edition uses already supported space types, rule modes, and Event action types.
7. A genuinely new space type, rule mode, or Event mechanic requires a typed Kotlin implementation and tests; never place executable code in JSON.
