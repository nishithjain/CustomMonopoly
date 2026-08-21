#!/usr/bin/env python3
"""Validate data/banking_values.json and that the Android runtime copy exists."""

from __future__ import annotations

import json
import re
import sys
from pathlib import Path

EXPECTED = {
    "startingBalance": 1500,
    "goSalary": 200,
    "locationFee": 100,
    "jailReleaseFee": 100,
    "auctionBidIncrement": 20,
    "M50": 50,
    "M200": 200,
}


def find_project_root() -> Path:
    current = Path(__file__).resolve().parent
    for candidate in [current.parent, current.parent.parent]:
        if (candidate / "data").is_dir() and (candidate / "android-app").is_dir():
            return candidate
    raise FileNotFoundError("Could not locate project root")


def main() -> int:
    project_root = find_project_root()
    source = project_root / "data" / "banking_values.json"
    runtime = (
        project_root
        / "android-app"
        / "app"
        / "src"
        / "main"
        / "assets"
        / "game"
        / "banking_values.json"
    )
    report = project_root / "data" / "banking_values_validation.txt"
    problems: list[str] = []

    if not source.is_file():
        problems.append("data/banking_values.json is missing")
        data = {}
    else:
        data = json.loads(source.read_text(encoding="utf-8"))

    if data.get("schemaVersion") != 1:
        problems.append("schemaVersion must be 1")
    currency = data.get("currency") or {}
    if not isinstance(currency, dict):
        problems.append("currency is missing")
    else:
        if currency.get("code") != "M":
            problems.append("currency.code must be M for current edition")
        if currency.get("symbol") != "M":
            problems.append("currency.symbol must be M for current edition")
        if currency.get("scale") != 1:
            problems.append("currency.scale must be 1 for current edition")

    for key in ("startingBalance", "goSalary", "locationFee", "jailReleaseFee", "auctionBidIncrement"):
        value = data.get(key)
        if not isinstance(value, int) or value <= 0:
            problems.append(f"{key} must be an integer > 0")
        elif value != EXPECTED[key]:
            problems.append(f"{key}: expected {EXPECTED[key]}, found {value}")

    amounts = data.get("eventAmounts") or {}
    for key in ("M50", "M200"):
        value = amounts.get(key)
        if not isinstance(value, int) or value <= 0:
            problems.append(f"eventAmounts.{key} must be an integer > 0")
        elif value != EXPECTED[key]:
            problems.append(f"eventAmounts.{key}: expected {EXPECTED[key]}, found {value}")

    if not runtime.is_file():
        problems.append("Android runtime copy missing: android-app/app/src/main/assets/game/banking_values.json")

    engine_dir = project_root / "android-app" / "game-core" / "src" / "main" / "kotlin"
    hardcode_checks = [
        (
            engine_dir / "com/boardbanker/core/model/GameRulesConfig.kt",
            r'startingBalance", 1500\)',
            "GameRulesConfig must not fall back to hard-coded startingBalance 1500",
        ),
        (
            engine_dir / "com/boardbanker/core/model/GameRulesConfig.kt",
            r'goSalary", 200\)',
            "GameRulesConfig must not fall back to hard-coded goSalary 200",
        ),
        (
            engine_dir / "com/boardbanker/core/event/EventEngine.kt",
            r"rule\.amount \?: 50",
            "EventEngine must not hard-code Event M50 as rule.amount ?: 50",
        ),
        (
            engine_dir / "com/boardbanker/core/event/EventEngine.kt",
            r"rule\.amount \?: 200",
            "EventEngine must not hard-code Event M200 as rule.amount ?: 200",
        ),
        (
            engine_dir / "com/boardbanker/core/engine/DefaultGameEngine.kt",
            r"balance = 1500",
            "DefaultGameEngine must not assign balance = 1500",
        ),
        (
            project_root / "android-app/app/src/main/java/com/boardbanker/app/ui/screens/auction/AuctionScreen.kt",
            r"formatMoney\(20\)",
            "AuctionScreen must not hard-code formatMoney(20)",
        ),
    ]
    for path, pattern, message in hardcode_checks:
        if path.is_file() and re.search(pattern, path.read_text(encoding="utf-8")):
            problems.append(message)

    overall = "PASS" if not problems else "FAIL"
    lines = [
        "BANKING VALUES VALIDATION",
        "=========================",
        "",
        f"Source: {source.relative_to(project_root).as_posix()}",
        f"Runtime copy: {runtime.relative_to(project_root).as_posix()}",
        "",
        f"RESULT: {overall}",
        "",
    ]
    if problems:
        lines.append("Problems:")
        lines.extend(f"- {item}" for item in problems)
    else:
        lines.extend(
            [
                "startingBalance=1500",
                "goSalary=200",
                "locationFee=100",
                "jailReleaseFee=100",
                "auctionBidIncrement=20",
                "eventAmounts.M50=50",
                "eventAmounts.M200=200",
                "Android runtime copy present",
                "No selected engine/UI hard-code fallbacks found",
            ]
        )
    report.write_text("\n".join(lines) + "\n", encoding="utf-8")
    print(report.read_text(encoding="utf-8"), end="")
    return 0 if not problems else 1


if __name__ == "__main__":
    raise SystemExit(main())
