#!/usr/bin/env python3
"""Validate generated Android player and common icon resources and registry coverage."""

from __future__ import annotations

import json
import sys
from pathlib import Path

REPORT_NAME = "player_icon_validation.txt"
PLAYER_MANIFEST_NAME = "android_player_icon_manifest.json"
COMMON_MANIFEST_NAME = "android_common_icon_manifest.json"
EXPECTED_PLAYERS = {
    "USR_01": ("Car", "player_car.png"),
    "USR_02": ("Helicopter", "player_helicopter.png"),
    "USR_03": ("Ship", "player_ship.png"),
    "USR_04": ("Aeroplane", "player_aeroplane.png"),
}
EXPECTED_COMMON = {
    "ABANDON_GAME": ("Abandon Game", "common_abandon_game.png"),
    "ACCEPT_CARD": ("Accept Card", "common_accept_card.png"),
    "BACK": ("Back", "common_back.png"),
    "BANK": ("Bank", "common_bank.png"),
    "CHECK": ("Check", "common_check.png"),
    "COLLECT_GO": ("Collect GO", "common_collect_go.png"),
    "COLLECT": ("Collect", "common_collect.png"),
    "CANCEL": ("Cancel", "common_cancel.png"),
    "END_GAME": ("End Game", "common_end_game.png"),
    "END_TURN": ("End Turn", "common_end_turn.png"),
    "GAME_STATUS": ("Game Status", "common_game_status.png"),
    "JAIL": ("Jail", "common_jail.png"),
    "LOCATION": ("Location", "common_location.png"),
    "RECENT_BANKING": ("Recent Banking", "common_recent_banking.png"),
    "RETURN_HOME": ("Return Home", "common_return_home.png"),
    "SCAN_CARD": ("Scan Card", "common_scan_card.png"),
    "START_GAME": ("Start Game", "common_start_game.png"),
    "DICE": ("Dice", "common_dice.png"),
    "DO_NOTHING": ("Do Nothing", "common_do_nothing.png"),
    "ENERGY_GRID": ("Energy Grid", "common_energy_grid.png"),
    "UNDO_LAST_ACTION": ("Undo Last Action", "common_undo_last_action.png"),
}
EXPECTED_COMMON_SOURCE_FILES = [
    "abandon_game.png",
    "accept_card.png",
    "back.png",
    "bank.png",
    "cancel.png",
    "check.png",
    "collect.png",
    "collect_go.png",
    "do_nothing.png",
    "end_game.png",
    "end_turn.png",
    "gmae_status.png",
    "jail.png",
    "location.png",
    "recent_banking.png",
    "return_home.png",
    "scan_card.png",
    "start_game.png",
    "undo_last_action.png",
]


def find_workspace_root() -> Path:
    tools_dir = Path(__file__).resolve().parent
    for candidate in [tools_dir.parent.parent, tools_dir.parent.parent.parent]:
        icons = candidate / "Resources" / "Common" / "Icons"
        android = candidate / "monopoly-ultimate-banking-qr" / "android-app"
        if icons.is_dir() and android.is_dir():
            return candidate
        project = candidate / "android-app"
        if icons.is_dir() and project.is_dir():
            return candidate
    raise FileNotFoundError("Could not locate workspace root")


def find_project_root(workspace_root: Path) -> Path:
    direct = workspace_root / "monopoly-ultimate-banking-qr"
    if (direct / "android-app").is_dir():
        return direct
    if (workspace_root / "android-app").is_dir():
        return workspace_root
    raise FileNotFoundError("Could not locate android-app project root")


def main() -> int:
    workspace_root = find_workspace_root()
    project_root = find_project_root(workspace_root)
    report_path = project_root / "data" / REPORT_NAME
    player_manifest_path = project_root / "data" / PLAYER_MANIFEST_NAME
    common_manifest_path = project_root / "data" / COMMON_MANIFEST_NAME
    drawable_dir = project_root / "android-app" / "app" / "src" / "main" / "res" / "drawable"
    app_root = project_root / "android-app" / "app" / "src" / "main" / "java" / "com" / "boardbanker" / "app"
    game_core = project_root / "android-app" / "game-core" / "src"

    problems: list[str] = []

    icons_dir = workspace_root / "Resources" / "Common" / "Icons"
    if not icons_dir.is_dir():
        problems.append("Resources/Common/Icons directory missing")
    for icon_name in ["Car.png", "Helicopter.png", "Ship.png", "Aeroplane.png"] + EXPECTED_COMMON_SOURCE_FILES:
        if not (icons_dir / icon_name).is_file():
            problems.append(f"Missing source icon: Resources/Common/Icons/{icon_name}")

    if not player_manifest_path.is_file():
        problems.append(f"Missing manifest: data/{PLAYER_MANIFEST_NAME}")
        player_manifest = {}
    else:
        player_manifest = json.loads(player_manifest_path.read_text(encoding="utf-8")).get("players", {})

    if not common_manifest_path.is_file():
        problems.append(f"Missing manifest: data/{COMMON_MANIFEST_NAME}")
        common_manifest = {}
    else:
        common_manifest = json.loads(common_manifest_path.read_text(encoding="utf-8")).get("icons", {})

    for player_id, (name, drawable_file) in EXPECTED_PLAYERS.items():
        drawable_path = drawable_dir / drawable_file
        if not drawable_path.is_file():
            problems.append(f"Missing Android drawable: res/drawable/{drawable_file}")
        entry = player_manifest.get(player_id)
        if entry is None:
            problems.append(f"Missing manifest entry for {player_id}")
        elif entry.get("drawableResource") != drawable_file.removesuffix(".png"):
            problems.append(f"{player_id} manifest drawable mismatch")

    for icon_id, (name, drawable_file) in EXPECTED_COMMON.items():
        drawable_path = drawable_dir / drawable_file
        if not drawable_path.is_file():
            problems.append(f"Missing Android drawable: res/drawable/{drawable_file}")
        entry = common_manifest.get(icon_id)
        if entry is None:
            problems.append(f"Missing common manifest entry for {icon_id}")
        elif entry.get("drawableResource") != drawable_file.removesuffix(".png"):
            problems.append(f"{icon_id} manifest drawable mismatch")

    registry_path = app_root / "player" / "PlayerIconRegistry.kt"
    common_registry_path = app_root / "player" / "CommonIconRegistry.kt"
    identity_path = app_root / "ui" / "components" / "PlayerIdentity.kt"
    display_identity_path = app_root / "ui" / "components" / "DisplayIdentity.kt"
    common_ui_icon_path = app_root / "ui" / "components" / "CommonUiIcon.kt"
    if not registry_path.is_file():
        problems.append("PlayerIconRegistry.kt missing")
    if not common_registry_path.is_file():
        problems.append("CommonIconRegistry.kt missing")
    if not identity_path.is_file():
        problems.append("PlayerIdentity component missing")
    if not display_identity_path.is_file():
        problems.append("DisplayIdentity component missing")
    if not common_ui_icon_path.is_file():
        problems.append("CommonUiIcon component missing")

    image_hits: list[str] = []
    for path in game_core.rglob("*"):
        if path.is_file() and path.suffix.lower() in {".png", ".jpg", ".jpeg", ".webp"}:
            image_hits.append(str(path.relative_to(project_root)))
    if image_hits:
        problems.append(f"game-core contains image resources: {', '.join(image_hits)}")

    session_path = game_core / "main" / "kotlin" / "com" / "boardbanker" / "core" / "model" / "GameSession.kt"
    if session_path.is_file():
        session_text = session_path.read_text(encoding="utf-8")
        for forbidden in ["drawable", "iconPath", "bitmap", "playerIcon"]:
            if forbidden in session_text:
                problems.append(f"GameSession may contain icon persistence reference: {forbidden}")

    lines = [
        "PLAYER ICON VALIDATION",
        f"Workspace root: {workspace_root}",
        f"Project root:   {project_root}",
        "",
        f"Source icons directory: {'PASS' if icons_dir.is_dir() else 'FAIL'}",
        f"Source player icon files: {sum(1 for n in ['Car.png','Helicopter.png','Ship.png','Aeroplane.png'] if (icons_dir / n).is_file())} / 4",
        f"Source common icon files: {sum(1 for n in EXPECTED_COMMON_SOURCE_FILES if (icons_dir / n).is_file())} / {len(EXPECTED_COMMON_SOURCE_FILES)}",
        f"Android player drawables: {sum(1 for _, (_, f) in EXPECTED_PLAYERS.items() if (drawable_dir / f).is_file())} / 4",
        f"Android common drawables: {sum(1 for _, (_, f) in EXPECTED_COMMON.items() if (drawable_dir / f).is_file())} / {len(EXPECTED_COMMON)}",
        f"Player manifest entries: {len(player_manifest)} / 4",
        f"Common manifest entries: {len(common_manifest)} / {len(EXPECTED_COMMON)}",
        f"PlayerIconRegistry: {'PASS' if registry_path.is_file() else 'FAIL'}",
        f"CommonIconRegistry: {'PASS' if common_registry_path.is_file() else 'FAIL'}",
        f"PlayerIdentity component: {'PASS' if identity_path.is_file() else 'FAIL'}",
        f"DisplayIdentity component: {'PASS' if display_identity_path.is_file() else 'FAIL'}",
        f"CommonUiIcon component: {'PASS' if common_ui_icon_path.is_file() else 'FAIL'}",
        f"game-core image resources: {len(image_hits)}",
        "",
    ]
    if problems:
        lines.append("RESULT: FAIL")
        lines.extend(f"- {problem}" for problem in problems)
    else:
        lines.append("RESULT: PASS")
        lines.append("- All source and runtime icons present")
        lines.append("- USR_01..USR_04 mappings correct")
        lines.append("- Common UI icons mapped")
        lines.append("- Reusable PlayerIdentity and DisplayIdentity components exist")
        lines.append("- game-core contains no Android image resources")
        lines.append("- No icon data added to GameSession")

    report_path.write_text("\n".join(lines) + "\n", encoding="utf-8")
    print("\n".join(lines))
    print(f"\nReport written to: {report_path}")
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main())
