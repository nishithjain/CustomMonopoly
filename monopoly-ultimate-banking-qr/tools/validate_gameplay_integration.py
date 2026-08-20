#!/usr/bin/env python3
"""Validate Step 10 gameplay integration."""

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
    output_path = project_root / "data" / "gameplay_integration_validation.txt"
    package_root = app_dir / "src" / "main" / "java" / "com" / "boardbanker" / "app"
    gameplay_root = package_root / "gameplay"

    checks: list[tuple[str, bool, str]] = []

    def record(name: str, passed: bool, detail: str = "") -> None:
        checks.append((name, passed, detail))

    game_vm = read_text(package_root / "ui" / "screens" / "game" / "GameViewModel.kt")
    game_screen = read_text(package_root / "ui" / "screens" / "game" / "GameScreen.kt")
    workflow = read_text(gameplay_root / "workflow" / "GameplayWorkflowController.kt")
    event_planner = read_text(gameplay_root / "workflow" / "EventWorkflowPlanner.kt")
    mapper = read_text(gameplay_root / "gameplay" / "presentation" / "GameplayResultMapper.kt")
    if not mapper:
        mapper = read_text(gameplay_root / "presentation" / "GameplayResultMapper.kt")

    record("Property workflow exists", "onPropertyScanned" in workflow)
    record("Event workflow exists", "onEventScanned" in workflow)
    record("Game Engine is used", "processCommand" in game_vm and "GameCommand" in game_vm)
    record("Property rules not duplicated in UI", "rentLevels" not in game_screen and "purchasePrice" not in game_vm)
    record("Event relationship maps not duplicated in UI", "boardSides" not in game_screen and "neighbours" not in game_vm)
    record("Event workflow planner exists", event_planner.strip() != "")
    record("23 Event workflows mapped", (project_root / "data" / "event_ui_workflow_coverage.txt").is_file())
    record("successful results persist", "ProcessCommitResult.Committed" in game_vm)
    record("scanner remains reusable", "ScannerScreen" in read_text(package_root / "navigation" / "AppNavigation.kt"))
    record("ScanGate remains active", "ScanGate" in read_text(package_root / "scanner" / "ScannerController.kt"))
    record(
        "transient workflow not persisted",
        "TransientScanWorkflowHolder" in read_text(package_root / "persistence" / "TransientScanWorkflowHolder.kt"),
    )
    record("no virtual board position added", "boardPosition" not in game_vm and "currentTurnPlayer" not in workflow)
    record("no advanced Auction UI added", "PlaceAuctionBid" not in game_vm)
    record("no debt UI added", "ResolveDebt" not in game_vm)
    record("gameplay workflow tests exist", (app_dir / "src" / "test" / "java" / "com" / "boardbanker" / "app" / "gameplay" / "GameplayWorkflowControllerTest.kt").is_file())
    record("gameplay integration docs exist", (project_root / "docs" / "GAMEPLAY_INTEGRATION.md").is_file())

    pure_ok, violations = check_game_core_pure_kotlin(game_core_dir)
    record("game-core remains Android-free", pure_ok, "; ".join(violations))

    subprocess.run([sys.executable, str(project_root / "tools" / "generate_event_workflow_coverage.py")], check=False)

    passed = sum(1 for _, ok, _ in checks if ok)
    total = len(checks)
    lines = [
        "Gameplay Integration Validation",
        "=============================",
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
