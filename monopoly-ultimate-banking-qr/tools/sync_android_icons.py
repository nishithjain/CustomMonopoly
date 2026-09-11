#!/usr/bin/env python3
"""Copy authoritative icon PNGs from Resources/Common/Icons/ into Android drawable resources."""

from __future__ import annotations

import json
import shutil
import sys
from pathlib import Path

PLAYER_ICON_MAPPINGS = [
    ("USR_01", "Car", "Resources/Common/Icons/Car.png", "player_car.png"),
    ("USR_02", "Helicopter", "Resources/Common/Icons/Helicopter.png", "player_helicopter.png"),
    ("USR_03", "Ship", "Resources/Common/Icons/Ship.png", "player_ship.png"),
    ("USR_04", "Aeroplane", "Resources/Common/Icons/Aeroplane.png", "player_aeroplane.png"),
]

COMMON_ICON_MAPPINGS = [
    ("ABANDON_GAME", "Abandon Game", "Resources/Common/Icons/abandon_game.png", "common_abandon_game.png"),
    ("ACCEPT_CARD", "Accept Card", "Resources/Common/Icons/accept_card.png", "common_accept_card.png"),
    ("BACK", "Back", "Resources/Common/Icons/back.png", "common_back.png"),
    ("BANK", "Bank", "Resources/Common/Icons/bank.png", "common_bank.png"),
    ("CHECK", "Check", "Resources/Common/Icons/check.png", "common_check.png"),
    ("COLLECT_GO", "Collect GO", "Resources/Common/Icons/collect_go.png", "common_collect_go.png"),
    ("COLLECT", "Collect", "Resources/Common/Icons/collect.png", "common_collect.png"),
    ("CANCEL", "Cancel", "Resources/Common/Icons/cancel.png", "common_cancel.png"),
    ("END_GAME", "End Game", "Resources/Common/Icons/end_game.png", "common_end_game.png"),
    ("END_TURN", "End Turn", "Resources/Common/Icons/end_turn.png", "common_end_turn.png"),
    ("GAME_STATUS", "Game Status", "Resources/Common/Icons/gmae_status.png", "common_game_status.png"),
    ("JAIL", "Jail", "Resources/Common/Icons/jail.png", "common_jail.png"),
    ("LOCATION", "Location", "Resources/Common/Icons/location.png", "common_location.png"),
    ("RECENT_BANKING", "Recent Banking", "Resources/Common/Icons/recent_banking.png", "common_recent_banking.png"),
    ("SCAN_CARD", "Scan Card", "Resources/Common/Icons/scan_card.png", "common_scan_card.png"),
    ("START_GAME", "Start Game", "Resources/Common/Icons/start_game.png", "common_start_game.png"),
    ("RETURN_HOME", "Return Home", "Resources/Common/Icons/return_home.png", "common_return_home.png"),
    ("UNDO_LAST_ACTION", "Undo Last Action", "Resources/Common/Icons/undo_last_action.png", "common_undo_last_action.png"),
    ("AUCTION", "Auction", "Resources/Common/Icons/auction.png", "common_auction.png"),
    ("BUY_PROPERTY", "Buy Property", "Resources/Common/Icons/buy_property.png", "common_buy_property.png"),
    ("CAMERA", "Camera", "Resources/Common/Icons/camera.png", "common_camera.png"),
    ("COLOR_SET_COMPLETE", "Color Set Complete", "Resources/Common/Icons/color_set_complete.png", "common_color_set_complete.png"),
    ("CURRENT_TURN", "Current Turn", "Resources/Common/Icons/current_turn.png", "common_current_turn.png"),
    ("DEBT", "Debt", "Resources/Common/Icons/debt.png", "common_debt.png"),
    ("DICE", "Dice", "Resources/Common/Icons/dice.png", "common_dice.png"),
    ("JACKPOT", "Jackpot", "Resources/Common/Icons/jackpot.png", "common_jackpot.png"),
    ("PENALTY", "Penalty", "Resources/Common/Icons/penalty.png", "common_penalty.png"),
    ("DO_NOTHING", "Do Nothing", "Resources/Common/Icons/do_nothing.png", "common_do_nothing.png"),
    ("ENERGY_GRID", "Energy Grid", "Resources/Common/Icons/energy_grid.png", "common_energy_grid.png"),
    ("ERROR", "Error", "Resources/Common/Icons/error.png", "common_error.png"),
    ("EVENT_CARD", "Event Card", "Resources/Common/Icons/event_card.png", "common_event_card.png"),
    ("LUCKY_DRAW", "Lucky Draw", "Resources/Common/Icons/lucky_draw.png", "common_lucky_draw.png"),
    ("MONEY_TRANSFER", "Money Transfer", "Resources/Common/Icons/money_transfer.png", "common_money_transfer.png"),
    ("PLAYER_DETAILS", "Player Details", "Resources/Common/Icons/player_details.png", "common_player_details.png"),
    ("PROPERTY", "Property", "Resources/Common/Icons/property.png", "common_property.png"),
    ("RENT", "Rent", "Resources/Common/Icons/rent.png", "common_rent.png"),
    ("RENT_DECREASE", "Rent Decrease", "Resources/Common/Icons/rent_decrease.png", "common_rent_decrease.png"),
    ("RENT_INCREASE", "Rent Increase", "Resources/Common/Icons/rent_increase.png", "common_rent_increase.png"),
    ("RESUME_GAME", "Resume Game", "Resources/Common/Icons/resume_game.png", "common_resume_game.png"),
]

MANIFEST_NAME = "android_player_icon_manifest.json"
COMMON_MANIFEST_NAME = "android_common_icon_manifest.json"
EXPECTED_COMMON_ICON_COUNT = len(COMMON_ICON_MAPPINGS)


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
    raise FileNotFoundError("Could not locate workspace root containing Resources/Common/Icons and android-app/")


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
    destination_dir = project_root / "android-app" / "app" / "src" / "main" / "res" / "drawable"
    destination_dir.mkdir(parents=True, exist_ok=True)
    data_dir = project_root / "data"
    player_manifest_path = data_dir / MANIFEST_NAME
    common_manifest_path = data_dir / COMMON_MANIFEST_NAME

    problems: list[str] = []
    player_manifest: dict[str, dict] = {}
    common_manifest: dict[str, dict] = {}

    print(f"Workspace root: {workspace_root}")
    print(f"Destination:    {destination_dir}")
    print("Copied:")

    for player_id, name, source_rel, dest_name in PLAYER_ICON_MAPPINGS:
        source = workspace_root / source_rel
        if not source.is_file():
            problems.append(f"Missing source icon: {source_rel}")
            continue
        destination = destination_dir / dest_name
        shutil.copy2(source, destination)
        drawable_name = dest_name.removesuffix(".png")
        player_manifest[player_id] = {
            "playerId": player_id,
            "name": name,
            "sourceIconPath": source_rel.replace("\\", "/"),
            "drawableResource": drawable_name,
            "runtimePath": f"res/drawable/{dest_name}",
        }
        print(f"  {player_id} ({name}) -> res/drawable/{dest_name}")

    for icon_id, name, source_rel, dest_name in COMMON_ICON_MAPPINGS:
        source = workspace_root / source_rel
        if not source.is_file():
            problems.append(f"Missing source icon: {source_rel}")
            continue
        destination = destination_dir / dest_name
        shutil.copy2(source, destination)
        drawable_name = dest_name.removesuffix(".png")
        common_manifest[icon_id] = {
            "iconId": icon_id,
            "name": name,
            "sourceIconPath": source_rel.replace("\\", "/"),
            "drawableResource": drawable_name,
            "runtimePath": f"res/drawable/{dest_name}",
        }
        print(f"  {icon_id} ({name}) -> res/drawable/{dest_name}")

    if problems:
        print("\nFAIL:", file=sys.stderr)
        for problem in problems:
            print(f"  - {problem}", file=sys.stderr)
        return 1

    if len(player_manifest) != 4:
        print(f"Expected 4 player icons, copied {len(player_manifest)}.", file=sys.stderr)
        return 1
    if len(common_manifest) != EXPECTED_COMMON_ICON_COUNT:
        print(
            f"Expected {EXPECTED_COMMON_ICON_COUNT} common icons, copied {len(common_manifest)}.",
            file=sys.stderr,
        )
        return 1

    data_dir.mkdir(parents=True, exist_ok=True)
    player_manifest_path.write_text(
        json.dumps(
            {
                "schemaVersion": 1,
                "generatedBy": "tools/sync_android_icons.py",
                "players": player_manifest,
            },
            indent=2,
        )
        + "\n",
        encoding="utf-8",
    )
    common_manifest_path.write_text(
        json.dumps(
            {
                "schemaVersion": 1,
                "generatedBy": "tools/sync_android_icons.py",
                "icons": common_manifest,
            },
            indent=2,
        )
        + "\n",
        encoding="utf-8",
    )

    print("\nGENERATED RUNTIME ICONS — DO NOT EDIT DIRECTLY")
    print("Authoritative source: Resources/Common/Icons/")
    print(f"Player icons:  {len(player_manifest)}")
    print(f"Common icons:  {len(common_manifest)}")
    print(f"TOTAL:         {len(player_manifest) + len(common_manifest)}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
