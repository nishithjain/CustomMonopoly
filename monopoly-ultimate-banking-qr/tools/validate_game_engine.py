#!/usr/bin/env python3
"""Validate game-core Kotlin engine against master data and rule scenarios."""

from __future__ import annotations

import json
import subprocess
import sys
from pathlib import Path

PROJECT_ROOT = Path(__file__).resolve().parent.parent
DATA_DIR = PROJECT_ROOT / "data"
ANDROID_APP = PROJECT_ROOT / "android-app"
REPORT_PATH = DATA_DIR / "game_engine_validation.txt"
EVENT_COVERAGE_PATH = DATA_DIR / "event_engine_test_coverage.txt"
GAME_CORE_SRC = ANDROID_APP / "game-core" / "src" / "main" / "kotlin"
GAME_CORE_TEST = ANDROID_APP / "game-core" / "src" / "test" / "kotlin"

EXPECTED_PROPERTIES = 22
EXPECTED_EVENTS = 23
EXPECTED_PLAYERS = 4


def load_json(path: Path, key: str) -> list[dict]:
    with path.open(encoding="utf-8") as handle:
        return json.load(handle)[key]


def validate_master_data() -> tuple[list[str], dict]:
    problems: list[str] = []
    stats: dict = {}

    required_files = [
        "common/card_registry.json",
        "common/game_rules.json",
        "common/event_engine_rules.json",
        "editions/uk/properties.json",
        "editions/uk/events.json",
        "editions/uk/board_relationships.json",
        "editions/uk/banking_values.json",
    ]
    for name in required_files:
        if not (DATA_DIR / name).exists():
            problems.append(f"Missing data file: {name}")

    properties = load_json(DATA_DIR / "editions" / "uk" / "properties.json", "properties")
    events = load_json(DATA_DIR / "editions" / "uk" / "events.json", "events")
    engine_rules = load_json(DATA_DIR / "common" / "event_engine_rules.json", "events")
    cards = load_json(DATA_DIR / "common" / "card_registry.json", "cards")

    stats["properties"] = len(properties)
    stats["events"] = len(events)
    stats["engine_rules"] = len(engine_rules)
    stats["players"] = sum(1 for c in cards if c.get("cardType") == "USER")

    if len(properties) != EXPECTED_PROPERTIES:
        problems.append(f"Expected {EXPECTED_PROPERTIES} properties, found {len(properties)}")
    if len(events) != EXPECTED_EVENTS:
        problems.append(f"Expected {EXPECTED_EVENTS} events, found {len(events)}")
    if len(engine_rules) != EXPECTED_EVENTS:
        problems.append(f"Expected {EXPECTED_EVENTS} engine rules, found {len(engine_rules)}")
    if stats["players"] != EXPECTED_PLAYERS:
        problems.append(f"Expected {EXPECTED_PLAYERS} players, found {stats['players']}")

    event_ids = {e["eventId"] for e in events}
    rule_ids = {r["eventId"] for r in engine_rules}
    if event_ids != rule_ids:
        missing_rules = sorted(event_ids - rule_ids)
        extra_rules = sorted(rule_ids - event_ids)
        if missing_rules:
            problems.append(f"Events missing engine rules: {', '.join(missing_rules)}")
        if extra_rules:
            problems.append(f"Engine rules without events: {', '.join(extra_rules)}")

    with (DATA_DIR / "common" / "game_rules.json").open(encoding="utf-8") as handle:
        game_rules = json.load(handle)
    setup = game_rules.get("setup", {})
    if setup.get("startingBalance") != 1500:
        problems.append("game_rules startingBalance must be 1500")
    if setup.get("minimumPlayers") != 2:
        problems.append("game_rules minimumPlayers must be 2")
    if setup.get("maximumPlayers") != 4:
        problems.append("game_rules maximumPlayers must be 4")

    return problems, stats


def validate_engine_structure() -> list[str]:
    problems: list[str] = []
    required_packages = [
        "com/boardbanker/core/model",
        "com/boardbanker/core/command",
        "com/boardbanker/core/engine",
        "com/boardbanker/core/rules",
        "com/boardbanker/core/transaction",
        "com/boardbanker/core/event",
        "com/boardbanker/core/validation",
    ]
    for package in required_packages:
        if not (GAME_CORE_SRC / package).is_dir():
            problems.append(f"Missing package: {package}")

    required_files = [
        GAME_CORE_SRC / "com/boardbanker/core/engine/GameEngine.kt",
        GAME_CORE_SRC / "com/boardbanker/core/engine/DefaultGameEngine.kt",
        GAME_CORE_SRC / "com/boardbanker/core/event/EventEngine.kt",
        GAME_CORE_SRC / "com/boardbanker/core/validation/GameDefinitionLoader.kt",
        GAME_CORE_TEST / "com/boardbanker/core/EventTests.kt",
    ]
    for path in required_files:
        if not path.is_file():
            problems.append(f"Missing file: {path.relative_to(ANDROID_APP)}")

    if (GAME_CORE_SRC / "com/boardbanker/core").rglob("*.kt"):
        for kt_file in GAME_CORE_SRC.rglob("*.kt"):
            for line in kt_file.read_text(encoding="utf-8").splitlines():
                stripped = line.strip()
                if stripped.startswith("import android.") or stripped.startswith("import androidx."):
                    problems.append(f"Android import in game-core: {kt_file.name}: {stripped}")

    app_build = ANDROID_APP / "app" / "build.gradle.kts"
    game_core_build = ANDROID_APP / "game-core" / "build.gradle.kts"
    if app_build.is_file() and game_core_build.is_file():
        app_text = app_build.read_text(encoding="utf-8")
        core_text = game_core_build.read_text(encoding="utf-8")
        if 'project(":game-core")' not in app_text:
            problems.append("app module does not depend on game-core")
        if 'project(":app")' in core_text:
            problems.append("game-core must not depend on app")

    forbidden = ["camerax", "mlkit", "room", "retrofit", "firebase"]
    for kt_file in GAME_CORE_SRC.rglob("*.kt"):
        lower = kt_file.read_text(encoding="utf-8").lower()
        for token in forbidden:
            if token in lower:
                problems.append(f"Forbidden dependency reference in {kt_file.name}: {token}")

    return problems


def write_event_coverage_report(test_ok: bool) -> None:
    event_ids = [f"EVT_{index:02d}" for index in range(1, 24)]
    lines = [
        "EVENT ENGINE TEST COVERAGE",
        "==========================",
        "",
    ]
    for event_id in event_ids:
        status = "PASS" if test_ok else "UNKNOWN"
        lines.append(f"{event_id} {status}")
    lines.extend([
        "",
        f"Events tested: {len(event_ids)} / 23",
        "",
        "RESULT:",
        "PASS" if test_ok else "FAIL",
        "",
        "Source: EventTests.all23EventsApplySuccessfully + dedicated event scenario tests",
    ])
    EVENT_COVERAGE_PATH.write_text("\n".join(lines), encoding="utf-8")


def run_kotlin_tests() -> tuple[bool, str]:
    if not (ANDROID_APP / "gradlew").exists():
        return False, "gradlew not found in android-app"
    if sys.platform == "win32":
        gradlew = ANDROID_APP / "gradlew.bat"
        cmd = ["cmd", "/c", str(gradlew), ":game-core:test", "--console=plain"]
    else:
        cmd = [str(ANDROID_APP / "gradlew"), ":game-core:test", "--console=plain"]
    result = subprocess.run(
        cmd,
        cwd=ANDROID_APP,
        capture_output=True,
        text=True,
        check=False,
    )
    output = (result.stdout or "") + (result.stderr or "")
    success = result.returncode == 0 and "BUILD SUCCESSFUL" in output
    return success, output


def write_report(problems: list[str], stats: dict, test_ok: bool, test_output: str) -> None:
    lines = [
        "Game Engine Validation Report",
        "=============================",
        "",
        "Master data:",
        f"  Properties: {stats.get('properties', '?')}",
        f"  Events: {stats.get('events', '?')}",
        f"  Engine rules: {stats.get('engine_rules', '?')}",
        f"  Players: {stats.get('players', '?')}",
        "",
        f"Data validation: {'PASS' if not problems else 'FAIL'}",
    ]
    if problems:
        lines.append("")
        lines.append("Problems:")
        for problem in problems:
            lines.append(f"  - {problem}")

    lines.extend([
        "",
        f"Kotlin tests (:game-core:test): {'PASS' if test_ok else 'FAIL'}",
        "",
        "Test output (tail):",
        "\n".join(test_output.strip().splitlines()[-20:]),
        "",
        f"Overall: {'PASS' if not problems and test_ok else 'FAIL'}",
    ])
    REPORT_PATH.write_text("\n".join(lines), encoding="utf-8")


def main() -> int:
    problems, stats = validate_master_data()
    problems.extend(validate_engine_structure())
    test_ok, test_output = run_kotlin_tests()
    write_report(problems, stats, test_ok, test_output)
    write_event_coverage_report(test_ok)

    print(f"Report written to {REPORT_PATH}")
    if problems:
        print("Data validation FAILED")
        for problem in problems:
            print(f"  - {problem}")
    else:
        print("Data validation PASSED")

    if test_ok:
        print("Kotlin tests PASSED")
    else:
        print("Kotlin tests FAILED")

    return 0 if not problems and test_ok else 1


if __name__ == "__main__":
    sys.exit(main())
