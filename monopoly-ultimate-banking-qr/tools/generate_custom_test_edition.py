#!/usr/bin/env python3
"""Generate the custom-test edition used by DefinitionValidatorTest."""

from __future__ import annotations

import json
import shutil
from pathlib import Path

PROJECT_ROOT = Path(__file__).resolve().parent.parent
DATA = PROJECT_ROOT / "data"
TARGET = PROJECT_ROOT / "android-app" / "game-core" / "src" / "test" / "resources" / "test-editions" / "custom-test"
UK = DATA / "editions" / "uk"
COMMON = DATA / "common"

CARD_CONFIG = {
    "playerCardCount": 5,
    "propertyCardCount": 24,
    "eventCardCount": 20,
    "rentLevelsPerProperty": 6,
}


def load(path: Path) -> dict | list:
    return json.loads(path.read_text(encoding="utf-8"))


def write(path: Path, payload: dict | list) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(payload, indent=2) + "\n", encoding="utf-8")


def extend_rent_levels(rent_levels: list[dict], target: int) -> list[dict]:
    levels = list(rent_levels)
    while len(levels) < target:
        last = levels[-1]
        levels.append(
            {
                "level": len(levels) + 1,
                "amount": int(last["amount"] * 1.5),
            }
        )
    return levels[:target]


def build_properties() -> dict:
    uk_props = load(UK / "properties.json")["properties"][:22]
    color_groups = {
        range(1, 7): "CUSTOM_A",
        range(7, 13): "CUSTOM_B",
        range(13, 19): "CUSTOM_C",
        range(19, 25): "CUSTOM_D",
    }
    properties = []
    for index, prop in enumerate(uk_props, start=1):
        prop = dict(prop)
        prop["propertyId"] = f"CTP_{index:02d}"
        prop["qrPayload"] = f"MUB:CTP:{index:02d}"
        prop["sequence"] = index
        prop["maximumRentLevel"] = 6
        prop["rentLevels"] = extend_rent_levels(prop["rentLevels"], 6)
        prop["colorGroup"] = next(group for ids, group in color_groups.items() if index in ids)
        prop["colorGroupLabel"] = prop["colorGroup"].replace("_", " ").title()
        properties.append(prop)
    for extra_index in (23, 24):
        template = dict(properties[-1])
        template["propertyId"] = f"CTP_{extra_index:02d}"
        template["name"] = f"Custom Property {extra_index}"
        template["qrPayload"] = f"MUB:CTP:{extra_index:02d}"
        template["sequence"] = extra_index
        template["colorGroup"] = "CUSTOM_D"
        template["colorGroupLabel"] = "Custom D"
        properties.append(template)
    return {"schemaVersion": 1, "properties": properties}


def build_events() -> dict:
    uk_events = load(UK / "events.json")["events"][:20]
    events = []
    for index, event in enumerate(uk_events, start=1):
        event = dict(event)
        event["eventId"] = f"CEV_{index:02d}"
        event["qrPayload"] = f"MUB:CEV:{index:02d}"
        event["sequence"] = index
        events.append(event)
    return {"schemaVersion": 1, "events": events}


def build_card_registry(properties: list[dict], events: list[dict]) -> dict:
    cards = []
    cards.extend(
        {
            "cardId": prop["propertyId"],
            "cardType": "PROPERTY",
            "sequence": prop["sequence"],
            "name": prop["name"],
            "qrPayload": prop["qrPayload"],
        }
        for prop in properties
    )
    cards.extend(
        {
            "cardId": event["eventId"],
            "cardType": "EVENT",
            "sequence": event["sequence"],
            "name": event["name"],
            "qrPayload": event["qrPayload"],
        }
        for event in events
    )
    return {"schemaVersion": 1, "cards": cards}


def build_board_layout() -> dict:
    spaces = [{"position": 0, "spaceId": "GO", "spaceType": "GO"}]
    side_property_groups = [
        [f"CTP_{i:02d}" for i in range(1, 7)],
        [f"CTP_{i:02d}" for i in range(7, 13)],
        [f"CTP_{i:02d}" for i in range(13, 19)],
        [f"CTP_{i:02d}" for i in range(19, 25)],
    ]
    side_defs = [
        (side_property_groups[0], 8, "JAIL", "JAIL"),
        (side_property_groups[1], 16, "FREE_PARKING", "FREE_PARKING"),
        (side_property_groups[2], 24, "GO_TO_JAIL", "GO_TO_JAIL"),
    ]
    side4 = side_property_groups[3]
    position = 1

    def append_side(tokens: list[str], event_slot: int) -> None:
        nonlocal position
        property_index = 0
        for slot in range(7):
            if slot == event_slot:
                spaces.append(
                    {
                        "position": position,
                        "spaceId": f"EVENT_SPACE_{position}",
                        "spaceType": "EVENT",
                        "deckId": "main",
                    }
                )
            else:
                token = tokens[property_index]
                property_index += 1
                spaces.append(
                    {
                        "position": position,
                        "spaceId": f"PROPERTY_{token}_SPACE",
                        "spaceType": "PROPERTY",
                        "targetId": token,
                    }
                )
            position += 1

    append_side(side_property_groups[0], event_slot=2)
    spaces.append({"position": 8, "spaceId": "JAIL", "spaceType": "JAIL"})
    position = 9
    append_side(side_property_groups[1], event_slot=2)
    spaces.append({"position": 16, "spaceId": "FREE_PARKING", "spaceType": "FREE_PARKING"})
    position = 17
    append_side(side_property_groups[2], event_slot=2)
    spaces.append({"position": 24, "spaceId": "GO_TO_JAIL", "spaceType": "GO_TO_JAIL"})
    position = 25
    append_side(side4, event_slot=2)
    spaces.sort(key=lambda item: item["position"])
    return {"schemaVersion": 1, "spaces": spaces}


def build_board_relationships(properties: list[dict]) -> dict:
    ids = [prop["propertyId"] for prop in properties]
    neighbours = {}
    for index, property_id in enumerate(ids):
        left = ids[(index - 1) % len(ids)]
        right = ids[(index + 1) % len(ids)]
        neighbours[property_id] = [left, right]
    side1 = ids[0:6]
    side2 = ids[6:12]
    side3 = ids[12:18]
    side4 = ids[18:24]
    return {
        "schemaVersion": 1,
        "colorGroups": {
            "CUSTOM_A": ids[0:6],
            "CUSTOM_B": ids[6:12],
            "CUSTOM_C": ids[12:18],
            "CUSTOM_D": ids[18:24],
        },
        "neighbours": {
            "status": "RESOLVED",
            "definition": "Custom test circular chain.",
            "ownedOnlyForEventEffects": True,
            "mappings": neighbours,
        },
        "boardSides": {
            "status": "RESOLVED",
            "definition": "Custom test board sides.",
            "mappings": {
                "SIDE_A": side1,
                "SIDE_B": side2,
                "SIDE_C": side3,
                "SIDE_D": side4,
            },
            "propertyToSide": {
                **{property_id: "SIDE_A" for property_id in side1},
                **{property_id: "SIDE_B" for property_id in side2},
                **{property_id: "SIDE_C" for property_id in side3},
                **{property_id: "SIDE_D" for property_id in side4},
            },
        },
    }


def build_event_engine_rules(events: list[dict]) -> None:
    uk_rules = load(COMMON / "event_engine_rules.json")
    rules_by_old_id = {rule["eventId"]: rule for rule in uk_rules["events"]}
    mapped = []
    for event in events:
        old_id = f"EVT_{event['sequence']:02d}"
        rule = dict(rules_by_old_id[old_id])
        rule["eventId"] = event["eventId"]
        mapped.append(rule)
    write(TARGET / "event_engine_rules.json", {"schemaVersion": 1, "events": mapped})


def build_edition_json() -> dict:
    uk_edition = load(UK / "edition.json")
    uk_edition["definitionVersion"] = 1
    uk_edition["editionId"] = "custom-test"
    uk_edition["name"] = "Custom Test Edition"
    uk_edition["cardConfiguration"] = CARD_CONFIG
    uk_edition["data"]["boardLayout"] = "board_layout.json"
    uk_edition["data"]["cardRegistry"] = "card_registry.json"
    uk_edition["data"]["events"] = "events.json"
    uk_edition["data"]["eventEngineRules"] = "event_engine_rules.json"
    uk_edition["resources"] = {
        "propertyCards": "Editions/custom-test/PropertyCards",
        "eventCards": "Editions/custom-test/EventCards",
    }
    return uk_edition


def build_common_registry() -> None:
    users = load(COMMON / "card_registry.json")["cards"]
    fifth_user = {
        "cardId": "USR_05",
        "cardType": "USER",
        "sequence": 5,
        "name": "Train",
        "qrPayload": "MUB:PL:TRAIN",
        "assets": {
            "front": "Resources/Common/UserCards/Train_Front.jpg",
            "qr": "Resources/Common/UserCards/05_Train_Back_QR.png",
        },
    }
    write(TARGET / "common_card_registry.json", {"schemaVersion": 1, "cards": users + [fifth_user]})


def main() -> int:
    if TARGET.exists():
        shutil.rmtree(TARGET)
    TARGET.mkdir(parents=True)
    properties = build_properties()
    events = build_events()
    write(TARGET / "properties.json", properties)
    write(TARGET / "events.json", events)
    write(TARGET / "card_registry.json", build_card_registry(properties["properties"], events["events"]))
    write(TARGET / "board_layout.json", build_board_layout())
    write(TARGET / "board_relationships.json", build_board_relationships(properties["properties"]))
    write(TARGET / "banking_values.json", load(UK / "banking_values.json"))
    write(TARGET / "edition.json", build_edition_json())
    build_event_engine_rules(events["events"])
    build_common_registry()
    print(f"Generated custom-test edition at {TARGET}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
