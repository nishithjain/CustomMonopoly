#!/usr/bin/env python3
"""One-shot data/resource migration into common + editions layout."""

from __future__ import annotations

import json
import shutil
from pathlib import Path

WORKSPACE = Path(__file__).resolve().parent.parent.parent
PROJECT = Path(__file__).resolve().parent.parent
DATA = PROJECT / "data"
RES = WORKSPACE / "Resources"


def dump(path: Path, payload: object) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(payload, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")


def replace_prefix(text: str, old: str, new: str) -> str:
    return text.replace(old, new)


def main() -> None:
    cards = json.loads((DATA / "cards.json").read_text(encoding="utf-8"))
    properties = json.loads((DATA / "properties.json").read_text(encoding="utf-8"))
    events = json.loads((DATA / "events.json").read_text(encoding="utf-8"))
    rules = json.loads((DATA / "game_rules.json").read_text(encoding="utf-8"))
    banking = json.loads((DATA / "banking_values.json").read_text(encoding="utf-8"))
    board = json.loads((DATA / "board_relationships.json").read_text(encoding="utf-8"))
    engine_rules = json.loads((DATA / "event_engine_rules.json").read_text(encoding="utf-8"))
    india_props = json.loads(
        (WORKSPACE / "CustomCardGenerators" / "properties_india.json").read_text(encoding="utf-8")
    )

    baseline = {
        "schemaVersion": 1,
        "editionId": "uk",
        "properties": [
            {
                "propertyId": p["propertyId"],
                "name": p["name"],
                "sequence": p["sequence"],
                "colorGroup": p["colorGroup"],
                "purchasePrice": p["purchasePrice"],
                "rentLevels": p["rentLevels"],
            }
            for p in properties["properties"]
        ],
    }
    dump(DATA / "uk_property_baseline.json", baseline)

    # Card registry: identity only for property/event; user assets stay common.
    registry_cards = []
    for card in cards["cards"]:
        item = {
            "cardId": card["cardId"],
            "cardType": card["cardType"],
            "sequence": card["sequence"],
            "name": card["name"],
            "qrPayload": card["qrPayload"],
        }
        if card["cardType"] == "USER":
            assets = card.get("assets") or {}
            item["assets"] = {
                "front": replace_prefix(assets.get("front", ""), "Resources/Cards/UserCards/", "Resources/Common/UserCards/"),
                "qr": replace_prefix(assets.get("qr", ""), "Resources/Cards/UserCards/", "Resources/Common/UserCards/"),
            }
        registry_cards.append(item)
    dump(
        DATA / "common" / "card_registry.json",
        {"schemaVersion": cards.get("schemaVersion", 1), "cards": registry_cards},
    )

    rules_text = json.dumps(rules, indent=2, ensure_ascii=False)
    rules_text = rules_text.replace("data/cards.json", "data/common/card_registry.json")
    rules_text = rules_text.replace("data/properties.json", "data/editions/<editionId>/properties.json")
    (DATA / "common" / "game_rules.json").parent.mkdir(parents=True, exist_ok=True)
    (DATA / "common" / "game_rules.json").write_text(rules_text + "\n", encoding="utf-8")
    dump(DATA / "common" / "event_engine_rules.json", engine_rules)

    def rewrite_prop_assets(payload: dict, edition: str) -> dict:
        cloned = json.loads(json.dumps(payload))
        for prop in cloned["properties"]:
            if "frontAsset" in prop:
                prop["frontAsset"] = f"Resources/Editions/{edition}/PropertyCards/" + Path(prop["frontAsset"]).name
            if "qrAsset" in prop:
                prop["qrAsset"] = f"Resources/Editions/{edition}/PropertyCards/" + Path(prop["qrAsset"]).name
        return cloned

    def rewrite_event_assets(payload: dict, edition: str) -> dict:
        cloned = json.loads(json.dumps(payload))
        for event in cloned["events"]:
            if "frontAsset" in event:
                event["frontAsset"] = f"Resources/Editions/{edition}/EventCards/" + Path(event["frontAsset"]).name
            if "qrAsset" in event:
                event["qrAsset"] = f"Resources/Editions/{edition}/EventCards/" + Path(event["qrAsset"]).name
        return cloned

    dump(DATA / "editions" / "uk" / "properties.json", rewrite_prop_assets(properties, "uk"))
    dump(DATA / "editions" / "uk" / "events.json", rewrite_event_assets(events, "uk"))
    dump(DATA / "editions" / "uk" / "banking_values.json", banking)
    dump(DATA / "editions" / "uk" / "board_relationships.json", board)
    dump(
        DATA / "editions" / "uk" / "edition.json",
        {
            "schemaVersion": 1,
            "editionId": "uk",
            "name": "UK Edition",
            "countryCode": "GB",
            "currency": {"code": "M", "symbol": "M", "scale": 1},
            "data": {
                "properties": "properties.json",
                "bankingValues": "banking_values.json",
                "events": "events.json",
                "boardRelationships": "board_relationships.json",
            },
            "resources": {
                "propertyCards": "Editions/uk/PropertyCards",
                "eventCards": "Editions/uk/EventCards",
            },
        },
    )

    dump(DATA / "editions" / "india" / "properties.json", rewrite_prop_assets(india_props, "india"))
    dump(DATA / "editions" / "india" / "events.json", rewrite_event_assets(events, "india"))
    dump(
        DATA / "editions" / "india" / "banking_values.json",
        {
            "schemaVersion": 1,
            "currency": {"code": "INR", "symbol": "₹", "scale": 100},
            "startingBalance": 150000,
            "goSalary": 20000,
            "locationFee": 10000,
            "jailReleaseFee": 10000,
            "auctionBidIncrement": 2000,
            "eventAmounts": {"M50": 5000, "M200": 20000},
        },
    )
    dump(DATA / "editions" / "india" / "board_relationships.json", board)
    dump(
        DATA / "editions" / "india" / "edition.json",
        {
            "schemaVersion": 1,
            "editionId": "india",
            "name": "India Edition",
            "countryCode": "IN",
            "currency": {"code": "INR", "symbol": "₹", "scale": 100},
            "data": {
                "properties": "properties.json",
                "bankingValues": "banking_values.json",
                "events": "events.json",
                "boardRelationships": "board_relationships.json",
            },
            "resources": {
                "propertyCards": "Editions/india/PropertyCards",
                "eventCards": "Editions/india/EventCards",
            },
            "artworkStatus": "INCOMPLETE",
            "notes": "Event text currently matches the UK baseline. India-specific Event artwork/text may be added later. Do not treat this edition as READY_FOR_PLAY until artwork exists.",
        },
    )

    # Resource moves
    def move_dir(src: Path, dest: Path) -> None:
        dest.parent.mkdir(parents=True, exist_ok=True)
        if dest.exists():
            return
        if src.exists():
            shutil.copytree(src, dest)

    move_dir(RES / "Icons", RES / "Common" / "Icons")
    move_dir(RES / "Sounds", RES / "Common" / "Sounds")
    move_dir(RES / "Cards" / "UserCards", RES / "Common" / "UserCards")
    move_dir(RES / "Cards" / "PropertyCards", RES / "Editions" / "uk" / "PropertyCards")
    move_dir(RES / "Cards" / "EventCards", RES / "Editions" / "uk" / "EventCards")
    (RES / "Editions" / "india" / "PropertyCards").mkdir(parents=True, exist_ok=True)
    (RES / "Editions" / "india" / "EventCards").mkdir(parents=True, exist_ok=True)
    (RES / "Editions" / "india" / "README.md").write_text(
        "India Property/Event artwork is not populated yet.\n"
        "Do not copy UK artwork here. Add original India card fronts when available.\n",
        encoding="utf-8",
    )

    obsolete = [
        DATA / "cards.json",
        DATA / "properties.json",
        DATA / "events.json",
        DATA / "game_rules.json",
        DATA / "banking_values.json",
        DATA / "board_relationships.json",
        DATA / "event_engine_rules.json",
    ]
    for path in obsolete:
        if path.exists():
            path.unlink()

    print("Edition layout files written.")
    print("Resource trees copied. Remove obsolete Resources/Cards, Icons, Sounds after validation.")


if __name__ == "__main__":
    main()
