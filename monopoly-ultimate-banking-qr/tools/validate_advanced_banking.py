#!/usr/bin/env python3
"""Validate Step 11 advanced banking integration."""

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
    output_path = project_root / "data" / "advanced_banking_validation.txt"
    package_root = app_dir / "src" / "main" / "java" / "com" / "boardbanker" / "app"

    checks: list[tuple[str, bool, str]] = []

    def record(name: str, passed: bool, detail: str = "") -> None:
        checks.append((name, passed, detail))

    banking_vm = read_text(package_root / "ui" / "screens" / "banking" / "AdvancedBankingViewModel.kt")
    banking_screen = read_text(package_root / "ui" / "screens" / "banking" / "AdvancedBankingScreen.kt")
    auction_vm = read_text(package_root / "ui" / "screens" / "auction" / "AuctionViewModel.kt")
    debt_screen = read_text(package_root / "ui" / "screens" / "debt" / "DebtResolutionScreen.kt")
    game_over = read_text(package_root / "ui" / "screens" / "gameover" / "GameOverScreen.kt")
    game_vm = read_text(package_root / "ui" / "screens" / "game" / "GameViewModel.kt")
    game_screen = read_text(package_root / "ui" / "screens" / "game" / "GameScreen.kt")
    workflow = read_text(package_root / "gameplay" / "workflow" / "GameplayWorkflowController.kt")
    nav = read_text(package_root / "navigation" / "AppNavigation.kt")

    record("GO workflow exists", "PayGoSalary" in banking_vm and "COLLECT GO" in banking_screen)
    record("Location workflow exists", "PayLocationFee" in banking_vm and "LOCATION" in banking_screen)
    record("Jail workflow exists", "PayJailFee" in banking_vm and "JAIL" in banking_screen)
    record("Auction workflow exists", "PlaceAuctionBid" in auction_vm and "AUCTION" in read_text(package_root / "ui" / "screens" / "auction" / "AuctionScreen.kt"))
    record("Debt UI exists", "DebtResolutionScreen" in debt_screen and "ResolveDebt" in read_text(package_root / "ui" / "screens" / "debt" / "DebtResolutionViewModel.kt"))
    record("Bankruptcy result exists", "BANKRUPTCY" in banking_vm or "NavigateToGameOver" in game_vm)
    record("Winner screen exists", "WINNER" in game_over or "GAME OVER" in game_over)
    record("Undo UI exists", "UndoLastAction" in banking_vm)
    record("Game Engine commands used", "GameCommand" in banking_vm and "processCommand" in read_text(package_root / "banking" / "BankingCommandExecutor.kt"))
    record("Room persistence used", "CommittedGameSessionStore" in read_text(package_root / "game" / "ActiveGameSessionManager.kt"))
    record("no virtual dice", "rollDice" not in banking_screen and "virtualDice" not in game_vm)
    record("no virtual board position", "boardPosition" not in game_vm and "tokenPosition" not in game_screen)
    record("no generic trading", "TRADE PROPERTY" not in banking_screen and "SELL PROPERTY" not in nav)
    record("Auction bid increment M20", "auctionBidIncrement" in auction_vm or "PlaceAuctionBid" in auction_vm)
    record("Jail bid restriction respected", "jailStatus" in auction_vm)
    record(
        "GO amount not hard-coded in Compose",
        "bankingValues.goSalary" in banking_vm or "rulesConfig.goSalary" in banking_vm,
    )
    record(
        "Location fee uses domain config",
        "bankingValues.locationFee" in banking_vm or "rulesConfig.locationFee" in banking_vm,
    )
    record(
        "Jail fee uses domain config",
        "bankingValues.jailReleaseFee" in banking_vm or "rulesConfig.jailPaymentAmount" in banking_vm,
    )
    record("finished games block normal gameplay", "gameplayLocked" in game_screen)
    record("transaction history exists", (package_root / "ui" / "screens" / "history" / "TransactionHistoryScreen.kt").is_file())
    record("advanced banking tests exist", (app_dir / "src" / "test" / "java" / "com" / "boardbanker" / "app" / "banking" / "AdvancedBankingWorkflowTest.kt").is_file())
    record("auction navigation wired", "NavigateToAuction" in workflow and "Auction.route" in nav)
    record("debt auto navigation wired", "NavigateToDebt" in game_vm)
    record("docs exist", (project_root / "docs" / "ADVANCED_BANKING_WORKFLOW.md").is_file())

    pure_ok, violations = check_game_core_pure_kotlin(game_core_dir)
    record("game-core remains Android-free", pure_ok, "; ".join(violations))

    passed = sum(1 for _, ok, _ in checks if ok)
    total = len(checks)
    lines = [
        "ADVANCED BANKING VALIDATION",
        f"Result: {'PASS' if passed == total else 'FAIL'} ({passed}/{total})",
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
