#!/usr/bin/env python3
"""Copy authoritative sound files from Resources/Common/Sounds/ into Android res/raw."""

from __future__ import annotations

import shutil
import sys
from pathlib import Path

USER_SOUND_MAPPINGS = [
    ("Resources/Common/Sounds/UserCardSounds/Car.mp3", "user_car.mp3"),
    ("Resources/Common/Sounds/UserCardSounds/Helicopter.mp3", "user_helicopter.mp3"),
    ("Resources/Common/Sounds/UserCardSounds/Ship.mp3", "user_ship.mp3"),
    ("Resources/Common/Sounds/UserCardSounds/Aeroplane.mp3", "user_aeroplane.mp3"),
]

OTHER_SOUND_MAPPINGS = [
    ("Resources/Common/Sounds/Other/AuctionBegins.mp3", "auction_begins.mp3"),
    ("Resources/Common/Sounds/Other/AuctionEnding.mp3", "auction_ending.mp3"),
    ("Resources/Common/Sounds/Other/Error.mp3", "error.mp3"),
    ("Resources/Common/Sounds/Other/GameStarts.mp3", "game_starts.mp3"),
    ("Resources/Common/Sounds/Other/Go.mp3", "go.mp3"),
    ("Resources/Common/Sounds/Other/GoToJail.mp3", "go_to_jail.mp3"),
    ("Resources/Common/Sounds/Other/Jail.mp3", "jail.mp3"),
    ("Resources/Common/Sounds/Other/KaChing.mp3", "ka_ching.mp3"),
    ("Resources/Common/Sounds/Other/LostGame.mp3", "lost_game.mp3"),
    ("Resources/Common/Sounds/Other/PropertyPurchased.mp3", "property_purchased.mp3"),
    ("Resources/Common/Sounds/Other/RentLevelDecreased.mp3", "rent_level_decreased.mp3"),
    ("Resources/Common/Sounds/Other/RentLevelIncreased.mp3", "rent_level_increased.mp3"),
    ("Resources/Common/Sounds/Other/RentTransfer.mp3", "rent_transfer.mp3"),
    ("Resources/Common/Sounds/Other/ScanCard.mp3", "scan_card.mp3"),
    ("Resources/Common/Sounds/Other/SomeoneJustTookYourMoney.mp3", "someone_just_took_your_money.mp3"),
    ("Resources/Common/Sounds/Other/Undo.mp3", "undo.mp3"),
    ("Resources/Common/Sounds/Other/Winner.mp3", "winner.mp3"),
    ("Resources/Common/Sounds/Other/ColorSetComplete.mp3", "color_set_complete.mp3"),
]

MEDIA_MAPPINGS = USER_SOUND_MAPPINGS + OTHER_SOUND_MAPPINGS


def find_workspace_root() -> Path:
    tools_dir = Path(__file__).resolve().parent
    for candidate in [tools_dir.parent.parent, tools_dir.parent.parent.parent]:
        resources = candidate / "Resources" / "Common" / "Sounds"
        android = candidate / "monopoly-ultimate-banking-qr" / "android-app"
        if resources.is_dir() and android.is_dir():
            return candidate
        project = candidate / "android-app"
        if resources.is_dir() and project.is_dir():
            return candidate
    raise FileNotFoundError("Could not locate workspace root containing Resources/Common/Sounds and android-app/")


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
    destination_dir = project_root / "android-app" / "app" / "src" / "main" / "res" / "raw"
    destination_dir.mkdir(parents=True, exist_ok=True)

    missing: list[str] = []
    copied: list[str] = []

    print(f"Workspace root: {workspace_root}")
    print(f"Destination:    {destination_dir}")
    print("Copied:")

    for source_rel, dest_name in MEDIA_MAPPINGS:
        source = workspace_root / source_rel
        if not source.is_file():
            missing.append(str(source_rel))
            continue
        destination = destination_dir / dest_name
        shutil.copy2(source, destination)
        copied.append(dest_name)
        print(f"  - {source_rel} -> res/raw/{dest_name}")

    if missing:
        print("\nMissing required source files:", file=sys.stderr)
        for path in missing:
            print(f"  - {path}", file=sys.stderr)
        return 1

    expected_total = len(USER_SOUND_MAPPINGS) + len(OTHER_SOUND_MAPPINGS)
    if len(copied) != expected_total:
        print(f"Expected {expected_total} media files, copied {len(copied)}.", file=sys.stderr)
        return 1

    print("\nGENERATED RUNTIME MEDIA — DO NOT EDIT DIRECTLY")
    print("Authoritative source: Resources/Common/Sounds/")
    print(f"UserCardSounds: {len(USER_SOUND_MAPPINGS)}")
    print(f"Other:          {len(OTHER_SOUND_MAPPINGS)}")
    print(f"TOTAL:          {expected_total}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
