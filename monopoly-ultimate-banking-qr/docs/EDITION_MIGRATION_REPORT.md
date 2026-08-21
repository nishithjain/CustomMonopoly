# Edition Migration Report

Default edition after migration: **uk**

India edition data: **present** (22 properties, 23 events, banking values)

India artwork: **INCOMPLETE** — folders exist, no fabricated UK copies

India exposed in New Game: **NO**

Old saves without `editionId` resume as **uk**.

## Data

| Old path | New path |
| --- | --- |
| `data/cards.json` | `data/common/card_registry.json` |
| `data/game_rules.json` | `data/common/game_rules.json` |
| `data/event_engine_rules.json` | `data/common/event_engine_rules.json` |
| `data/properties.json` | `data/editions/uk/properties.json` |
| `data/events.json` | `data/editions/uk/events.json` |
| `data/banking_values.json` | `data/editions/uk/banking_values.json` |
| `data/board_relationships.json` | `data/editions/uk/board_relationships.json` |
| `CustomCardGenerators/properties_india.json` (source snapshot) | `data/editions/india/properties.json` (authoritative) |

Android generated copies:

```text
android-app/app/src/main/assets/game/common/
android-app/app/src/main/assets/game/editions/uk/
android-app/app/src/main/assets/game/editions/india/
```

## Resources

| Old path | New path |
| --- | --- |
| `Resources/Icons/` | `Resources/Common/Icons/` |
| `Resources/Sounds/` | `Resources/Common/Sounds/` |
| `Resources/Cards/UserCards/` | `Resources/Common/UserCards/` |
| `Resources/Cards/PropertyCards/` | `Resources/Editions/uk/PropertyCards/` |
| `Resources/Cards/EventCards/` | `Resources/Editions/uk/EventCards/` |
| _(none)_ | `Resources/Editions/india/PropertyCards/` |
| _(none)_ | `Resources/Editions/india/EventCards/` |

## Notes

- Stable IDs and QR payloads were not changed.
- UK purchase prices and rents were snapshotted in `data/uk_property_baseline.json`.
- India Event text currently matches the UK baseline; do not invent India-themed Event copy until a real dataset exists.
- Obsolete `Resources/Cards`, `Resources/Icons`, and `Resources/Sounds` directories are removed after generated Android assets are rebuilt from the new trees.
