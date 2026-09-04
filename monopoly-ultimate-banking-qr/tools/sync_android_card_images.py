#!/usr/bin/env python3
"""Generate edition-aware Android runtime card-front assets and manifests."""

from __future__ import annotations

import argparse
import json
import shutil
import sys
from pathlib import Path

try:
    from PIL import Image
except ImportError as exc:  # pragma: no cover
    raise SystemExit(
        "Pillow is required. Install with: python -m pip install Pillow",
    ) from exc

MAX_PORTRAIT_WIDTH = 1024
MAX_LANDSCAPE_WIDTH = 1024
COMMON_MANIFEST_NAME = "android_card_front_manifest.json"
FRONT_SUFFIXES = (".png", ".jpg", ".jpeg")
CARD_TYPE_FOLDERS = {
    "PROPERTY": "property",
    "EVENT": "event",
    "ENERGY_GRID": "energy-grid",
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
    if index_path.is_file():
        payload = load_json(index_path)
        return [entry["editionId"] for entry in payload["editions"]]
    editions_dir = project_root / "data" / "editions"
    return sorted(
        path.name
        for path in editions_dir.iterdir()
        if path.is_dir()
    )


def load_registry_cards(project_root: Path) -> list[dict]:
    payload = load_json(project_root / "data" / "common" / "card_registry.json")
    return payload["cards"]


def load_edition_manifest(project_root: Path, edition_id: str) -> dict:
    return load_json(project_root / "data" / "editions" / edition_id / "edition.json")


def load_edition_cards(project_root: Path, edition_id: str) -> list[dict]:
    edition = load_edition_manifest(project_root, edition_id)
    properties = load_json(
        project_root / "data" / "editions" / edition_id / edition["data"]["properties"],
    )["properties"]
    events = load_json(
        project_root / "data" / "editions" / edition_id / edition["data"]["events"],
    )["events"]
    cards: list[dict] = []
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
    energy_grids_file = edition.get("data", {}).get("energyGrids")
    if energy_grids_file:
        energy_grids = load_json(
            project_root / "data" / "editions" / edition_id / energy_grids_file,
        )["energyGrids"]
        for item in energy_grids:
            cards.append(
                {
                    "cardId": item["energyGridId"],
                    "cardType": "ENERGY_GRID",
                    "name": item["name"],
                    "frontAsset": item.get("frontAsset"),
                },
            )
    return cards


def resolve_source_front_path(
    workspace_root: Path,
    card: dict,
    front_rel: str,
) -> Path | None:
    source_path = workspace_root / front_rel
    if source_path.is_file():
        return source_path
    if card.get("cardType") != "ENERGY_GRID":
        return None
    parent = source_path.parent
    if not parent.is_dir():
        return None
    grid_id = str(card.get("cardId", "")).lower()
    prefix = grid_id.replace("eng_", "eng_")
    for candidate in sorted(parent.glob(f"{prefix}*.png")):
        lowered = candidate.name.lower()
        if "_back" in lowered or "_qr" in lowered:
            continue
        return candidate
    return None


def validate_front_path(front_rel: str, *, allow_energy_grid_alias: bool = False) -> None:
    lowered = front_rel.replace("\\", "/").lower()
    if "_back_qr" in lowered or lowered.endswith("_back.png") or lowered.endswith("_back.jpg"):
        raise ValueError(f"Back/QR asset must not be used as front: {front_rel}")
    if "_front" not in lowered and not allow_energy_grid_alias:
        raise ValueError(f"Front asset must contain '_Front' marker: {front_rel}")
    if not lowered.endswith(FRONT_SUFFIXES):
        raise ValueError(
            f"Front asset must be PNG or JPG ({', '.join(FRONT_SUFFIXES)}): {front_rel}",
        )


def runtime_asset_rel(card_type: str, edition_id: str, card_id: str) -> str:
    filename = f"{card_id.lower()}.png"
    if card_type == "USER":
        return f"cards/common/user/{filename}"
    folder = CARD_TYPE_FOLDERS[card_type]
    return f"cards/editions/{edition_id}/{folder}/{filename}"


def convert_working(image: Image.Image) -> Image.Image:
    if image.mode in {"RGBA", "LA", "P"}:
        return image.convert("RGBA")
    return image.convert("RGB")


def save_runtime_png(working: Image.Image, destination_path: Path) -> None:
    destination_path.parent.mkdir(parents=True, exist_ok=True)
    working.save(destination_path, format="PNG", optimize=True)


def process_user_image(source_path: Path, destination_path: Path) -> tuple[str, bool, tuple[int, int]]:
    with Image.open(source_path) as image:
        working = convert_working(image)
        width, height = working.size
        if width <= height:
            raise ValueError(
                f"User card front must remain landscape for {source_path}: {width}x{height}",
            )
        if width > MAX_LANDSCAPE_WIDTH:
            scale = MAX_LANDSCAPE_WIDTH / width
            working = working.resize(
                (int(round(width * scale)), int(round(height * scale))),
                Image.Resampling.LANCZOS,
            )
            width, height = working.size
        save_runtime_png(working, destination_path)
        return "LANDSCAPE", False, (width, height)


def process_portrait_image(source_path: Path, destination_path: Path) -> tuple[str, bool, tuple[int, int]]:
    with Image.open(source_path) as image:
        working = convert_working(image)
        width, height = working.size
        rotation_applied = False
        if width > height:
            working = working.rotate(90, expand=True)
            rotation_applied = True
        width, height = working.size
        if height <= width:
            raise ValueError(
                f"Generated image is not portrait for {source_path}: {width}x{height}",
            )
        if width > MAX_PORTRAIT_WIDTH:
            scale = MAX_PORTRAIT_WIDTH / width
            working = working.resize(
                (int(round(width * scale)), int(round(height * scale))),
                Image.Resampling.LANCZOS,
            )
            width, height = working.size
        save_runtime_png(working, destination_path)
        return "PORTRAIT", rotation_applied, (width, height)


def process_image(source_path: Path, destination_path: Path, card_type: str) -> tuple[str, bool, tuple[int, int]]:
    if card_type == "USER":
        return process_user_image(source_path, destination_path)
    return process_portrait_image(source_path, destination_path)


def should_process_card(source_path: Path, destination_path: Path) -> bool:
    if not destination_path.is_file():
        return True
    return source_path.stat().st_mtime > destination_path.stat().st_mtime


def write_manifest(path: Path, edition_id: str, cards: dict[str, dict]) -> None:
    payload = {
        "schemaVersion": 2,
        "editionId": edition_id,
        "generatedBy": "tools/sync_android_card_images.py",
        "cards": cards,
    }
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(payload, indent=2, sort_keys=False) + "\n", encoding="utf-8")


def sync_common_user_cards(
    workspace_root: Path,
    project_root: Path,
    assets_root: Path,
    problems: list[str],
    *,
    dry_run: bool = False,
    verify: bool = False,
) -> dict[str, dict]:
    manifest: dict[str, dict] = {}
    for card in load_registry_cards(project_root):
        if card["cardType"] != "USER":
            continue
        card_id = card["cardId"]
        front_rel = (card.get("assets") or {}).get("front")
        if not front_rel:
            problems.append(f"common/{card_id}: missing assets.front in card registry")
            continue
        try:
            validate_front_path(front_rel)
        except ValueError as exc:
            problems.append(f"common/{card_id}: {exc}")
            continue
        source_path = workspace_root / front_rel
        if not source_path.is_file():
            problems.append(f"common/{card_id}: missing source front asset: {front_rel}")
            continue
        runtime_rel = runtime_asset_rel("USER", "common", card_id)
        destination_path = assets_root / runtime_rel
        if verify:
            if not destination_path.is_file():
                problems.append(f"common/{card_id}: missing Android runtime asset: {runtime_rel}")
            else:
                print(f"  VERIFY OK common/{card_id} -> {runtime_rel}")
            manifest[card_id] = {
                "cardId": card_id,
                "cardType": "USER",
                "name": card["name"],
                "sourceFrontPath": front_rel.replace("\\", "/"),
                "runtimeAssetPath": runtime_rel,
                "asset": runtime_rel,
            }
            continue
        if dry_run:
            action = "REFRESH" if should_process_card(source_path, destination_path) else "KEEP"
            print(f"  DRY-RUN {action} common/{card_id} -> {runtime_rel}")
            manifest[card_id] = {
                "cardId": card_id,
                "cardType": "USER",
                "name": card["name"],
                "sourceFrontPath": front_rel.replace("\\", "/"),
                "runtimeAssetPath": runtime_rel,
                "asset": runtime_rel,
            }
            continue
        try:
            if should_process_card(source_path, destination_path):
                orientation, rotation_applied, dimensions = process_image(
                    source_path,
                    destination_path,
                    "USER",
                )
            else:
                with Image.open(destination_path) as image:
                    width, height = image.size
                    orientation = "LANDSCAPE"
                    rotation_applied = False
                    dimensions = (width, height)
        except Exception as exc:  # noqa: BLE001
            problems.append(f"common/{card_id}: failed to process {front_rel}: {exc}")
            continue
        manifest[card_id] = {
            "cardId": card_id,
            "cardType": "USER",
            "name": card["name"],
            "sourceFrontPath": front_rel.replace("\\", "/"),
            "runtimeAssetPath": runtime_rel,
            "asset": runtime_rel,
            "orientation": orientation,
            "rotationApplied": rotation_applied,
            "width": dimensions[0],
            "height": dimensions[1],
        }
        print(f"  common/{card_id} -> {runtime_rel}")
    return manifest


def sync_edition_cards(
    workspace_root: Path,
    project_root: Path,
    assets_root: Path,
    edition_id: str,
    problems: list[str],
    *,
    dry_run: bool = False,
    verify: bool = False,
    prune: bool = False,
) -> dict[str, dict]:
    edition = load_edition_manifest(project_root, edition_id)
    artwork_status = edition.get("artworkStatus", "READY")
    manifest: dict[str, dict] = {}
    for card in load_edition_cards(project_root, edition_id):
        card_id = card["cardId"]
        card_type = card["cardType"]
        front_rel = card.get("frontAsset")
        if not front_rel:
            msg = f"{edition_id}/{card_type}/{card_id}: missing frontAsset in edition data"
            if artwork_status in {"READY", "FRONTS_READY"}:
                problems.append(msg)
            else:
                print(f"  SKIP {msg}")
            continue
        try:
            validate_front_path(front_rel, allow_energy_grid_alias=card_type == "ENERGY_GRID")
        except ValueError as exc:
            problems.append(f"{edition_id}/{card_type}/{card_id}: {exc}")
            continue
        source_path = resolve_source_front_path(workspace_root, card, front_rel)
        if source_path is None:
            msg = f"{edition_id}/{card_type}/{card_id}: missing source front asset: {front_rel}"
            if artwork_status in {"READY", "FRONTS_READY"}:
                problems.append(msg)
            else:
                print(f"  SKIP {msg}")
            continue
        runtime_rel = runtime_asset_rel(card_type, edition_id, card_id)
        destination_path = assets_root / runtime_rel
        if verify:
            if not destination_path.is_file():
                problems.append(f"{edition_id}/{card_type}/{card_id}: missing Android runtime asset: {runtime_rel}")
            else:
                print(f"  VERIFY OK {edition_id}/{card_type}/{card_id} -> {runtime_rel}")
            manifest[card_id] = {
                "cardId": card_id,
                "cardType": card_type,
                "name": card["name"],
                "sourceFrontPath": front_rel.replace("\\", "/"),
                "runtimeAssetPath": runtime_rel,
                "asset": runtime_rel,
            }
            continue
        if dry_run:
            action = "REFRESH" if should_process_card(source_path, destination_path) else "KEEP"
            print(f"  DRY-RUN {action} {edition_id}/{card_type}/{card_id} -> {runtime_rel}")
            manifest[card_id] = {
                "cardId": card_id,
                "cardType": card_type,
                "name": card["name"],
                "sourceFrontPath": front_rel.replace("\\", "/"),
                "runtimeAssetPath": runtime_rel,
                "asset": runtime_rel,
            }
            continue
        try:
            if should_process_card(source_path, destination_path):
                orientation, rotation_applied, dimensions = process_image(
                    source_path,
                    destination_path,
                    card_type,
                )
            else:
                with Image.open(destination_path) as image:
                    width, height = image.size
                    orientation = "PORTRAIT" if card_type != "USER" else "LANDSCAPE"
                    rotation_applied = False
                    dimensions = (width, height)
        except Exception as exc:  # noqa: BLE001
            problems.append(f"{edition_id}/{card_type}/{card_id}: failed to process {front_rel}: {exc}")
            continue
        manifest[card_id] = {
            "cardId": card_id,
            "cardType": card_type,
            "name": card["name"],
            "sourceFrontPath": front_rel.replace("\\", "/"),
            "runtimeAssetPath": runtime_rel,
            "asset": runtime_rel,
            "orientation": orientation,
            "rotationApplied": rotation_applied,
            "width": dimensions[0],
            "height": dimensions[1],
        }
        print(f"  {edition_id}/{card_type}/{card_id} -> {runtime_rel}")

    if prune and edition_id == "india":
        event_dir = assets_root / "cards" / "editions" / edition_id / "event"
        expected = {
            runtime_asset_rel("EVENT", edition_id, card_id).split("/")[-1]
            for card_id, entry in manifest.items()
            if entry.get("cardType") == "EVENT"
        }
        if event_dir.is_dir():
            for path in sorted(event_dir.glob("*.png")):
                if path.name not in expected:
                    if dry_run:
                        print(f"  DRY-RUN PRUNE {path}")
                    elif prune:
                        print(f"  PRUNE {path}")
                        path.unlink()
    return manifest


def sync_bundled_edition_json(project_root: Path, edition_id: str, *, dry_run: bool = False) -> None:
    source_dir = project_root / "data" / "editions" / edition_id
    destination_dir = (
        project_root
        / "android-app"
        / "app"
        / "src"
        / "main"
        / "assets"
        / "game"
        / "editions"
        / edition_id
    )
    for filename in ("edition.json", "events.json", "properties.json"):
        source = source_dir / filename
        if not source.is_file():
            continue
        destination = destination_dir / filename
        if dry_run:
            print(f"  DRY-RUN COPY {source.relative_to(project_root)} -> {destination.relative_to(project_root)}")
            continue
        destination.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(source, destination)
        print(f"  COPY {filename} -> assets/game/editions/{edition_id}/{filename}")


def remove_legacy_assets(project_root: Path, assets_root: Path) -> None:
    legacy_manifest = assets_root / "cards" / COMMON_MANIFEST_NAME
    if legacy_manifest.is_file():
        legacy_manifest.unlink()
    legacy_fronts = assets_root / "cards" / "fronts"
    if legacy_fronts.is_dir():
        for path in legacy_fronts.glob("*"):
            if path.is_file():
                path.unlink()
        legacy_fronts.rmdir()
    legacy_data_manifest = project_root / "data" / COMMON_MANIFEST_NAME
    if legacy_data_manifest.is_file():
        legacy_data_manifest.unlink()


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--edition",
        help="Sync only the specified edition id (common user cards are always synced)",
    )
    parser.add_argument("--dry-run", action="store_true", help="Report actions without writing files.")
    parser.add_argument("--verify", action="store_true", help="Verify Android runtime assets exist.")
    parser.add_argument(
        "--prune",
        action="store_true",
        help="Remove stale India event runtime PNGs not in the current manifest.",
    )
    args = parser.parse_args()

    tools_dir = Path(__file__).resolve().parent
    workspace_root = find_workspace_root(tools_dir)
    project_root = find_project_root(workspace_root)
    assets_root = project_root / "android-app" / "app" / "src" / "main" / "assets"
    data_cards_root = project_root / "data" / "cards"

    edition_ids = discover_edition_ids(project_root, args.edition)
    problems: list[str] = []

    print(f"Workspace root: {workspace_root}")
    print(f"Project root:   {project_root}")
    print(f"Assets root:    {assets_root}")
    print("Syncing common user cards:")
    common_manifest = sync_common_user_cards(
        workspace_root,
        project_root,
        assets_root,
        problems,
        dry_run=args.dry_run,
        verify=args.verify,
    )
    if not args.dry_run and not args.verify:
        common_manifest_path = data_cards_root / "common" / COMMON_MANIFEST_NAME
        android_common_manifest_path = assets_root / "cards" / "common" / COMMON_MANIFEST_NAME
        write_manifest(common_manifest_path, "common", common_manifest)
        write_manifest(android_common_manifest_path, "common", common_manifest)

    for edition_id in edition_ids:
        print(f"Syncing edition '{edition_id}':")
        edition_manifest = sync_edition_cards(
            workspace_root,
            project_root,
            assets_root,
            edition_id,
            problems,
            dry_run=args.dry_run,
            verify=args.verify,
            prune=args.prune,
        )
        if not args.dry_run and not args.verify:
            edition_manifest_path = data_cards_root / "editions" / edition_id / COMMON_MANIFEST_NAME
            android_edition_manifest_path = (
                assets_root / "cards" / "editions" / edition_id / COMMON_MANIFEST_NAME
            )
            write_manifest(edition_manifest_path, edition_id, edition_manifest)
            write_manifest(android_edition_manifest_path, edition_id, edition_manifest)
            sync_bundled_edition_json(project_root, edition_id, dry_run=False)
        elif args.dry_run:
            sync_bundled_edition_json(project_root, edition_id, dry_run=True)

    if not args.dry_run and not args.verify:
        remove_legacy_assets(project_root, assets_root)

    if problems:
        print("\nFAIL:")
        for problem in problems:
            print(f"  - {problem}")
        return 1

    print("\nPASS:")
    print(f"  Common user fronts: {len(common_manifest)}")
    for edition_id in edition_ids:
        manifest_path = data_cards_root / "editions" / edition_id / COMMON_MANIFEST_NAME
        count = len(load_json(manifest_path)["cards"])
        print(f"  {edition_id} fronts: {count}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
