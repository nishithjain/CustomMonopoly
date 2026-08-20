# Android Architecture

Architecture for the Banking QR Android application (Step 5 foundation).

---

## Module Overview

```text
┌───────────────────────────────┐
│           :app                │
│                               │
│ Compose                       │
│ Navigation                    │
│ Android APIs                  │
│ Future Scanner                │
│ Future Persistence            │
└───────────────┬───────────────┘
                │
                ↓
┌───────────────────────────────┐
│        :game-core             │
│                               │
│ PURE KOTLIN                   │
│                               │
│ Game Engine                   │
│ Rules                         │
│ State                         │
│ Commands                      │
│ Events                        │
│ Transactions                  │
└───────────────────────────────┘
```

**Mandatory rule:** `:game-core` MUST NEVER depend on `:app`.

Dependency direction:

```text
Android App (:app)
    ↓
game-core (:game-core)
```

---

## Future Data Flow

```text
QR Camera
    ↓
Decoded string
    ↓
Card Resolver
    ↓
cardId
    ↓
Workflow Controller
    ↓
GameCommand
    ↓
:game-core Game Engine
    ↓
GameResult
    ↓
Persistence
    ↓
UI
```

The scanner is an **input adapter**. It must not contain game rule logic.

---

## UI Architecture (Future)

```text
Compose UI
    ↓
ViewModel (UI state only)
    ↓
Game Engine (:game-core)
```

Game rules must not live in Compose screens or ViewModels.

---

## Master Data

| Location | Role |
| -------- | ---- |
| `data/*.json` (repository root) | Authoritative master data |
| `app/src/main/assets/game/*.json` | Generated runtime copies (sync via `tools/sync_android_assets.py`) |

---

## Save and Resume (GR-SAVE-002)

On app restart during an in-progress scan workflow:

- Restore only the **last committed** GameSession state
- Discard transient/uncommitted scan workflow state
- Return scanner/workflow to `READY`

No partial workflow restoration.

---

## Step 5 Boundaries

**Included:**

- Gradle multi-module project
- Compose + Material 3 placeholder UI
- Navigation skeleton
- Pure Kotlin `:game-core` module
- Asset sync tooling

**Not included (later steps):**

- Game Engine implementation
- QR scanning
- Room persistence
- Game domain models

---

## Related Documents

- [GAME_ENGINE_DESIGN.md](GAME_ENGINE_DESIGN.md)
- [GAME_RULES.md](GAME_RULES.md)
- [../android-app/README.md](../android-app/README.md)
