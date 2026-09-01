#!/usr/bin/env python3
"""Wire monopoly-edition-generator India PNG output into Resources/Editions/india paths."""

from __future__ import annotations

import json
import shutil
import sys
from pathlib import Path


def find_workspace_root(tools_dir: Path) -> Path:
    for candidate in [tools_dir.parent.parent, tools_dir.parent.parent.parent]:
        resources = candidate / "Resources"
        generators = candidate / "monopoly-edition-generator"
        if resources.is_dir() and generators.is_dir():
            return candidate
    raise FileNotFoundError("Could not locate workspace root")


def load_json(path: Path) -> dict:
    with path.open(encoding="utf-8") as handle:
        return json.load(handle)


def find_generator_source(output_dir: Path, sequence: int, card_type: str) -> Path | None:
    if card_type == "PROPERTY":
        folder = output_dir / "property_cards" / "png"
        prefix = f"{sequence:02d}_"
    else:
        folder = output_dir / "event_cards" / "png"
        prefix = f"E{sequence:02d}_"
    if not folder.is_dir():
        return None
    matches = sorted(path for path in folder.iterdir() if path.name.startswith(prefix))
    return matches[0] if matches else None


def main() -> int:
    tools_dir = Path(__file__).resolve().parent
    workspace_root = find_workspace_root(tools_dir)
    project_root = workspace_root / "monopoly-ultimate-banking-qr"
    output_dir = workspace_root / "monopoly-edition-generator" / "output" / "india"
    resources_root = workspace_root / "Resources" / "Editions" / "india"
    edition_path = project_root / "data" / "editions" / "india" / "edition.json"
    properties_path = project_root / "data" / "editions" / "india" / "properties.json"
    events_path = project_root / "data" / "editions" / "india" / "events.json"

    missing: list[str] = []
    copied = 0

    for item in load_json(properties_path)["properties"]:
        sequence = item["sequence"]
        front_rel = item["frontAsset"].replace("\\", "/")
        destination = workspace_root / front_rel
        source = find_generator_source(output_dir, sequence, "PROPERTY")
        if source is None or not source.is_file():
            missing.append(f"property sequence {sequence}: no generator output for {destination.name}")
            continue
        destination.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(source, destination)
        copied += 1

    for item in load_json(events_path)["events"]:
        sequence = item["sequence"]
        front_rel = item["frontAsset"].replace("\\", "/")
        destination = workspace_root / front_rel
        source = find_generator_source(output_dir, sequence, "EVENT")
        if source is None or not source.is_file():
            missing.append(f"event sequence {sequence}: no generator output for {destination.name}")
            continue
        destination.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(source, destination)
        copied += 1

    edition = load_json(edition_path)
    if not missing:
        edition["artworkStatus"] = "READY"
        edition_path.write_text(json.dumps(edition, indent=2) + "\n", encoding="utf-8")

    print(f"Copied {copied} India card fronts into Resources/Editions/india")
    if missing:
        print("Missing generator sources:")
        for item in missing:
            print(f"- {item}")
        return 1
    print("India edition artworkStatus set to READY")
    return 0


if __name__ == "__main__":
    sys.exit(main())
