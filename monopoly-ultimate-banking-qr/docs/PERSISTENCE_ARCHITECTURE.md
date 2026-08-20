# Persistence Architecture

Step 8 adds durable storage for the last **committed** `GameSession` snapshot.

## Data flow

```text
             :game-core
                 │
                 │ GameSession
                 ↓
       GameSessionSerializer
                 │
                 │ JSON
                 ↓
             :app
                 │
      RoomGameSessionRepository
                 │
                 ↓
              Room
```

## Responsibilities

| Layer | Responsibility |
|-------|----------------|
| `GameSession` (`:game-core`) | Authoritative domain state and rules |
| `GameSessionSerializer` (`:game-core`) | Platform-neutral JSON encode/decode |
| `SessionRestoreValidator` (`:game-core`) | Validates restored IDs against loaded definitions |
| `SavedGameEntity` (`:app`) | Durable snapshot row + metadata |
| `RoomGameSessionRepository` (`:app`) | Save/load/delete operations |
| `CommittedGameSessionStore` (`:app`) | Persists only successful committed `GameResult`s |

## Snapshot model (Version 1)

Room stores one row per game in `saved_games`:

- `gameId` (stable identity)
- `status`, timestamps (`createdAt`, `updatedAt`, `committedAt`)
- `schemaVersion` (currently `1`)
- lightweight metadata (`playerCount`, `transactionCount`)
- `sessionJson` (full serialized `GameSession`)

Room is **not** used to normalize players, properties, effects, or transactions into separate tables.

## GR-SAVE-002

Only committed `GameSession` snapshots are durable.

Transient scanner/workflow state (for example `WAITING_FOR_PLAYER`, camera frames, Compose navigation) is intentionally **not** persisted. On restart, the app restores the last committed session and scanner workflow returns to `READY`.

## Commit rule

```text
GameEngine.process(...)
       ↓
GameResult
       ↓
CommittedGameSessionStore.commitGameResult(...)
       ↓
if success and not REJECTED:
    repository.save(session)
```

Failures, rejected commands, unknown QR, and incomplete scanner workflows do not create a new durable gameplay snapshot.

## Schema versioning

- Current schema version: `1`
- Unsupported versions return `SavedGameLoadResult.IncompatibleVersion`
- Future format changes must increment schema version and add explicit Room migrations

## Migration policy

- Database version: `1`
- **No** `fallbackToDestructiveMigration()` in production
- Future schema changes require explicit migrations to avoid silent deletion of saved games

## Persistence failure

If Room save fails after a successful in-memory engine commit, `CommitResult.PersistenceFailed` is returned. The app does not claim the game was durably saved.

## Physical QR validation (Step 7)

Basic physical device validation completed:

- User card: PASS
- Property card: PASS
- Event card: PASS
- Duplicate protection: PASS
- Unknown QR: PASS

Full 49-card physical checklist remains separate in `docs/PHYSICAL_QR_TEST_CHECKLIST.md`.

## Not in Step 8

- Scanner → `GameCommand` integration
- Real gameplay UI workflows
- Purchase/rent/event execution from QR scans
