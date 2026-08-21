#!/usr/bin/env python3
"""Copy runtime master data from data/common and data/editions into Android assets."""

from __future__ import annotations

import shutil
import sys
from pathlib import Path

COMMON_FILES = [
    "card_registry.json",
    "game_rules.json",
    "event_engine_rules.json",
]
EDITIONS = ("uk", "india")
EDITION_FILES = [
    "edition.json",
    "properties.json",
    "banking_values.json",
    "events.json",
    "board_relationships.json",
]


def find_project_root() -> Path:
    current = Path(__file__).resolve().parent
    for candidate in [current.parent, current.parent.parent]:
        if (candidate / "data").is_dir() and (candidate / "android-app").is_dir():
            return candidate
    raise FileNotFoundError("Could not locate project root containing data/ and android-app/")


def copy_file(source: Path, destination: Path) -> None:
    destination.parent.mkdir(parents=True, exist_ok=True)
    shutil.copy2(source, destination)
    print(f"  - {destination.relative_to(destination.parents[4] if False else destination)}")


def main() -> int:
    project_root = find_project_root()
    data_dir = project_root / "data"
    destination_root = project_root / "android-app" / "app" / "src" / "main" / "assets" / "game"

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

    for edition in EDITIONS:
        for filename in EDITION_FILES:
            source = data_dir / "editions" / edition / filename
            if not source.is_file():
                print(f"Missing {source}", file=sys.stderr)
                return 1
            destination = destination_root / "editions" / edition / filename
            destination.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(source, destination)
            print(f"  - editions/{edition}/{filename}")

    print("\nGENERATED RUNTIME ASSETS — DO NOT EDIT DIRECTLY")
    print("Authoritative source: data/common and data/editions")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
