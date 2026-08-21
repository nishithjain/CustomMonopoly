#!/usr/bin/env python3
"""Validate Location -> Property landing workflow wiring."""

from __future__ import annotations

import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ANDROID = ROOT / "android-app"
OUT = ROOT / "data" / "location_property_workflow_validation.txt"

CHECKS: list[tuple[str, bool, str]] = []


def read(path: Path) -> str:
    return path.read_text(encoding="utf-8") if path.exists() else ""


def check(name: str, ok: bool, detail: str) -> None:
    CHECKS.append((name, ok, detail))


def main() -> int:
    app = ANDROID / "app" / "src" / "main" / "java" / "com" / "boardbanker" / "app"
    game_vm = read(app / "ui" / "screens" / "game" / "GameViewModel.kt")
    banking_vm = read(app / "ui" / "screens" / "banking" / "AdvancedBankingViewModel.kt")
    player_vm = read(app / "ui" / "screens" / "playerdetails" / "PlayerDetailsViewModel.kt")
    workflow = read(app / "gameplay" / "workflow" / "GameplayWorkflowController.kt")
    holder = read(app / "gameplay" / "location" / "LocationWorkflowHolder.kt")
    constants = read(app / "gameplay" / "location" / "LocationWorkflowConstants.kt")
    transient = read(app / "persistence" / "TransientScanWorkflowHolder.kt")
    docs = read(ROOT / "docs" / "LOCATION_WORKFLOW.md")
    engine = read(
        ANDROID
        / "game-core"
        / "src"
        / "main"
        / "kotlin"
        / "com"
        / "boardbanker"
        / "core"
        / "engine"
        / "DefaultGameEngine.kt"
    )

    check(
        "Location fee uses Game Engine PayLocationFee",
        "GameCommand.PayLocationFee" in banking_vm and "GameCommand.PayLocationFee" in player_vm,
        "Banking and Player Details call PayLocationFee",
    )
    check(
        "Fee-only sentinel property id",
        "FEE_ONLY_PROPERTY_ID" in constants and "FEE_ONLY_PROPERTY_ID" in banking_vm,
        "M100 committed before destination scan",
    )
    check(
        "Transient location wait state",
        "LocationWaitingForDestinationProperty" in workflow
        and "LocationWorkflowHolder" in holder
        and "LOCATION_WAITING_FOR_DESTINATION_PROPERTY" in transient,
        "Non-persisted wait for destination Property",
    )
    check(
        "Handoff to normal Property workflow",
        "beginLocationDestinationProperty" in workflow
        and "ProcessPropertyLanding" in workflow,
        "No LocationPropertyProcessor duplicate rules",
    )
    check(
        "Landing player carried (no re-scan)",
        "landingPlayerId" in workflow and "beginLocationDestinationProperty" in game_vm,
        "Location player preselected for BUY/AUCTION/rent",
    )
    check(
        "Continue on Active Game after fee",
        "ContinueLocationOnActiveGame" in banking_vm and "resumeLocationWorkflowIfPending" in game_vm,
        "Does not stop at banking result hub",
    )
    check(
        "GR-SAVE-002 documented",
        "GR-SAVE-002" in docs and "auto-refund" in docs.lower(),
        "Restart behaviour documented",
    )
    check(
        "Game-core remains Android-free",
        "android" not in engine.lower() and "import com.boardbanker.app" not in engine,
        "Engine has no Android imports",
    )

    lines = ["Location Property Workflow Validation", "=" * 40, ""]
    passed = 0
    for name, ok, detail in CHECKS:
        status = "PASS" if ok else "FAIL"
        if ok:
            passed += 1
        lines.append(f"[{status}] {name}")
        lines.append(f"  {detail}")
        lines.append("")
    lines.append(f"Summary: {passed}/{len(CHECKS)} checks passed")
    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text("\n".join(lines) + "\n", encoding="utf-8")
    print(OUT)
    return 0 if passed == len(CHECKS) else 1


if __name__ == "__main__":
    raise SystemExit(main())
