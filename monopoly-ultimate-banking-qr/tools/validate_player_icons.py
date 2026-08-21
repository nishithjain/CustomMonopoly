#!/usr/bin/env python3
"""Validate generated Android player icon resources and registry coverage."""

from __future__ import annotations

import json
import sys
from pathlib import Path

REPORT_NAME = "player_icon_validation.txt"
MANIFEST_NAME = "android_player_icon_manifest.json"
EXPECTED = {
    "USR_01": ("Car", "player_car.png"),
    "USR_02": ("Helicopter", "player_helicopter.png"),
    "USR_03": ("Ship", "player_ship.png"),
    "USR_04": ("Aeroplane", "player_aeroplane.png"),
}


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
    tools_dir = Path(__file__).resolve().parent
    workspace_root = find_workspace_root()
    project_root = find_project_root(workspace_root)
    report_path = project_root / "data" / REPORT_NAME
    manifest_path = project_root / "data" / MANIFEST_NAME
    drawable_dir = project_root / "android-app" / "app" / "src" / "main" / "res" / "drawable"
    app_root = project_root / "android-app" / "app" / "src" / "main" / "java" / "com" / "boardbanker" / "app"
    game_core = project_root / "android-app" / "game-core" / "src"

    problems: list[str] = []

    icons_dir = workspace_root / "Resources" / "Common" / "Icons"
    if not icons_dir.is_dir():
        problems.append("Resources/Common/Icons directory missing")
    for icon_name in ["Car.png", "Helicopter.png", "Ship.png", "Aeroplane.png"]:
        if not (icons_dir / icon_name).is_file():
            problems.append(f"Missing source icon: Resources/Common/Icons/{icon_name}")

    if not manifest_path.is_file():
        problems.append(f"Missing manifest: data/{MANIFEST_NAME}")
        manifest = {}
    else:
        manifest = json.loads(manifest_path.read_text(encoding="utf-8")).get("players", {})

    for player_id, (name, drawable_file) in EXPECTED.items():
        drawable_path = drawable_dir / drawable_file
        if not drawable_path.is_file():
            problems.append(f"Missing Android drawable: res/drawable/{drawable_file}")
        entry = manifest.get(player_id)
        if entry is None:
            problems.append(f"Missing manifest entry for {player_id}")
        elif entry.get("drawableResource") != drawable_file.removesuffix(".png"):
            problems.append(f"{player_id} manifest drawable mismatch")

    registry_path = app_root / "player" / "PlayerIconRegistry.kt"
    identity_path = app_root / "ui" / "components" / "PlayerIdentity.kt"
    if not registry_path.is_file():
        problems.append("PlayerIconRegistry.kt missing")
    if not identity_path.is_file():
        problems.append("PlayerIdentity component missing")

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
        f"Source icon files: {sum(1 for n in ['Car.png','Helicopter.png','Ship.png','Aeroplane.png'] if (icons_dir / n).is_file())} / 4",
        f"Android drawable resources: {sum(1 for _, (_, f) in EXPECTED.items() if (drawable_dir / f).is_file())} / 4",
        f"Manifest entries: {len(manifest)} / 4",
        f"PlayerIconRegistry: {'PASS' if registry_path.is_file() else 'FAIL'}",
        f"PlayerIdentity component: {'PASS' if identity_path.is_file() else 'FAIL'}",
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
        lines.append("- Reusable PlayerIdentity component exists")
        lines.append("- game-core contains no Android image resources")
        lines.append("- No icon data added to GameSession")

    report_path.write_text("\n".join(lines) + "\n", encoding="utf-8")
    print("\n".join(lines))
    print(f"\nReport written to: {report_path}")
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main())
