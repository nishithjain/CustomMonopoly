#!/usr/bin/env python3
"""Validate Step 8 persistence scaffolding."""

from __future__ import annotations

import re
import sys
from pathlib import Path

ANDROID_IMPORT_PATTERN = re.compile(r"^\s*import\s+(android\.|androidx\.)")


def find_project_root() -> Path:
    current = Path(__file__).resolve().parent
    for candidate in [current.parent, current.parent.parent]:
        if (candidate / "android-app").is_dir() and (candidate / "data").is_dir():
            return candidate
    raise FileNotFoundError("Could not locate project root")


def read_text(path: Path) -> str:
    return path.read_text(encoding="utf-8") if path.is_file() else ""


def check_game_core_pure_kotlin(game_core_dir: Path) -> tuple[bool, list[str]]:
    violations: list[str] = []
    kotlin_root = game_core_dir / "src" / "main" / "kotlin"
    for file_path in kotlin_root.rglob("*.kt"):
        for line_number, line in enumerate(file_path.read_text(encoding="utf-8").splitlines(), start=1):
            if ANDROID_IMPORT_PATTERN.match(line):
                violations.append(f"{file_path}:{line_number}: {line.strip()}")
    return len(violations) == 0, violations


def main() -> int:
    project_root = find_project_root()
    android_root = project_root / "android-app"
    app_dir = android_root / "app"
    game_core_dir = android_root / "game-core"
    output_path = project_root / "data" / "persistence_validation.txt"

    checks: list[tuple[str, bool, str]] = []

    def record(name: str, passed: bool, detail: str = "") -> None:
        checks.append((name, passed, detail))

    libs = read_text(android_root / "gradle" / "libs.versions.toml")
    app_build = read_text(app_dir / "build.gradle.kts")
    game_core_build = read_text(game_core_dir / "build.gradle.kts")
    package_root = app_dir / "src" / "main" / "java" / "com" / "boardbanker" / "app"
    persistence_root = package_root / "persistence"

    record("Room dependency exists", "androidx-room-runtime" in libs and "room" in app_build.lower())
    record("Room KTX exists", "room-ktx" in libs)
    record("Room compiler via KSP exists", "ksp" in app_build and "room.compiler" in app_build)
    record("SavedGameEntity exists", (persistence_root / "entity" / "SavedGameEntity.kt").is_file())
    record("SavedGameDao exists", (persistence_root / "db" / "SavedGameDao.kt").is_file())
    record("BoardBankerDatabase exists", (persistence_root / "db" / "BoardBankerDatabase.kt").is_file())
    record("GameSessionRepository exists", (persistence_root / "repository" / "GameSessionRepository.kt").is_file())
    record("RoomGameSessionRepository exists", (persistence_root / "repository" / "RoomGameSessionRepository.kt").is_file())
    record(
        "GameSession serializer exists",
        (game_core_dir / "src" / "main" / "kotlin" / "com" / "boardbanker" / "core" / "persistence" / "GameSessionSerializer.kt").is_file(),
    )
    record(
        "schemaVersion exists",
        "CURRENT_VERSION" in read_text(game_core_dir / "src" / "main" / "kotlin" / "com" / "boardbanker" / "core" / "persistence" / "GameSessionSerializer.kt"),
    )
    record("no fallbackToDestructiveMigration", "fallbackToDestructiveMigration" not in app_build)
    record("Room only in app layer", "room" not in game_core_build.lower())
    pure_ok, violations = check_game_core_pure_kotlin(game_core_dir)
    record("game-core has no Android imports", pure_ok, "; ".join(violations))
    record("game-core does not depend on app", 'project(":app")' not in game_core_build)
    record(
        "Resume uses persisted availability",
        "savedGameStatus" in read_text(package_root / "ui" / "screens" / "home" / "HomeScreen.kt")
        or "hasResumableGame" in read_text(package_root / "ui" / "screens" / "home" / "HomeScreen.kt"),
    )
    record(
        "transient scanner state not in SavedGameEntity",
        "WAITING_FOR_PLAYER" not in read_text(persistence_root / "entity" / "SavedGameEntity.kt"),
    )
    record(
        "serialization round-trip tests exist",
        (game_core_dir / "src" / "test" / "kotlin" / "com" / "boardbanker" / "core" / "persistence" / "GameSessionSerializerTest.kt").is_file(),
    )
    record(
        "persistence architecture doc exists",
        (project_root / "docs" / "PERSISTENCE_ARCHITECTURE.md").is_file(),
    )

    passed = sum(1 for _, ok, _ in checks if ok)
    total = len(checks)
    lines = [
        "Persistence Validation",
        "====================",
        f"Passed: {passed} / {total}",
        "",
    ]
    for name, ok, detail in checks:
        status = "PASS" if ok else "FAIL"
        line = f"[{status}] {name}"
        if detail:
            line += f" — {detail}"
        lines.append(line)

    output_path.write_text("\n".join(lines) + "\n", encoding="utf-8")
    print("\n".join(lines))
    return 0 if passed == total else 1


if __name__ == "__main__":
    sys.exit(main())
