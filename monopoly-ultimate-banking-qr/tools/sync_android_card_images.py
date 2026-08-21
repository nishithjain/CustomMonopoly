#!/usr/bin/env python3
"""Generate Android runtime card-front assets from the master card registry."""

from __future__ import annotations

import json
import sys
from pathlib import Path

try:
    from PIL import Image
except ImportError as exc:  # pragma: no cover - runtime dependency check
    raise SystemExit(
        "Pillow is required. Install with: python -m pip install Pillow",
    ) from exc

EXPECTED_COUNTS = {
    "USER": 4,
    "PROPERTY": 22,
    "EVENT": 23,
}
EXPECTED_TOTAL = 49
MAX_PORTRAIT_WIDTH = 1024
MAX_LANDSCAPE_WIDTH = 1024
MANIFEST_NAME = "android_card_front_manifest.json"
FRONT_SUFFIXES = (".png", ".jpg", ".jpeg")


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


def load_cards(project_root: Path) -> list[dict]:
    registry_path = project_root / "data" / "common" / "card_registry.json"
    with registry_path.open(encoding="utf-8") as handle:
        payload = json.load(handle)
    cards = payload["cards"]
    uk_properties = json.loads(
        (project_root / "data" / "editions" / "uk" / "properties.json").read_text(encoding="utf-8")
    )["properties"]
    uk_events = json.loads(
        (project_root / "data" / "editions" / "uk" / "events.json").read_text(encoding="utf-8")
    )["events"]
    property_fronts = {item["propertyId"]: item.get("frontAsset") for item in uk_properties}
    event_fronts = {item["eventId"]: item.get("frontAsset") for item in uk_events}
    merged = []
    for card in cards:
        card_type = card["cardType"]
        item = dict(card)
        if card_type == "USER":
            merged.append(item)
            continue
        assets = dict(item.get("assets") or {})
        if card_type == "PROPERTY":
            assets["front"] = property_fronts.get(card["cardId"])
        elif card_type == "EVENT":
            assets["front"] = event_fronts.get(card["cardId"])
        item["assets"] = assets
        if card_type == "PROPERTY":
            name = next((p["name"] for p in uk_properties if p["propertyId"] == card["cardId"]), card.get("name"))
            item["name"] = name
        if card_type == "EVENT":
            name = next((e["name"] for e in uk_events if e["eventId"] == card["cardId"]), card.get("name"))
            item["name"] = name
        merged.append(item)
    return merged


def validate_front_path(front_rel: str) -> None:
    lowered = front_rel.replace("\\", "/").lower()
    if "_back_qr" in lowered or lowered.endswith("_back.png") or lowered.endswith("_back.jpg"):
        raise ValueError(f"Back/QR asset must not be used as front: {front_rel}")
    if "_front" not in lowered:
        raise ValueError(f"Front asset must contain '_Front' marker: {front_rel}")
    if not lowered.endswith(FRONT_SUFFIXES):
        raise ValueError(
            f"Front asset must be PNG or JPG ({', '.join(FRONT_SUFFIXES)}): {front_rel}",
        )


def runtime_filename(card_id: str) -> str:
    return f"{card_id.lower()}.png"


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


def main() -> int:
    tools_dir = Path(__file__).resolve().parent
    workspace_root = find_workspace_root(tools_dir)
    project_root = find_project_root(workspace_root)
    cards = load_cards(project_root)

    destination_dir = (
        project_root
        / "android-app"
        / "app"
        / "src"
        / "main"
        / "assets"
        / "cards"
        / "fronts"
    )
    data_manifest_path = project_root / "data" / MANIFEST_NAME
    android_manifest_path = (
        project_root
        / "android-app"
        / "app"
        / "src"
        / "main"
        / "assets"
        / "cards"
        / MANIFEST_NAME
    )

    counts: dict[str, int] = {"USER": 0, "PROPERTY": 0, "EVENT": 0}
    manifest: dict[str, dict] = {}
    problems: list[str] = []

    print(f"Workspace root: {workspace_root}")
    print(f"Project root:   {project_root}")
    print(f"Destination:    {destination_dir}")

    for card in cards:
        card_id = card["cardId"]
        card_type = card["cardType"]
        assets = card.get("assets") or {}
        front_rel = assets.get("front")
        if not front_rel:
            problems.append(f"{card_id}: missing assets.front in card registry/edition data")
            continue
        try:
            validate_front_path(front_rel)
        except ValueError as exc:
            problems.append(f"{card_id}: {exc}")
            continue

        source_path = workspace_root / front_rel
        if not source_path.is_file():
            problems.append(f"{card_id}: missing source front asset: {front_rel}")
            continue

        runtime_name = runtime_filename(card_id)
        runtime_rel = f"cards/fronts/{runtime_name}"
        destination_path = destination_dir / runtime_name

        try:
            orientation, rotation_applied, dimensions = process_image(
                source_path,
                destination_path,
                card_type,
            )
        except Exception as exc:  # noqa: BLE001 - report per-card failures
            problems.append(f"{card_id}: failed to process {front_rel}: {exc}")
            continue

        counts[card_type] = counts.get(card_type, 0) + 1
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
        print(
            f"  {card_id} -> {runtime_rel} ({dimensions[0]}x{dimensions[1]}, {orientation})",
        )

    if len(manifest) != EXPECTED_TOTAL:
        problems.append(
            f"Total processed card fronts mismatch: expected {EXPECTED_TOTAL}, found {len(manifest)}",
        )
    for card_type, expected in EXPECTED_COUNTS.items():
        if counts.get(card_type, 0) != expected:
            problems.append(
                f"{card_type} front count mismatch: expected {expected}, found {counts.get(card_type, 0)}",
            )

    if problems:
        print("\nFAIL:")
        for problem in problems:
            print(f"  - {problem}")
        return 1

    payload = {
        "schemaVersion": 1,
        "generatedBy": "tools/sync_android_card_images.py",
        "cards": manifest,
    }
    data_manifest_path.parent.mkdir(parents=True, exist_ok=True)
    android_manifest_path.parent.mkdir(parents=True, exist_ok=True)
    encoded = json.dumps(payload, indent=2, sort_keys=False)
    data_manifest_path.write_text(encoded + "\n", encoding="utf-8")
    android_manifest_path.write_text(encoded + "\n", encoding="utf-8")

    print("\nPASS:")
    print(f"  User fronts:     {counts['USER']} / {EXPECTED_COUNTS['USER']}")
    print(f"  Property fronts: {counts['PROPERTY']} / {EXPECTED_COUNTS['PROPERTY']}")
    print(f"  Event fronts:    {counts['EVENT']} / {EXPECTED_COUNTS['EVENT']}")
    print(f"  Total:           {len(manifest)} / {EXPECTED_TOTAL}")
    print(f"  Manifest:        {data_manifest_path}")
    print(f"  Android manifest:{android_manifest_path}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
