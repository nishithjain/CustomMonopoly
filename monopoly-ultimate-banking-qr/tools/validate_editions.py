#!/usr/bin/env python3
"""Validate common + edition data layout, UK regression, and India data completeness."""

from __future__ import annotations

import json
from pathlib import Path

PROJECT_ROOT = Path(__file__).resolve().parent.parent
WORKSPACE_ROOT = PROJECT_ROOT.parent
DATA = PROJECT_ROOT / "data"
REPORT = DATA / "edition_validation.txt"

UK_BANKING = {
    "startingBalance": 1500,
    "goSalary": 200,
    "locationFee": 100,
    "jailReleaseFee": 100,
    "auctionBidIncrement": 20,
}
INDIA_BANKING = {
    "startingBalance": 150000,
    "goSalary": 20000,
    "locationFee": 10000,
    "jailReleaseFee": 10000,
    "auctionBidIncrement": 2000,
}


def load(path: Path) -> dict:
    return json.loads(path.read_text(encoding="utf-8"))


def count_fronts(directory: Path, suffix: str) -> int:
    if not directory.is_dir():
        return 0
    return sum(1 for path in directory.iterdir() if path.name.endswith(suffix) and path.is_file())


def card_config(edition: dict) -> dict:
    config = edition.get("cardConfiguration")
    if not isinstance(config, dict):
        raise ValueError(f"Edition '{edition.get('editionId', '?')}': cardConfiguration is missing or invalid.")
    return config


def validate_card_configuration(
    edition_id: str,
    edition: dict,
    properties: list,
    events: list,
    cards: list,
) -> list[str]:
    problems: list[str] = []
    try:
        config = card_config(edition)
    except ValueError as exc:
        return [str(exc)]
    for field in ("playerCardCount", "propertyCardCount", "eventCardCount", "rentLevelsPerProperty"):
        value = config.get(field)
        if not isinstance(value, int) or value < 0:
            problems.append(f"Edition '{edition_id}': cardConfiguration.{field} is missing or invalid.")
    if problems:
        return problems
    if config["playerCardCount"] <= 0 or config["propertyCardCount"] <= 0 or config["rentLevelsPerProperty"] <= 0:
        problems.append(f"Edition '{edition_id}': cardConfiguration contains non-positive required counts.")
    if len(properties) != config["propertyCardCount"]:
        problems.append(
            f"Edition '{edition_id}': expected {config['propertyCardCount']} Property Cards from edition.json, but found {len(properties)}."
        )
    if len(events) != config["eventCardCount"]:
        problems.append(
            f"Edition '{edition_id}': expected {config['eventCardCount']} Event Cards from edition.json, but found {len(events)}."
        )
    user_cards = [card for card in cards if card["cardType"] == "USER"]
    property_cards = [card for card in cards if card["cardType"] == "PROPERTY"]
    event_cards = [card for card in cards if card["cardType"] == "EVENT"]
    if len(user_cards) != config["playerCardCount"]:
        problems.append(
            f"Edition '{edition_id}': expected {config['playerCardCount']} Player Cards from edition.json, but found {len(user_cards)}."
        )
    if len(property_cards) != config["propertyCardCount"]:
        problems.append(
            f"Edition '{edition_id}': expected {config['propertyCardCount']} Property Cards in registry, but found {len(property_cards)}."
        )
    if len(event_cards) != config["eventCardCount"]:
        problems.append(
            f"Edition '{edition_id}': expected {config['eventCardCount']} Event Cards in registry, but found {len(event_cards)}."
        )
    expected_total = config["playerCardCount"] + config["propertyCardCount"] + config["eventCardCount"]
    if len(cards) != expected_total:
        problems.append(
            f"Edition '{edition_id}': expected {expected_total} total cards from edition.json, but found {len(cards)}."
        )
    for prop in properties:
        rent_levels = prop.get("rentLevels") or []
        if len(rent_levels) != config["rentLevelsPerProperty"]:
            problems.append(
                f"Edition '{edition_id}', property '{prop['propertyId']}': expected {config['rentLevelsPerProperty']} rent levels, but found {len(rent_levels)}."
            )
    return problems


def load_edition_cards(edition_id: str) -> list[dict]:
    common_cards = load(DATA / "common" / "card_registry.json")["cards"]
    edition_cards = load(DATA / "editions" / edition_id / "card_registry.json")["cards"]
    return common_cards + edition_cards


def main() -> int:
    problems: list[str] = []
    lines: list[str] = ["EDITION VALIDATION", "==================", ""]

    common_cards = DATA / "common" / "card_registry.json"
    common_rules = DATA / "common" / "game_rules.json"
    if not common_cards.is_file():
        problems.append("common/card_registry.json missing")
    if not common_rules.is_file():
        problems.append("common/game_rules.json missing")

    cards = load(common_cards)["cards"] if common_cards.is_file() else []
    user_cards = [card for card in cards if card.get("cardType") == "USER"]
    if cards and any(card.get("cardType") != "USER" for card in cards):
        problems.append("common/card_registry.json must contain USER cards only")
    ids = [c["cardId"] for c in cards]
    payloads = [c["qrPayload"] for c in cards]
    if len(ids) != len(set(ids)):
        problems.append("card IDs are not unique")
    if len(payloads) != len(set(payloads)):
        problems.append("QR payloads are not unique")
    lines += [
        "COMMON",
        f"card_registry: {'PASS' if common_cards.is_file() else 'FAIL'}",
        f"game_rules: {'PASS' if common_rules.is_file() else 'FAIL'}",
        f"cards: {len(cards)} user cards",
        "",
    ]

    uk_props = load(DATA / "editions" / "uk" / "properties.json")["properties"]
    uk_events = load(DATA / "editions" / "uk" / "events.json")["events"]
    uk_banking = load(DATA / "editions" / "uk" / "banking_values.json")
    uk_board = load(DATA / "editions" / "uk" / "board_relationships.json")
    uk_edition = load(DATA / "editions" / "uk" / "edition.json")
    uk_cards = load_edition_cards("uk")
    if uk_edition.get("editionId") != "uk":
        problems.append("UK editionId must be uk")
    if uk_edition.get("definitionVersion") is None:
        problems.append("Edition 'uk': definitionVersion is missing or invalid.")
    problems.extend(validate_card_configuration("uk", uk_edition, uk_props, uk_events, uk_cards))
    for key, expected in UK_BANKING.items():
        if uk_banking.get(key) != expected:
            problems.append(f"UK {key}: expected {expected}, found {uk_banking.get(key)}")
    if uk_banking.get("eventAmounts", {}).get("M50") != 50:
        problems.append("UK event M50 must be 50")
    if uk_banking.get("eventAmounts", {}).get("M200") != 200:
        problems.append("UK event M200 must be 200")
    sides = uk_board.get("boardSides", {}).get("mappings", {})
    expected_sides = {
        "SIDE_1_GO_TO_JAIL": [f"PRP_{i:02d}" for i in range(1, 6)],
        "SIDE_2_JAIL_TO_FREE_PARKING": [f"PRP_{i:02d}" for i in range(6, 12)],
        "SIDE_3_FREE_PARKING_TO_GO_TO_JAIL": [f"PRP_{i:02d}" for i in range(12, 18)],
        "SIDE_4_GO_TO_JAIL_TO_GO": [f"PRP_{i:02d}" for i in range(18, 23)],
    }
    for side, expected in expected_sides.items():
        if sides.get(side) != expected:
            problems.append(f"UK board side mismatch for {side}")

    baseline_path = DATA / "uk_property_baseline.json"
    if baseline_path.is_file():
        baseline = load(baseline_path)["properties"]
        current = {p["propertyId"]: p for p in uk_props}
        if len(baseline) != 22:
            problems.append("UK baseline does not contain 22 properties")
        rent_count = 0
        for item in baseline:
            now = current.get(item["propertyId"])
            if not now:
                problems.append(f"UK missing {item['propertyId']}")
                continue
            if now["name"] != item["name"]:
                problems.append(f"UK name changed for {item['propertyId']}")
            if now["purchasePrice"] != item["purchasePrice"]:
                problems.append(f"UK price changed for {item['propertyId']}")
            if now["rentLevels"] != item["rentLevels"]:
                problems.append(f"UK rents changed for {item['propertyId']}")
            if now["sequence"] != item["sequence"] or now["colorGroup"] != item["colorGroup"]:
                problems.append(f"UK sequence/color changed for {item['propertyId']}")
            rent_count += len(now.get("rentLevels") or [])
        if rent_count != 110:
            problems.append(f"UK rent amount count {rent_count}/110")
    else:
        problems.append("uk_property_baseline.json missing")

    names = {p["name"] for p in uk_props}
    if "Cubbon Park" in names or "Taj Mohalla" in names:
        problems.append("India names leaked into UK properties")

    uk_prop_art = count_fronts(WORKSPACE_ROOT / "Resources" / "Editions" / "uk" / "PropertyCards", "_Front.png")
    uk_event_art = count_fronts(WORKSPACE_ROOT / "Resources" / "Editions" / "uk" / "EventCards", "_Front.png")
    if uk_prop_art < 22:
        problems.append(f"UK property artwork {uk_prop_art}/22")
    if uk_event_art < 23:
        problems.append(f"UK event artwork {uk_event_art}/23")

    lines += [
        "UK",
        f"edition.json: {'PASS' if uk_edition.get('editionId') == 'uk' else 'FAIL'}",
        f"properties: {len(uk_props)} / 22",
        f"events: {len(uk_events)} / 23",
        f"property artwork fronts: {uk_prop_art} / 22",
        f"event artwork fronts: {uk_event_art} / 23",
        "",
    ]

    india_props = load(DATA / "editions" / "india" / "properties.json")["properties"]
    india_events = load(DATA / "editions" / "india" / "events.json")["events"]
    india_banking = load(DATA / "editions" / "india" / "banking_values.json")
    india_edition = load(DATA / "editions" / "india" / "edition.json")
    india_cards = load_edition_cards("india")
    if india_edition.get("editionId") != "india":
        problems.append("India editionId must be india")
    if india_edition.get("definitionVersion") is None:
        problems.append("Edition 'india': definitionVersion is missing or invalid.")
    problems.extend(validate_card_configuration("india", india_edition, india_props, india_events, india_cards))
    prp01 = next(p for p in india_props if p["propertyId"] == "PRP_01")
    if prp01["name"] != "Cubbon Park":
        problems.append("India PRP_01 must be Cubbon Park")
    if prp01["purchasePrice"] != 6000:
        problems.append("India PRP_01 purchase must be 6000")
    rents = [level["amount"] for level in prp01["rentLevels"]]
    if rents != [7000, 13000, 22000, 37000, 75000]:
        problems.append(f"India PRP_01 rents mismatch: {rents}")
    for key, expected in INDIA_BANKING.items():
        if india_banking.get(key) != expected:
            problems.append(f"India {key}: expected {expected}, found {india_banking.get(key)}")
    if india_banking.get("eventAmounts", {}).get("M50") != 5000:
        problems.append("India event M50 must be 5000")
    if india_banking.get("eventAmounts", {}).get("M200") != 20000:
        problems.append("India event M200 must be 20000")

    india_prop_art = count_fronts(WORKSPACE_ROOT / "Resources" / "Editions" / "india" / "PropertyCards", "_Front.png")
    india_event_art = count_fronts(WORKSPACE_ROOT / "Resources" / "Editions" / "india" / "EventCards", "_Front.png")
    india_art_status = "READY" if india_prop_art >= 22 and india_event_art >= 25 else "INCOMPLETE"

    lines += [
        "INDIA",
        f"edition.json: {'PASS' if india_edition.get('editionId') == 'india' else 'FAIL'}",
        f"properties: {len(india_props)} / 22",
        f"events: {len(india_events)} / 25",
        f"property artwork: {india_art_status} ({india_prop_art}/22)",
        f"event artwork: {india_art_status} ({india_event_art}/25)",
        "exposed in New Game: NO",
        "",
    ]

    result = "PASS" if not problems else "FAIL"
    lines += ["RESULT: " + result]
    if problems:
        lines += ["", "Problems:"]
        lines.extend(f"- {item}" for item in problems)
    REPORT.write_text("\n".join(lines) + "\n", encoding="utf-8")
    print(REPORT.read_text(encoding="utf-8"), end="")
    return 0 if not problems else 1


if __name__ == "__main__":
    raise SystemExit(main())
