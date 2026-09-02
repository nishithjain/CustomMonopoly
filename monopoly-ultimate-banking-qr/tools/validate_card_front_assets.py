#!/usr/bin/env python3
"""Validate edition-aware Android card-front assets and manifests."""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

try:
    from PIL import Image
except ImportError as exc:  # pragma: no cover
    raise SystemExit(
        "Pillow is required. Install with: python -m pip install Pillow",
    ) from exc

COMMON_MANIFEST_NAME = "android_card_front_manifest.json"
REPORT_NAME = "card_front_asset_validation.txt"
CARD_TYPE_FOLDERS = {
    "PROPERTY": "property",
    "EVENT": "event",
}


def find_workspace_root(tools_dir: Path) -> Path:
    for candidate in [tools_dir.parent.parent, tools_dir.parent.parent.parent]:
        resources = candidate / "Resources" / "Common"
        android = candidate / "monopoly-ultimate-banking-qr" / "android-app"
        if resources.is_dir() and android.is_dir():
            return candidate
        project = candidate / "android-app"
        if resources.is_dir() and project.is_dir():
            return candidate
    raise FileNotFoundError(
        "Could not locate workspace root containing Resources/Common and android-app/",
    )


def find_project_root(workspace_root: Path) -> Path:
    direct = workspace_root / "monopoly-ultimate-banking-qr"
    if (direct / "android-app").is_dir():
        return direct
    if (workspace_root / "android-app").is_dir():
        return workspace_root
    raise FileNotFoundError("Could not locate android-app project root")


def load_json(path: Path) -> dict:
    with path.open(encoding="utf-8") as handle:
        return json.load(handle)


def discover_edition_ids(project_root: Path, edition_filter: str | None) -> list[str]:
    if edition_filter:
        return [edition_filter]
    index_path = project_root / "data" / "editions" / "index.json"
    payload = load_json(index_path)
    return [entry["editionId"] for entry in payload["editions"] if entry.get("enabled", True)]


def load_edition_index(project_root: Path) -> dict[str, dict]:
    index_path = project_root / "data" / "editions" / "index.json"
    payload = load_json(index_path)
    return {entry["editionId"]: entry for entry in payload["editions"]}


def load_registry_cards(project_root: Path) -> list[dict]:
    return load_json(project_root / "data" / "common" / "card_registry.json")["cards"]


def load_edition_cards(project_root: Path, edition_id: str) -> list[dict]:
    edition = load_json(project_root / "data" / "editions" / edition_id / "edition.json")
    properties = load_json(
        project_root / "data" / "editions" / edition_id / edition["data"]["properties"],
    )["properties"]
    events = load_json(
        project_root / "data" / "editions" / edition_id / edition["data"]["events"],
    )["events"]
    cards = []
    for item in properties:
        cards.append(
            {
                "cardId": item["propertyId"],
                "cardType": "PROPERTY",
                "name": item["name"],
                "frontAsset": item.get("frontAsset"),
            },
        )
    for item in events:
        cards.append(
            {
                "cardId": item["eventId"],
                "cardType": "EVENT",
                "name": item["name"],
                "frontAsset": item.get("frontAsset"),
            },
        )
    return cards


def orientation_ok(card_type: str, width: int, height: int, expected_orientation: str) -> bool:
    if expected_orientation == "LANDSCAPE":
        return width > height
    if expected_orientation == "PORTRAIT":
        return height > width
    return False


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
    return hits


def validate_manifest(
    project_root: Path,
    assets_root: Path,
    manifest_path: Path,
    expected_edition_id: str,
    expected_cards: list[dict],
    artwork_status: str,
    problems: list[str],
) -> dict[str, int]:
    counts = {"USER": 0, "PROPERTY": 0, "EVENT": 0}
    if not manifest_path.is_file():
        if artwork_status == "READY":
            problems.append(
                f"{expected_edition_id}: missing manifest at {manifest_path.relative_to(project_root)}",
            )
        return counts

    payload = load_json(manifest_path)
    if payload.get("editionId") != expected_edition_id:
        problems.append(
            f"{expected_edition_id}: manifest editionId '{payload.get('editionId')}' mismatch",
        )
    manifest = payload.get("cards", {})
    duplicate_assets: dict[str, list[str]] = {}

    for card_id, entry in manifest.items():
        card_type = entry.get("cardType")
        if card_type in counts:
            counts[card_type] += 1
        asset_rel = entry.get("asset") or entry.get("runtimeAssetPath")
        source_front = entry.get("sourceFrontPath", "")
        expected_orientation = entry.get("orientation", "")
        lowered_source = source_front.replace("\\", "/").lower()
        if "_back_qr" in lowered_source or "_back." in lowered_source:
            problems.append(
                f"{expected_edition_id}/{card_type}/{card_id}: back asset referenced as front",
            )
        if not asset_rel:
            problems.append(f"{expected_edition_id}/{card_type}/{card_id}: missing asset path")
            continue
        duplicate_assets.setdefault(asset_rel, []).append(card_id)
        asset_path = assets_root / asset_rel
        if not asset_path.is_file():
            problems.append(
                f"{expected_edition_id}/{card_type}/{card_id}: missing runtime asset {asset_rel}",
            )
            continue
        with Image.open(asset_path) as image:
            width, height = image.size
            if not orientation_ok(card_type, width, height, expected_orientation):
                problems.append(
                    f"{expected_edition_id}/{card_type}/{card_id}: orientation mismatch "
                    f"({expected_orientation}, {width}x{height})",
                )

    for asset_rel, ids in duplicate_assets.items():
        if len(ids) > 1:
            problems.append(
                f"{expected_edition_id}: duplicate runtime asset mapping {asset_rel}: {', '.join(ids)}",
            )

    expected_by_id = {card["cardId"]: card for card in expected_cards}
    manifest_ids = set(manifest.keys())
    expected_ids = set(expected_by_id.keys())
    if artwork_status == "READY":
        missing = sorted(expected_ids - manifest_ids)
        if missing:
            problems.append(
                f"{expected_edition_id}: missing manifest entries: {', '.join(missing)}",
            )
    extra = sorted(manifest_ids - expected_ids)
    if extra:
        problems.append(
            f"{expected_edition_id}: unexpected manifest entries: {', '.join(extra)}",
        )

    for card_id, entry in manifest.items():
        expected = expected_by_id.get(card_id)
        if expected is None:
            continue
        if entry.get("cardType") != expected["cardType"]:
            problems.append(
                f"{expected_edition_id}/{expected['cardType']}/{card_id}: "
                f"manifest type '{entry.get('cardType')}' != registry '{expected['cardType']}'",
            )

    return counts


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--edition", help="Validate only the specified edition id")
    args = parser.parse_args()

    tools_dir = Path(__file__).resolve().parent
    workspace_root = find_workspace_root(tools_dir)
    project_root = find_project_root(workspace_root)
    assets_root = project_root / "android-app" / "app" / "src" / "main" / "assets"
    report_path = project_root / "data" / REPORT_NAME
    problems: list[str] = []
    lines = [
        "CARD FRONT VALIDATION",
        f"Workspace root: {workspace_root}",
        f"Project root:   {project_root}",
        "",
    ]

    user_cards = [card for card in load_registry_cards(project_root) if card["cardType"] == "USER"]
    common_counts = validate_manifest(
        project_root,
        assets_root,
        assets_root / "cards" / "common" / COMMON_MANIFEST_NAME,
        "common",
        user_cards,
        "READY",
        problems,
    )
    lines.append("COMMON USER")
    lines.append(
        f"User fronts: {common_counts.get('USER', 0)} / {len(user_cards)}",
    )

    edition_ids = discover_edition_ids(project_root, args.edition)
    edition_index = load_edition_index(project_root)
    for edition_id in edition_ids:
        edition = load_json(project_root / "data" / "editions" / edition_id / "edition.json")
        artwork_status = edition.get("artworkStatus", "READY")
        enabled = edition_index.get(edition_id, {}).get("enabled", True)
        if enabled and artwork_status == "INCOMPLETE":
            problems.append(
                f"{edition_id}: enabled production edition has artworkStatus={artwork_status}; required artwork is incomplete",
            )
        expected_cards = load_edition_cards(project_root, edition_id)
        counts = validate_manifest(
            project_root,
            assets_root,
            assets_root / "cards" / "editions" / edition_id / COMMON_MANIFEST_NAME,
            edition_id,
            expected_cards,
            "READY" if enabled else artwork_status,
            problems,
        )
        lines.append("")
        lines.append(f"EDITION {edition_id} ({artwork_status})")
        lines.append(
            f"Property fronts: {counts.get('PROPERTY', 0)} / "
            f"{sum(1 for card in expected_cards if card['cardType'] == 'PROPERTY')}",
        )
        lines.append(
            f"Event fronts:    {counts.get('EVENT', 0)} / "
            f"{sum(1 for card in expected_cards if card['cardType'] == 'EVENT')}",
        )

    image_hits = scan_game_core_for_images(project_root)
    if image_hits:
        problems.append(f"game-core contains image dependencies: {', '.join(image_hits)}")

    lines.append("")
    if problems:
        lines.append("RESULT: FAIL")
        lines.extend(f"- {problem}" for problem in problems)
    else:
        lines.append("RESULT: PASS")
        lines.append("- Edition-aware manifests and runtime assets validated")

    report_path.write_text("\n".join(lines) + "\n", encoding="utf-8")
    print("\n".join(lines))
    print(f"\nReport written to: {report_path}")
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main())
