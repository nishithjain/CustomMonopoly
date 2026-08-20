#!/usr/bin/env python3
"""Validate banking button UI semantics and architecture constraints."""

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


def validate(project_root: Path) -> tuple[list[str], dict]:
    problems: list[str] = []
    stats = {
        "screens_using_bar": 0,
        "auction_middle": False,
        "global_middle": False,
    }

    app_root = project_root / "android-app" / "app" / "src" / "main" / "java" / "com" / "boardbanker" / "app"
    component = app_root / "ui" / "components" / "BankingActionBar.kt"
    labels = app_root / "ui" / "components" / "BankingActionLabels.kt"
    docs = project_root / "docs" / "BANKING_BUTTON_UI.md"

    for required in (component, labels, docs):
        if not required.is_file():
            problems.append(f"Missing required file: {required.relative_to(project_root)}")

    component_text = read_text(component)
    labels_text = read_text(labels)

    if "fun BankingActionBar" not in component_text:
        problems.append("BankingActionBar composable missing")
    if "middleLabel" not in component_text or "onMiddle" not in component_text:
        problems.append("Optional middle action API missing")
    if "confirmLabel" not in component_text or "cancelLabel" not in component_text:
        problems.append("Confirm/Cancel semantic API missing")
    if BankingActionLabels_checks(labels_text, problems) is False:
        pass

    screen_files = [
        app_root / "ui" / "screens" / "game" / "GameScreen.kt",
        app_root / "ui" / "screens" / "auction" / "AuctionScreen.kt",
        app_root / "ui" / "screens" / "banking" / "AdvancedBankingScreen.kt",
        app_root / "ui" / "screens" / "setup" / "PlayerSetupScreen.kt",
        app_root / "ui" / "screens" / "debt" / "DebtResolutionScreen.kt",
    ]
    for screen in screen_files:
        text = read_text(screen)
        if "BankingActionBar" in text:
            stats["screens_using_bar"] += 1

    auction_text = read_text(app_root / "ui" / "screens" / "auction" / "AuctionScreen.kt")
    if 'BankingActionLabels.middle("BID' not in auction_text and "middleLabel" not in auction_text:
        problems.append("Auction screen does not use M/BID middle action")
    else:
        stats["auction_middle"] = True

    home_text = read_text(app_root / "ui" / "screens" / "home" / "HomeScreen.kt")
    game_hub_text = read_text(app_root / "ui" / "screens" / "game" / "GameScreen.kt")
    if "BankingActionLabels.middle" in home_text:
        stats["global_middle"] = True
        problems.append("Home screen exposes global middle M button")
    if "BankingActionLabels.middle" in game_hub_text and "AuctionScreen" not in game_hub_text:
        # GameScreen should not use middle action globally
        if "middleLabel" in game_hub_text:
            problems.append("Game dashboard exposes middle M button")

    debt_text = read_text(app_root / "ui" / "screens" / "debt" / "DebtResolutionScreen.kt")
    if "cancelLabel" in debt_text and "onCancel" in debt_text and "result == null" in debt_text:
        problems.append("Debt resolution exposes cancel despite mandatory consequence rules")

    if "playError()" in component_text:
        problems.append("BankingActionBar must not call playError directly")

    game_core = project_root / "android-app" / "game-core" / "src" / "main" / "kotlin"
    for path in game_core.rglob("*.kt"):
        for line in read_text(path).splitlines():
            if ANDROID_IMPORT_PATTERN.match(line):
                problems.append(f"Android import in game-core: {path.name}")
                break

    if "commandInFlight" not in read_text(app_root / "ui" / "screens" / "game" / "GameScreen.kt"):
        problems.append("GameScreen may have lost double-submit protection wiring")

    tests = project_root / "android-app" / "app" / "src" / "test" / "java" / "com" / "boardbanker" / "app" / "ui" / "components"
    if not (tests / "BankingActionBarTest.kt").is_file():
        problems.append("Missing BankingActionBarTest.kt")

    return problems, stats


def BankingActionLabels_checks(text: str, problems: list[str]) -> bool:
    for token in ("CONFIRM_SYMBOL", "MIDDLE_SYMBOL", "CANCEL_SYMBOL", "fun confirm", "fun middle", "fun cancel"):
        if token not in text:
            problems.append(f"BankingActionLabels missing {token}")
            return False
    return True


def write_report(project_root: Path, problems: list[str], stats: dict) -> Path:
    output = project_root / "data" / "banking_button_ui_validation.txt"
    result = "PASS" if not problems else "FAIL"
    lines = [
        "BANKING BUTTON UI VALIDATION",
        "============================",
        "",
        f"Screens using BankingActionBar: {stats['screens_using_bar']}",
        f"Auction M/BID control: {'YES' if stats['auction_middle'] else 'NO'}",
        f"Global middle M on hub screens: {'YES' if stats['global_middle'] else 'NO'}",
        "",
        f"RESULT: {result}",
    ]
    if problems:
        lines.extend(["", "Problems:", "---------"])
        lines.extend(f"- {problem}" for problem in problems)
    output.write_text("\n".join(lines) + "\n", encoding="utf-8")
    print(f"Wrote {output}")
    return output


def main() -> int:
    project_root = find_project_root()
    problems, stats = validate(project_root)
    write_report(project_root, problems, stats)

    if problems:
        print(f"Validation FAILED with {len(problems)} problem(s).")
        for problem in problems:
            print(f"  - {problem}")
        return 1

    print("Validation PASSED.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
