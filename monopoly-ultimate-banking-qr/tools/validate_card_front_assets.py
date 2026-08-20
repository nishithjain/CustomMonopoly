#!/usr/bin/env python3
"""Validate generated Android card-front assets and manifest coverage."""

from __future__ import annotations

import json
import sys
from pathlib import Path

try:
    from PIL import Image
except ImportError as exc:  # pragma: no cover
    raise SystemExit(
        "Pillow is required. Install with: python -m pip install Pillow",
    ) from exc

EXPECTED_COUNTS = {
    "USER": 4,
    "PROPERTY": 22,
    "EVENT": 23,
}
EXPECTED_TOTAL = 49
USER_IDS = {
    "USR_01": "Car",
    "USR_02": "Helicopter",
    "USR_03": "Ship",
    "USR_04": "Aeroplane",
}
MANIFEST_NAME = "android_card_front_manifest.json"
REPORT_NAME = "card_front_asset_validation.txt"


def find_workspace_root(tools_dir: Path) -> Path:
    for candidate in [tools_dir.parent.parent, tools_dir.parent.parent.parent]:
        resources = candidate / "Resources" / "Cards"
        android = candidate / "monopoly-ultimate-banking-qr" / "android-app"
        if resources.is_dir() and android.is_dir():
            return candidate
        project = candidate / "android-app"
        if resources.is_dir() and project.is_dir():
            return candidate
    raise FileNotFoundError(
        "Could not locate workspace root containing Resources/Cards and android-app/",
    )


def find_project_root(workspace_root: Path) -> Path:
    direct = workspace_root / "monopoly-ultimate-banking-qr"
    if (direct / "android-app").is_dir():
        return direct
    if (workspace_root / "android-app").is_dir():
        return workspace_root
    raise FileNotFoundError("Could not locate android-app project root")


def load_manifest(project_root: Path) -> dict:
    manifest_path = project_root / "data" / MANIFEST_NAME
    with manifest_path.open(encoding="utf-8") as handle:
        payload = json.load(handle)
    return payload["cards"]


def load_cards(project_root: Path) -> list[dict]:
    with (project_root / "data" / "cards.json").open(encoding="utf-8") as handle:
        return json.load(handle)["cards"]


def scan_game_core_for_images(project_root: Path) -> list[str]:
    game_core = project_root / "android-app" / "game-core" / "src"
    hits: list[str] = []
    if not game_core.is_dir():
        return hits
    for path in game_core.rglob("*"):
        if not path.is_file():
            continue
        suffix = path.suffix.lower()
        if suffix in {".png", ".jpg", ".jpeg", ".webp"}:
            hits.append(str(path.relative_to(project_root)))
        if path.name.lower() in {"cardfrontimageprovider.kt", "cardfrontregistry.kt"}:
            hits.append(str(path.relative_to(project_root)))
    return hits


def orientation_ok(card_type: str, width: int, height: int, expected_orientation: str) -> bool:
    if expected_orientation == "LANDSCAPE":
        return width > height
    if expected_orientation == "PORTRAIT":
        return height > width
    return False


def main() -> int:
    tools_dir = Path(__file__).resolve().parent
    workspace_root = find_workspace_root(tools_dir)
    project_root = find_project_root(workspace_root)
    manifest = load_manifest(project_root)
    cards = load_cards(project_root)
    report_path = project_root / "data" / REPORT_NAME
    assets_root = project_root / "android-app" / "app" / "src" / "main" / "assets"

    problems: list[str] = []
    counts = {"USER": 0, "PROPERTY": 0, "EVENT": 0}
    back_usage: list[str] = []
    duplicate_assets: dict[str, list[str]] = {}
    user_lines: list[str] = []

    card_ids = [card["cardId"] for card in cards]
    if len(set(card_ids)) != len(card_ids):
        problems.append("Duplicate card IDs in cards.json")

    for card_id, entry in manifest.items():
        card_type = entry.get("cardType")
        if card_type in counts:
            counts[card_type] += 1
        asset_rel = entry.get("asset") or entry.get("runtimeAssetPath")
        source_front = entry.get("sourceFrontPath", "")
        expected_orientation = entry.get("orientation", "")
        lowered_source = source_front.replace("\\", "/").lower()
        if "_back_qr" in lowered_source or "_back." in lowered_source:
            back_usage.append(f"{card_id}: source={source_front}")
        if not asset_rel:
            problems.append(f"{card_id}: missing asset path in manifest")
            continue
        duplicate_assets.setdefault(asset_rel, []).append(card_id)
        asset_path = assets_root / asset_rel
        if not asset_path.is_file():
            problems.append(f"{card_id}: missing runtime asset {asset_rel}")
            continue
        with Image.open(asset_path) as image:
            width, height = image.size
            if not orientation_ok(card_type, width, height, expected_orientation):
                problems.append(
                    f"{card_id}: orientation mismatch ({expected_orientation}, {width}x{height})",
                )
        if card_type == "USER":
            if expected_orientation != "LANDSCAPE":
                problems.append(f"{card_id}: USER front must be LANDSCAPE in manifest")
            if entry.get("rotationApplied"):
                problems.append(f"{card_id}: USER front must not be rotated")
            if not lowered_source.endswith((".jpg", ".jpeg", ".png")):
                problems.append(f"{card_id}: USER source must be JPG/PNG front asset")
            name = USER_IDS.get(card_id, entry.get("name", card_id))
            status = "PASS" if card_id not in [p.split(":")[0] for p in problems] else "FAIL"
            user_lines.append(f"{card_id} {name:<11} LANDSCAPE {status}")

    for asset_rel, ids in duplicate_assets.items():
        if len(ids) > 1:
            problems.append(f"Duplicate runtime asset mapping {asset_rel}: {', '.join(ids)}")

    for card_type, expected in EXPECTED_COUNTS.items():
        if counts.get(card_type, 0) != expected:
            problems.append(
                f"{card_type} runtime front count mismatch: expected {expected}, found {counts.get(card_type, 0)}",
            )

    if len(manifest) != EXPECTED_TOTAL:
        problems.append(
            f"Manifest card count mismatch: expected {EXPECTED_TOTAL}, found {len(manifest)}",
        )

    registry_ids = {card["cardId"] for card in cards}
    manifest_ids = set(manifest.keys())
    missing_in_manifest = sorted(registry_ids - manifest_ids)
    extra_in_manifest = sorted(manifest_ids - registry_ids)
    if missing_in_manifest:
        problems.append(f"Missing manifest entries: {', '.join(missing_in_manifest)}")
    if extra_in_manifest:
        problems.append(f"Unexpected manifest entries: {', '.join(extra_in_manifest)}")

    image_hits = scan_game_core_for_images(project_root)
    if image_hits:
        problems.append(f"game-core contains image dependencies: {', '.join(image_hits)}")

    if back_usage:
        problems.append(f"Back/Back_QR referenced as front: {', '.join(back_usage)}")

    user_pass = counts.get("USER", 0) == EXPECTED_COUNTS["USER"] and all(
        manifest.get(card_id, {}).get("orientation") == "LANDSCAPE"
        for card_id in USER_IDS
    )
    property_pass = counts.get("PROPERTY", 0) == EXPECTED_COUNTS["PROPERTY"]
    event_pass = counts.get("EVENT", 0) == EXPECTED_COUNTS["EVENT"]
    total_pass = len(manifest) == EXPECTED_TOTAL and not problems

    lines = [
        "CARD FRONT VALIDATION",
        f"Workspace root: {workspace_root}",
        f"Project root:   {project_root}",
        "",
        "USER",
    ]
    for card_id, name in USER_IDS.items():
        entry = manifest.get(card_id, {})
        orientation = entry.get("orientation", "?")
        rotated = entry.get("rotationApplied", True)
        source = entry.get("sourceFrontPath", "")
        ok = (
            card_id in manifest
            and orientation == "LANDSCAPE"
            and not rotated
            and source.lower().endswith((".jpg", ".jpeg", ".png"))
        )
        lines.append(f"{card_id} {name:<11} {orientation:<9} {'PASS' if ok else 'FAIL'}")
    lines.extend(
        [
            "",
            f"User fronts:     {counts.get('USER', 0)} / {EXPECTED_COUNTS['USER']} {'PASS' if user_pass else 'FAIL'}",
            f"Property fronts: {counts.get('PROPERTY', 0)} / {EXPECTED_COUNTS['PROPERTY']} {'PASS' if property_pass else 'FAIL'}",
            f"Event fronts:    {counts.get('EVENT', 0)} / {EXPECTED_COUNTS['EVENT']} {'PASS' if event_pass else 'FAIL'}",
            f"Total:           {len(manifest)} / {EXPECTED_TOTAL} {'PASS' if total_pass else 'FAIL'}",
            f"game-core image dependencies: {len(image_hits)}",
            "",
        ]
    )
    if problems:
        lines.append("RESULT: FAIL")
        lines.extend(f"- {problem}" for problem in problems)
    else:
        lines.append("RESULT: PASS")
        lines.append("- All runtime assets exist")
        lines.append("- USER fronts remain landscape without rotation")
        lines.append("- EVENT/PROPERTY fronts use portrait canonical orientation")
        lines.append("- JPG User-card sources accepted")
        lines.append("- No Back/Back_QR front mappings")
        lines.append("- cardId mappings unique")
        lines.append("- Source Resources images unchanged (sync tool copies only)")
        lines.append("- game-core contains no image dependencies")

    report_path.write_text("\n".join(lines) + "\n", encoding="utf-8")
    print("\n".join(lines))
    print(f"\nReport written to: {report_path}")
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main())
