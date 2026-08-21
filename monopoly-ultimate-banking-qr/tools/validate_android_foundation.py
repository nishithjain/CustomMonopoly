#!/usr/bin/env python3
"""Validate Android foundation scaffolding for Step 5."""

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


def check_game_core_pure_kotlin(game_core_dir: Path) -> tuple[bool, list[str]]:
    violations: list[str] = []
    kotlin_root = game_core_dir / "src" / "main" / "kotlin"
    if not kotlin_root.is_dir():
        return False, ["game-core Kotlin source directory missing"]
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
    data_dir = project_root / "data"
    docs_dir = project_root / "docs"

    checks: list[tuple[str, bool, str]] = []

    def record(name: str, passed: bool, detail: str = "") -> None:
        checks.append((name, passed, detail))

    record("android-app exists", android_root.is_dir())
    record(":app exists", app_dir.is_dir())
    record(":game-core exists", game_core_dir.is_dir())

    settings = (android_root / "settings.gradle.kts").read_text(encoding="utf-8") if (android_root / "settings.gradle.kts").is_file() else ""
    record("settings includes :app", ':app' in settings or '":app"' in settings)
    record("settings includes :game-core", ':game-core' in settings or '":game-core"' in settings)

    app_build = (app_dir / "build.gradle.kts").read_text(encoding="utf-8") if (app_dir / "build.gradle.kts").is_file() else ""
    game_core_build = (game_core_dir / "build.gradle.kts").read_text(encoding="utf-8") if (game_core_dir / "build.gradle.kts").is_file() else ""
    record("app depends on game-core", "project(\":game-core\")" in app_build)
    record("game-core does not depend on app", "project(\":app\")" not in game_core_build)

    pure_ok, violations = check_game_core_pure_kotlin(game_core_dir)
    record("game-core has no Android imports", pure_ok, "; ".join(violations))

    asset_dir = app_dir / "src" / "main" / "assets" / "game"
    asset_files = [
        "common/card_registry.json",
        "common/game_rules.json",
        "common/event_engine_rules.json",
        "editions/uk/edition.json",
        "editions/uk/properties.json",
        "editions/uk/events.json",
        "editions/uk/banking_values.json",
        "editions/uk/board_relationships.json",
        "editions/india/edition.json",
        "editions/india/properties.json",
        "editions/india/events.json",
        "editions/india/banking_values.json",
        "editions/india/board_relationships.json",
    ]
    for filename in asset_files:
        record(f"asset exists: {filename}", (asset_dir / filename).is_file())
        record(f"root source exists: {filename}", (data_dir / filename).is_file())

    package_root = app_dir / "src" / "main" / "java" / "com" / "boardbanker" / "app"
    record("MainActivity exists", (package_root / "MainActivity.kt").is_file())
    record("HomeScreen exists", (package_root / "ui" / "screens" / "home" / "HomeScreen.kt").is_file())
    record(
        "PlayerSetupScreen exists",
        (package_root / "ui" / "screens" / "setup" / "PlayerSetupScreen.kt").is_file(),
    )
    record("GameScreen exists", (package_root / "ui" / "screens" / "game" / "GameScreen.kt").is_file())
    record("ANDROID_ARCHITECTURE.md exists", (docs_dir / "ANDROID_ARCHITECTURE.md").is_file())
    record("sync_android_assets.py exists", (project_root / "tools" / "sync_android_assets.py").is_file())

    passed_count = sum(1 for _, passed, _ in checks if passed)
    total_count = len(checks)
    overall_pass = passed_count == total_count

    lines = [
        "ANDROID FOUNDATION VALIDATION",
        "=============================",
        "",
        f"Checks passed: {passed_count}/{total_count}",
        "",
        "RESULT:",
        "PASS" if overall_pass else "FAIL",
        "",
        "DETAILS:",
    ]
    for name, passed, detail in checks:
        status = "PASS" if passed else "FAIL"
        suffix = f" — {detail}" if detail and not passed else ""
        lines.append(f"- [{status}] {name}{suffix}")

    output = data_dir / "android_foundation_validation.txt"
    output.write_text("\n".join(lines) + "\n", encoding="utf-8")
    print(output.read_text(encoding="utf-8"))
    return 0 if overall_pass else 1


if __name__ == "__main__":
    sys.exit(main())
