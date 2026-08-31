# Edition Definition Versioning

Saved games persist both `editionId` and `editionDefinitionVersion`. Resume succeeds only when the installed edition's `definitionVersion` in `edition.json` exactly matches the saved value.

## Policy

```text
saved editionDefinitionVersion == installed definitionVersion
```

If the versions differ, the app rejects resume with a typed incompatibility result. The save is not modified, upgraded, or deleted automatically.

Legacy saves without `editionDefinitionVersion` in JSON deserialize as version `1`.

## When to increment `definitionVersion`

Increment the version in that edition's `edition.json` when changing gameplay-affecting edition data, including:

- Property purchase prices
- Rent levels
- Event behaviour or values
- Banking values
- Board-space count, order, type, IDs, targets, or deck references in `board_layout.json`
- Board relationships, regions, sides, neighbours, or Location destinations
- Card identities or references
- Card counts or rent-level structure
- Any rule-affecting edition configuration

Changing an Event's display text alone does not require a bump when it cannot change play. Changing its action type, action order, parameters, targets, value, deck membership, or follow-up requirements does require a bump.

A version bump is normally unnecessary for artwork-only changes, spelling corrections, or display-name changes that cannot affect gameplay. Visual board artwork changes normally need no bump; changes to `board_layout.json` do.

## Process

```text
1. Modify gameplay-affecting edition data.
2. Increment definitionVersion in that edition's edition.json.
3. Run edition validation and saved-game compatibility tests.
```

## Runtime flow

```text
New Game
  -> load selected edition manifest
  -> store editionId + definitionVersion in GameSession
  -> persist in sessionJson on autosave

Resume Game
  -> deserialize GameSession
  -> load installed manifest for saved editionId
  -> compare saved vs installed definitionVersion
  -> match: bind edition and continue
  -> mismatch: IncompatibleEditionVersion (save unchanged)
```
