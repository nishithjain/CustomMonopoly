#!/usr/bin/env python3
"""Copy runtime master data from data/common and data/editions into Android assets."""

from __future__ import annotations

import json
import shutil
import sys
from pathlib import Path

COMMON_FILES = [
    "card_registry.json",
    "game_rules.json",
    "event_engine_rules.json",
]
EDITION_FILES = [
    "edition.json",
    "properties.json",
    "banking_values.json",
    "events.json",
    "board_relationships.json",
    "board_layout.json",
    "card_registry.json",
    "game_rules.json",
    "event_engine_rules.json",
]


def find_project_root() -> Path:
    current = Path(__file__).resolve().parent
    for candidate in [current.parent, current.parent.parent]:
        if (candidate / "data").is_dir() and (candidate / "android-app").is_dir():
            return candidate
    raise FileNotFoundError("Could not locate project root containing data/ and android-app/")


def load_production_edition_ids(data_dir: Path) -> list[str]:
    catalog_path = data_dir / "editions" / "index.json"
    if not catalog_path.is_file():
        raise FileNotFoundError(f"Missing edition catalogue at {catalog_path}")
    catalog = json.loads(catalog_path.read_text(encoding="utf-8"))
    edition_ids: list[str] = []
    for entry in catalog.get("editions", []):
        edition_id = str(entry.get("editionId", "")).strip()
        if not edition_id:
            continue
        if entry.get("enabled", True):
            edition_ids.append(edition_id)
    if not edition_ids:
        raise ValueError(f"No enabled editions listed in {catalog_path}")
    return edition_ids


def main() -> int:
    project_root = find_project_root()
    data_dir = project_root / "data"
    destination_root = project_root / "android-app" / "app" / "src" / "main" / "assets" / "game"

    try:
        editions = load_production_edition_ids(data_dir)
    except (FileNotFoundError, ValueError, json.JSONDecodeError) as ex:
        print(str(ex), file=sys.stderr)
        return 1

    # Remove stale flat copies so success proves the new layout.
    for stale in [
        "cards.json",
        "properties.json",
        "events.json",
        "game_rules.json",
        "event_engine_rules.json",
        "board_relationships.json",
        "banking_values.json",
    ]:
        stale_path = destination_root / stale
        if stale_path.exists():
            stale_path.unlink()

    print(f"Source:      {data_dir}")
    print(f"Destination: {destination_root}")
    print(f"Editions:    {', '.join(editions)}")
    print("Copied:")

    for filename in COMMON_FILES:
        source = data_dir / "common" / filename
        if not source.is_file():
            print(f"Missing {source}", file=sys.stderr)
            return 1
        destination = destination_root / "common" / filename
        destination.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(source, destination)
        print(f"  - common/{filename}")

    for edition in editions:
        for filename in EDITION_FILES:
            source = data_dir / "editions" / edition / filename
            if not source.is_file():
                if filename == "event_engine_rules.json":
                    continue
                print(f"Missing {source}", file=sys.stderr)
                return 1
            destination = destination_root / "editions" / edition / filename
            destination.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(source, destination)
            print(f"  - editions/{edition}/{filename}")

    catalog_source = data_dir / "editions" / "index.json"
    if not catalog_source.is_file():
        print(f"Missing {catalog_source}", file=sys.stderr)
        return 1
    catalog_destination = destination_root / "editions" / "index.json"
    shutil.copy2(catalog_source, catalog_destination)
    print("  - editions/index.json")

    print("\nGENERATED RUNTIME ASSETS — DO NOT EDIT DIRECTLY")
    print("Authoritative source: data/common and data/editions")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
