#!/usr/bin/env python3
"""Copy authoritative player icon PNGs from Resources/Icons/ into Android drawable resources."""

from __future__ import annotations

import json
import shutil
import sys
from pathlib import Path

ICON_MAPPINGS = [
    ("USR_01", "Car", "Resources/Icons/Car.png", "player_car.png"),
    ("USR_02", "Helicopter", "Resources/Icons/Helicopter.png", "player_helicopter.png"),
    ("USR_03", "Ship", "Resources/Icons/Ship.png", "player_ship.png"),
    ("USR_04", "Aeroplane", "Resources/Icons/Aeroplane.png", "player_aeroplane.png"),
]

MANIFEST_NAME = "android_player_icon_manifest.json"


def find_workspace_root() -> Path:
    tools_dir = Path(__file__).resolve().parent
    for candidate in [tools_dir.parent.parent, tools_dir.parent.parent.parent]:
        icons = candidate / "Resources" / "Icons"
        android = candidate / "monopoly-ultimate-banking-qr" / "android-app"
        if icons.is_dir() and android.is_dir():
            return candidate
        project = candidate / "android-app"
        if icons.is_dir() and project.is_dir():
            return candidate
    raise FileNotFoundError("Could not locate workspace root containing Resources/Icons and android-app/")


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
    data_manifest_path = project_root / "data" / MANIFEST_NAME

    problems: list[str] = []
    manifest: dict[str, dict] = {}

    print(f"Workspace root: {workspace_root}")
    print(f"Destination:    {destination_dir}")
    print("Copied:")

    for player_id, name, source_rel, dest_name in ICON_MAPPINGS:
        source = workspace_root / source_rel
        if not source.is_file():
            problems.append(f"Missing source icon: {source_rel}")
            continue
        destination = destination_dir / dest_name
        shutil.copy2(source, destination)
        drawable_name = dest_name.removesuffix(".png")
        manifest[player_id] = {
            "playerId": player_id,
            "name": name,
            "sourceIconPath": source_rel.replace("\\", "/"),
            "drawableResource": drawable_name,
            "runtimePath": f"res/drawable/{dest_name}",
        }
        print(f"  {player_id} ({name}) -> res/drawable/{dest_name}")

    if problems:
        print("\nFAIL:", file=sys.stderr)
        for problem in problems:
            print(f"  - {problem}", file=sys.stderr)
        return 1

    if len(manifest) != 4:
        print(f"Expected 4 icons, copied {len(manifest)}.", file=sys.stderr)
        return 1

    payload = {
        "schemaVersion": 1,
        "generatedBy": "tools/sync_android_icons.py",
        "players": manifest,
    }
    data_manifest_path.parent.mkdir(parents=True, exist_ok=True)
    data_manifest_path.write_text(json.dumps(payload, indent=2) + "\n", encoding="utf-8")

    print("\nGENERATED RUNTIME ICONS — DO NOT EDIT DIRECTLY")
    print("Authoritative source: Resources/Icons/")
    print(f"Manifest: {data_manifest_path}")
    print(f"TOTAL: {len(manifest)} / 4")
    return 0


if __name__ == "__main__":
    sys.exit(main())
