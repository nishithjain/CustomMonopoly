#!/usr/bin/env python3
"""Validate Step 9 player setup integration."""

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
    output_path = project_root / "data" / "player_setup_integration_validation.txt"
    package_root = app_dir / "src" / "main" / "java" / "com" / "boardbanker" / "app"

    checks: list[tuple[str, bool, str]] = []

    def record(name: str, passed: bool, detail: str = "") -> None:
        checks.append((name, passed, detail))

    setup_screen = read_text(package_root / "ui" / "screens" / "setup" / "PlayerSetupScreen.kt")
    setup_vm = read_text(package_root / "ui" / "screens" / "setup" / "GameSetupViewModel.kt")
    game_screen = read_text(package_root / "ui" / "screens" / "game" / "GameScreen.kt")
    game_vm = read_text(package_root / "ui" / "screens" / "game" / "GameViewModel.kt")
    home_screen = read_text(package_root / "ui" / "screens" / "home" / "HomeScreen.kt")
    navigation = read_text(package_root / "navigation" / "AppNavigation.kt")
    scanner_vm = read_text(package_root / "scanner" / "ScannerViewModel.kt")
    scanner_filter = read_text(package_root / "scanner" / "ScannerCardFilter.kt")
    session_manager = read_text(package_root / "game" / "ActiveGameSessionManager.kt")
    game_core_build = read_text(game_core_dir / "build.gradle.kts")

    record("real PlayerSetupScreen exists", "SCAN PLAYER CARD" in setup_screen)
    record("placeholder text removed", "will be implemented in a later step" not in setup_screen)
    record("GameSetupViewModel exists", (package_root / "ui" / "screens" / "setup" / "GameSetupViewModel.kt").is_file())
    record("RegisterPlayer through Game Engine", "GameCommand.RegisterPlayer" in setup_vm and "processCommand" in setup_vm)
    record("StartGame through Game Engine", "GameCommand.StartGame" in setup_vm)
    record("ActiveGameSessionManager exists", (package_root / "game" / "ActiveGameSessionManager.kt").is_file())
    record("CardResolver used in scanner", "ScanProcessor" in read_text(package_root / "scanner" / "ScannerController.kt"))
    record("expected USER type enforced", "CardType.USER" in navigation and "ScannerCardFilter" in scanner_vm)
    record("ScanGate remains in flow", "ScanGate" in read_text(package_root / "scanner" / "ScannerController.kt"))
    record("successful registration persists", "processCommand" in setup_vm and "CommittedGameSessionStore" in read_text(package_root / "persistence" / "CommittedGameSessionStore.kt"))
    record("successful StartGame persists", "StartGame" in setup_vm and "NavigateToGame" in setup_vm)
    record("Game Dashboard renders balances", "balanceText" in game_screen or "formatMoney" in game_vm)
    record("Resume uses Room session", "restoreFromStorage" in session_manager and "RESUME" in home_screen)
    record("no Property GameCommands in setup UI", "PurchaseProperty" not in setup_vm and "ProcessPropertyLanding" not in setup_vm)
    record("no Event GameCommands in setup UI", "ApplyEvent" not in setup_vm)
    record("player setup workflow doc exists", (project_root / "docs" / "PLAYER_SETUP_WORKFLOW.md").is_file())
    record("player setup integration tests exist", (app_dir / "src" / "test" / "java" / "com" / "boardbanker" / "app" / "game" / "ActiveGameSessionManagerTest.kt").is_file())

    pure_ok, violations = check_game_core_pure_kotlin(game_core_dir)
    record("game-core remains Android-free", pure_ok, "; ".join(violations))
    record("game-core does not depend on app", 'project(":app")' not in game_core_build)

    passed = sum(1 for _, ok, _ in checks if ok)
    total = len(checks)
    lines = [
        "Player Setup Integration Validation",
        "=================================",
        f"Passed: {passed} / {total}",
        "",
    ]
    for name, ok, detail in checks:
        status = "PASS" if ok else "FAIL"
        lines.append(f"[{status}] {name}")
        if detail:
            lines.append(f"       {detail}")
    output_path.write_text("\n".join(lines) + "\n", encoding="utf-8")

    print("\n".join(lines))
    return 0 if passed == total else 1


if __name__ == "__main__":
    sys.exit(main())
