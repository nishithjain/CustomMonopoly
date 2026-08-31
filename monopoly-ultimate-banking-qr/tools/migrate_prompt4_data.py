#!/usr/bin/env python3
"""Migrate Prompt 4 data: split card registry, add board_layout.json, update edition.json."""

from __future__ import annotations

import json
import shutil
from pathlib import Path

PROJECT_ROOT = Path(__file__).resolve().parent.parent
DATA = PROJECT_ROOT / "data"
CARD_CONFIG = {
    "playerCardCount": 4,
    "propertyCardCount": 22,
    "eventCardCount": 23,
    "rentLevelsPerProperty": 5,
}


def load_json(path: Path) -> dict:
    return json.loads(path.read_text(encoding="utf-8"))


def write_json(path: Path, payload: dict) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(payload, indent=2) + "\n", encoding="utf-8")


def split_card_registry() -> None:
    registry_path = DATA / "common" / "card_registry.json"
    payload = load_json(registry_path)
    cards = payload["cards"]
    user_cards = [card for card in cards if card["cardType"] == "USER"]
    edition_cards = [card for card in cards if card["cardType"] in {"PROPERTY", "EVENT"}]

    write_json(registry_path, {"schemaVersion": payload.get("schemaVersion", 1), "cards": user_cards})

    for edition_id in ("uk", "india"):
        write_json(
            DATA / "editions" / edition_id / "card_registry.json",
            {"schemaVersion": 1, "cards": edition_cards},
        )


def build_uk_board_layout() -> dict:
    spaces: list[dict] = [{"position": 0, "spaceId": "GO", "spaceType": "GO"}]

    side1 = [
        "PRP_01",
        "EVENT",
        "PRP_02",
        "LOCATION",
        "PRP_03",
        "PRP_04",
        "EVENT",
        "PRP_05",
        "LOCATION",
    ]
    side2 = [
        "PRP_06",
        "EVENT",
        "PRP_07",
        "PRP_08",
        "LOCATION",
        "PRP_09",
        "PRP_10",
        "LOCATION",
        "PRP_11",
    ]
    side3 = [
        "PRP_12",
        "EVENT",
        "PRP_13",
        "PRP_14",
        "LOCATION",
        "PRP_15",
        "PRP_16",
        "LOCATION",
        "PRP_17",
    ]
    side4 = [
        "PRP_18",
        "LOCATION",
        "PRP_19",
        "PRP_20",
        "EVENT",
        "PRP_21",
        "PRP_22",
        "LOCATION",
        "LOCATION",
    ]
    side_defs = [
        (side1, 10, "JAIL", "JAIL"),
        (side2, 20, "FREE_PARKING", "FREE_PARKING"),
        (side3, 30, "GO_TO_JAIL", "GO_TO_JAIL"),
    ]

    position = 1
    event_index = 1
    location_index = 1

    for side, corner_pos, corner_id, corner_type in side_defs:
        for token in side:
            if token.startswith("PRP_"):
                spaces.append(
                    {
                        "position": position,
                        "spaceId": f"PROPERTY_{token}_SPACE",
                        "spaceType": "PROPERTY",
                        "targetId": token,
                    }
                )
            elif token == "EVENT":
                spaces.append(
                    {
                        "position": position,
                        "spaceId": f"EVENT_{event_index:02d}_SPACE",
                        "spaceType": "EVENT",
                        "deckId": "main",
                    }
                )
                event_index += 1
            else:
                spaces.append(
                    {
                        "position": position,
                        "spaceId": f"LOCATION_{location_index:02d}_SPACE",
                        "spaceType": "LOCATION",
                    }
                )
                location_index += 1
            position += 1
        spaces.append({"position": corner_pos, "spaceId": corner_id, "spaceType": corner_type})
        position = corner_pos + 1

    for token in side4:
        if token.startswith("PRP_"):
            spaces.append(
                {
                    "position": position,
                    "spaceId": f"PROPERTY_{token}_SPACE",
                    "spaceType": "PROPERTY",
                    "targetId": token,
                }
            )
        elif token == "EVENT":
            spaces.append(
                {
                    "position": position,
                    "spaceId": f"EVENT_{event_index:02d}_SPACE",
                    "spaceType": "EVENT",
                    "deckId": "main",
                }
            )
            event_index += 1
        else:
            spaces.append(
                {
                    "position": position,
                    "spaceId": f"LOCATION_{location_index:02d}_SPACE",
                    "spaceType": "LOCATION",
                }
            )
            location_index += 1
        position += 1

    spaces.sort(key=lambda item: item["position"])
    return {"schemaVersion": 1, "spaces": spaces}


def update_edition_manifests() -> None:
    for edition_id in ("uk", "india"):
        path = DATA / "editions" / edition_id / "edition.json"
        edition = load_json(path)
        edition["cardConfiguration"] = CARD_CONFIG
        edition.setdefault("data", {})
        edition["data"]["boardLayout"] = "board_layout.json"
        edition["data"]["cardRegistry"] = "card_registry.json"
        write_json(path, edition)


def write_board_layouts() -> None:
    layout = build_uk_board_layout()
    for edition_id in ("uk", "india"):
        write_json(DATA / "editions" / edition_id / "board_layout.json", layout)


def main() -> int:
    split_card_registry()
    write_board_layouts()
    update_edition_manifests()
    print("Prompt 4 data migration complete.")
    print(f"  common user cards: {len(load_json(DATA / 'common' / 'card_registry.json')['cards'])}")
    print(f"  uk edition cards: {len(load_json(DATA / 'editions' / 'uk' / 'card_registry.json')['cards'])}")
    print(f"  board spaces: {len(load_json(DATA / 'editions' / 'uk' / 'board_layout.json')['spaces'])}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
