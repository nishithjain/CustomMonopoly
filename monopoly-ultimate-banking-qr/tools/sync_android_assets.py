#!/usr/bin/env python3
"""Copy runtime master data JSON from root data/ into Android assets."""

from __future__ import annotations

import shutil
import sys
from pathlib import Path

ASSET_FILES = [
    "cards.json",
    "properties.json",
    "events.json",
    "game_rules.json",
    "event_engine_rules.json",
    "board_relationships.json",
    "banking_values.json",
]


def find_project_root() -> Path:
    current = Path(__file__).resolve().parent
    for candidate in [current.parent, current.parent.parent]:
        data_dir = candidate / "data"
        android_dir = candidate / "android-app"
        if data_dir.is_dir() and android_dir.is_dir():
            return candidate
    raise FileNotFoundError("Could not locate project root containing data/ and android-app/")


def main() -> int:
    project_root = find_project_root()
    source_dir = project_root / "data"
    destination_dir = (
        project_root / "android-app" / "app" / "src" / "main" / "assets" / "game"
    )

    missing = [name for name in ASSET_FILES if not (source_dir / name).is_file()]
    if missing:
        print("Missing source files:", ", ".join(missing), file=sys.stderr)
        return 1

    destination_dir.mkdir(parents=True, exist_ok=True)

    print(f"Source:      {source_dir}")
    print(f"Destination: {destination_dir}")
    print("Copied:")
    for filename in ASSET_FILES:
        source = source_dir / filename
        destination = destination_dir / filename
        shutil.copy2(source, destination)
        print(f"  - {filename}")

    print("\nGENERATED RUNTIME ASSETS — DO NOT EDIT DIRECTLY")
    print("Authoritative source: project-root/data/*.json")
    return 0


if __name__ == "__main__":
    sys.exit(main())
