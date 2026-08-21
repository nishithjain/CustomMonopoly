#!/usr/bin/env python3
"""Validate property and event master data structural integrity and content completeness."""

from __future__ import annotations

import csv
import json
import sys
from pathlib import Path

WORKSPACE_ROOT = Path(__file__).resolve().parent.parent.parent
PROJECT_ROOT = Path(__file__).resolve().parent.parent
DATA_DIR = PROJECT_ROOT / "data"

EXPECTED_PROPERTIES = 22
EXPECTED_EVENTS = 23
VALID_STATUSES = {"COMPLETE", "NEEDS_REVIEW", "UNREADABLE"}


def load_json(path: Path, key: str) -> list[dict]:
    with path.open(encoding="utf-8") as handle:
        return json.load(handle)[key]


def load_cards() -> list[dict]:
    return load_json(DATA_DIR / "common" / "card_registry.json", "cards")


def validate_properties(properties: list[dict], cards: list[dict]) -> tuple[list[str], dict]:
    problems: list[str] = []
    stats = {
        "count": len(properties),
        "complete": 0,
        "needs_review": 0,
        "unreadable": 0,
        "with_purchase_price": 0,
        "with_initial_rent_level": 0,
        "with_rent_levels": 0,
        "with_color_group": 0,
    }

    registry_props = {card["cardId"]: card for card in cards if card["cardType"] == "PROPERTY"}

    if len(properties) != EXPECTED_PROPERTIES:
        problems.append(f"Property count: expected {EXPECTED_PROPERTIES}, found {len(properties)}")

    ids = [prop["propertyId"] for prop in properties]
    if len(set(ids)) != len(ids):
        problems.append("Duplicate property IDs found")

    expected_ids = {f"PRP_{index:02d}" for index in range(1, 23)}
    missing_ids = sorted(expected_ids - set(ids))
    extra_ids = sorted(set(ids) - expected_ids)
    if missing_ids:
        problems.append(f"Missing property IDs: {', '.join(missing_ids)}")
    if extra_ids:
        problems.append(f"Unexpected property IDs: {', '.join(extra_ids)}")

    qr_payloads = [prop.get("qrPayload", "") for prop in properties if prop.get("qrPayload")]
    if len(qr_payloads) != len(set(qr_payloads)):
        problems.append("Duplicate property QR payloads found")

    for prop in properties:
        registry = registry_props.get(prop["propertyId"])
        if not registry:
            problems.append(f"{prop['propertyId']}: not in card registry")
            continue

        if prop.get("name") != registry["name"]:
            problems.append(
                f"{prop['propertyId']}: name mismatch registry='{registry['name']}' data='{prop.get('name')}'"
            )
        if prop.get("qrPayload") != registry["qrPayload"]:
            problems.append(f"{prop['propertyId']}: qrPayload mismatch with card registry")

        for asset_key in ("frontAsset", "qrAsset"):
            asset_path = prop.get(asset_key, "")
            if not asset_path:
                problems.append(f"{prop['propertyId']}: missing {asset_key}")
            elif not (WORKSPACE_ROOT / asset_path).exists():
                problems.append(f"{prop['propertyId']}: {asset_key} not found: {asset_path}")

        if prop.get("purchasePrice") is not None:
            stats["with_purchase_price"] += 1
        if prop.get("initialRentLevel") is not None:
            stats["with_initial_rent_level"] += 1
        if prop.get("rentLevels"):
            stats["with_rent_levels"] += 1
        if prop.get("colorGroup") and prop["colorGroup"] != "TO_BE_CONFIRMED":
            stats["with_color_group"] += 1
        if (
            prop.get("purchasePrice") is not None
            and prop.get("rentLevels")
            and prop.get("colorGroup")
            and prop["colorGroup"] != "TO_BE_CONFIRMED"
        ):
            stats["complete"] += 1

    return problems, stats


def validate_events(events: list[dict], cards: list[dict]) -> tuple[list[str], dict]:
    problems: list[str] = []
    stats = {
        "count": len(events),
        "complete": 0,
        "needs_review": 0,
        "unreadable": 0,
        "engine_complete": 0,
        "engine_needs_review": 0,
    }

    registry_events = {card["cardId"]: card for card in cards if card["cardType"] == "EVENT"}

    if len(events) != EXPECTED_EVENTS:
        problems.append(f"Event count: expected {EXPECTED_EVENTS}, found {len(events)}")

    ids = [event["eventId"] for event in events]
    if len(set(ids)) != len(ids):
        problems.append("Duplicate event IDs found")

    expected_ids = {f"EVT_{index:02d}" for index in range(1, 24)}
    missing_ids = sorted(expected_ids - set(ids))
    extra_ids = sorted(set(ids) - expected_ids)
    if missing_ids:
        problems.append(f"Missing event IDs: {', '.join(missing_ids)}")
    if extra_ids:
        problems.append(f"Unexpected event IDs: {', '.join(extra_ids)}")

    qr_payloads = [event.get("qrPayload", "") for event in events if event.get("qrPayload")]
    if len(qr_payloads) != len(set(qr_payloads)):
        problems.append("Duplicate event QR payloads found")

    for event in events:
        status = event.get("extractionStatus")
        if status not in VALID_STATUSES:
            problems.append(f"{event['eventId']}: invalid extractionStatus '{status}'")
        elif status == "COMPLETE":
            stats["complete"] += 1
        elif status == "NEEDS_REVIEW":
            stats["needs_review"] += 1
        elif status == "UNREADABLE":
            stats["unreadable"] += 1

        engine_status = event.get("engineImplementationStatus")
        if engine_status == "COMPLETE":
            stats["engine_complete"] += 1
        elif engine_status == "NEEDS_REVIEW":
            stats["engine_needs_review"] += 1

        registry = registry_events.get(event["eventId"])
        if not registry:
            problems.append(f"{event['eventId']}: not in card registry")
            continue

        if event.get("name") != registry["name"]:
            problems.append(
                f"{event['eventId']}: name mismatch registry='{registry['name']}' data='{event.get('name')}'"
            )
        if event.get("qrPayload") != registry["qrPayload"]:
            problems.append(f"{event['eventId']}: qrPayload mismatch with card registry")

        for asset_key in ("frontAsset", "qrAsset"):
            asset_path = event.get(asset_key, "")
            if not asset_path:
                problems.append(f"{event['eventId']}: missing {asset_key}")
            elif not (WORKSPACE_ROOT / asset_path).exists():
                problems.append(f"{event['eventId']}: {asset_key} not found: {asset_path}")

        subtitle = event.get("eventSubtitle", "")
        description = event.get("eventDescription", "") or event.get("printedText", "")
        if status == "UNREADABLE" and not description and not subtitle:
            pass
        elif not description:
            problems.append(f"{event['eventId']}: missing eventDescription")
        if event.get("printedTextValidated") is not True:
            problems.append(f"{event['eventId']}: printedText not validated")
        if event.get("printedRuleStatus") != "RESOLVED":
            problems.append(f"{event['eventId']}: printedRuleStatus not RESOLVED")
        if event.get("engineImplementationStatus") not in {"COMPLETE", "NEEDS_REVIEW"}:
            problems.append(
                f"{event['eventId']}: invalid engineImplementationStatus "
                f"'{event.get('engineImplementationStatus')}'"
            )

    return problems, stats


def write_report(
    property_problems: list[str],
    event_problems: list[str],
    property_stats: dict,
    event_stats: dict,
) -> Path:
    structural_problems = property_problems + event_problems
    structural_result = "PASS" if not structural_problems else "FAIL"

    content_review_required = (
        property_stats["needs_review"] > 0
        or property_stats["unreadable"] > 0
        or event_stats["engine_needs_review"] > 0
        or event_stats["unreadable"] > 0
        or property_stats["with_purchase_price"] < EXPECTED_PROPERTIES
    )
    content_result = "REVIEW REQUIRED" if content_review_required else "COMPLETE"

    lines = [
        "GAME DATA VALIDATION",
        "====================",
        "",
        "STRUCTURAL VALIDATION",
        "",
        "Properties",
        f"Expected: {EXPECTED_PROPERTIES}",
        f"Found:    {property_stats['count']}",
        "",
        "Events",
        f"Expected: {EXPECTED_EVENTS}",
        f"Found:    {event_stats['count']}",
        "",
        "Property IDs valid: PASS" if not any("property ID" in p.lower() or "property count" in p.lower() for p in property_problems) else "Property IDs valid: FAIL",
        "Event IDs valid: PASS" if not any("event id" in p.lower() or "event count" in p.lower() for p in event_problems) else "Event IDs valid: FAIL",
        "QR mappings valid: PASS" if not any("qr" in p.lower() for p in structural_problems) else "QR mappings valid: FAIL",
        "Asset references valid: PASS" if not any("asset" in p.lower() or "not found" in p.lower() for p in structural_problems) else "Asset references valid: FAIL",
        "",
        f"STRUCTURAL RESULT: {structural_result}",
        "",
        "",
        "CONTENT COMPLETENESS",
        "",
        f"Properties complete:     {property_stats['complete']}",
        f"Properties needs review: {property_stats['needs_review']}",
        f"Properties unreadable:   {property_stats['unreadable']}",
        "",
        f"Events complete:         {event_stats['complete']}",
        f"Events needs review:     {event_stats['needs_review']}",
        f"Events unreadable:       {event_stats['unreadable']}",
        "",
        f"Event engine behaviour complete:     {event_stats['engine_complete']}",
        f"Event engine behaviour needs review: {event_stats['engine_needs_review']}",
        "",
        f"Properties with purchasePrice: {property_stats['with_purchase_price']} / {EXPECTED_PROPERTIES}",
        f"Properties with initialRentLevel: {property_stats['with_initial_rent_level']} / {EXPECTED_PROPERTIES}",
        f"Properties with rentLevels:    {property_stats['with_rent_levels']} / {EXPECTED_PROPERTIES}",
        f"Properties with colorGroup:    {property_stats['with_color_group']} / {EXPECTED_PROPERTIES}",
        "",
        "CONTENT RESULT:",
        content_result,
    ]

    if structural_problems:
        lines.extend(["", "Structural problems:", "--------------------"])
        lines.extend(f"- {problem}" for problem in structural_problems)

    output = DATA_DIR / "game_data_validation.txt"
    output.write_text("\n".join(lines) + "\n", encoding="utf-8")
    print(f"Wrote {output}")
    return output


def main() -> int:
    cards = load_cards()
    properties = load_json(DATA_DIR / "editions" / "uk" / "properties.json", "properties")
    events = load_json(DATA_DIR / "editions" / "uk" / "events.json", "events")

    property_problems, property_stats = validate_properties(properties, cards)
    event_problems, event_stats = validate_events(events, cards)
    write_report(property_problems, event_problems, property_stats, event_stats)

    all_problems = property_problems + event_problems
    if all_problems:
        print(f"Structural validation FAILED with {len(all_problems)} problem(s).")
        for problem in all_problems:
            print(f"  - {problem}")
        return 1

    print("Structural validation PASSED.")
    if property_stats["needs_review"] or event_stats["needs_review"]:
        print("Content completeness: REVIEW REQUIRED.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
