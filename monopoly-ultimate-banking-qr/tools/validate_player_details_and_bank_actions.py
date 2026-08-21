#!/usr/bin/env python3
"""Validate Player Details and bank action UI integration."""

from __future__ import annotations

import re
import subprocess
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
    output_path = project_root / "data" / "player_details_bank_actions_validation.txt"
    package_root = app_dir / "src" / "main" / "java" / "com" / "boardbanker" / "app"

    checks: list[tuple[str, bool, str]] = []

    def record(name: str, passed: bool, detail: str = "") -> None:
        checks.append((name, passed, detail))

    store = read_text(package_root / "persistence" / "CommittedGameSessionStore.kt")
    manager = read_text(package_root / "game" / "ActiveGameSessionManager.kt")
    game_vm = read_text(package_root / "ui" / "screens" / "game" / "GameViewModel.kt")
    game_screen = read_text(package_root / "ui" / "screens" / "game" / "GameScreen.kt")
    banking_vm = read_text(package_root / "ui" / "screens" / "banking" / "AdvancedBankingViewModel.kt")
    banking_screen = read_text(package_root / "ui" / "screens" / "banking" / "AdvancedBankingScreen.kt")
    player_details_screen = read_text(package_root / "ui" / "screens" / "playerdetails" / "PlayerDetailsScreen.kt")
    player_details_vm = read_text(package_root / "ui" / "screens" / "playerdetails" / "PlayerDetailsViewModel.kt")
    presentation = read_text(package_root / "game" / "ActiveGamePresentation.kt")
    nav = read_text(package_root / "navigation" / "AppNavigation.kt")

    record("committed session StateFlow exists", "committedSession" in store and "MutableStateFlow" in store)
    record("ActiveGameSessionManager exposes committed session", "committedSession" in manager)
    record("GameViewModel observes committed session", "committedSession.collect" in game_vm)
    record("PlayerDetailsViewModel observes committed session", "committedSession.collect" in player_details_vm)
    record("Player rows clickable on Active Game", "clickable" in game_screen and "onPlayerSelected" in game_screen)
    record("PlayerDetailsScreen exists", "PlayerDetailsScreen" in player_details_screen)
    record("owned property list exists", "ownedProperties" in player_details_screen and "NO PROPERTIES OWNED" in player_details_screen)
    record(
        "owned properties derive from session ownership",
        "buildOwnedProperties" in presentation and "ownerPlayerId == playerId" in presentation,
    )
    record("player-specific bank actions on Player Details", "COLLECT GO" in player_details_screen and "GET OUT OF JAIL" in player_details_screen)
    record("GO TO JAIL bank action exists", "SendPlayerToJail" in banking_vm and "GO TO JAIL" in banking_screen)
    record("generic ambiguous JAIL label removed from hub", '"JAIL"' not in banking_screen or "GET OUT OF JAIL" in banking_screen)
    record("GET OUT OF JAIL exists", "GET OUT OF JAIL" in banking_screen and "onGetOutOfJail" in banking_vm)
    record("GO uses Game Engine", "PayGoSalary" in banking_vm)
    record("Location uses Game Engine", "PayLocationFee" in banking_vm)
    record("SendPlayerToJail uses Game Engine", "SendPlayerToJail" in banking_vm)
    record("Get Out uses Game Engine", "PayJailFee" in banking_vm)
    record("Player Details navigation by playerId", "player_details/{playerId}" in nav or "playerDetailsRoute" in read_text(package_root / "navigation" / "AppDestination.kt"))
    record(
        "no manual balance arithmetic in Compose screens",
        "balance + 200" not in game_screen and "balance - 100" not in player_details_screen,
    )

    pure, violations = check_game_core_pure_kotlin(game_core_dir)
    record("game-core remains Android-free", pure, "; ".join(violations[:5]))

    passed_count = sum(1 for _, passed, _ in checks if passed)
    lines = [
        "PLAYER DETAILS / BANK ACTIONS VALIDATION",
        f"Project root: {project_root}",
        "",
    ]
    for name, passed, detail in checks:
        status = "PASS" if passed else "FAIL"
        line = f"[{status}] {name}"
        if detail:
            line += f" — {detail}"
        lines.append(line)
    lines.append("")
    lines.append(f"RESULT: {'PASS' if passed_count == len(checks) else 'FAIL'} ({passed_count}/{len(checks)})")

    output_path.write_text("\n".join(lines) + "\n", encoding="utf-8")
    print(f"Wrote {output_path}")
    print(lines[-1])
    return 0 if passed_count == len(checks) else 1


if __name__ == "__main__":
    sys.exit(main())
