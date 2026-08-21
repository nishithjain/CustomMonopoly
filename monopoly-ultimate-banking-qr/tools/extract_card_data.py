#!/usr/bin/env python3
"""Development utility: build property and event master data from card registry + extractions."""

from __future__ import annotations

import csv
import json
import sys
from pathlib import Path

from game_data_extractions import EVENT_EXTRACTIONS, PROPERTY_EXTRACTIONS

WORKSPACE_ROOT = Path(__file__).resolve().parent.parent.parent
PROJECT_ROOT = Path(__file__).resolve().parent.parent
DATA_DIR = PROJECT_ROOT / "data"


def load_cards() -> list[dict]:
    with (DATA_DIR / "cards.json").open(encoding="utf-8") as handle:
        return json.load(handle)["cards"]


def build_properties(cards: list[dict]) -> list[dict]:
    properties: list[dict] = []
    for card in cards:
        if card["cardType"] != "PROPERTY":
            continue
        extraction = PROPERTY_EXTRACTIONS.get(card["cardId"])
        if not extraction:
            raise KeyError(f"Missing property extraction for {card['cardId']}")

        front_path = WORKSPACE_ROOT / card["assets"]["front"]
        if not front_path.exists():
            print(f"WARNING: front asset missing for {card['cardId']}: {front_path}", file=sys.stderr)

        properties.append(
            {
                "propertyId": card["cardId"],
                "name": extraction.get("displayName", card["name"]),
                "sequence": card["sequence"],
                "qrPayload": card["qrPayload"],
                "frontAsset": card["assets"]["front"],
                "qrAsset": card["assets"]["qr"],
                "colorGroup": extraction["colorGroup"],
                "colorGroupLabel": extraction["colorGroupLabel"],
                "purchasePrice": extraction["purchasePrice"],
                "initialRentLevel": extraction.get("initialRentLevel"),
                "rentLevels": extraction["rentLevels"],
                "maximumRentLevel": extraction["maximumRentLevel"],
            }
        )

    properties.sort(key=lambda item: item["sequence"])
    return properties


def build_events(cards: list[dict]) -> list[dict]:
    events: list[dict] = []
    for card in cards:
        if card["cardType"] != "EVENT":
            continue
        extraction = EVENT_EXTRACTIONS.get(card["cardId"])
        if not extraction:
            raise KeyError(f"Missing event extraction for {card['cardId']}")

        front_path = WORKSPACE_ROOT / card["assets"]["front"]
        if not front_path.exists():
            print(f"WARNING: front asset missing for {card['cardId']}: {front_path}", file=sys.stderr)

        events.append(
            {
                "eventId": card["cardId"],
                "sequence": card["sequence"],
                "name": extraction.get("displayName", card["name"]),
                "qrPayload": card["qrPayload"],
                "frontAsset": card["assets"]["front"],
                "qrAsset": card["assets"]["qr"],
                "eventSubtitle": extraction.get("eventSubtitle", ""),
                "eventDescription": extraction.get(
                    "eventDescription",
                    extraction.get("printedText", ""),
                ),
                "printedTextValidated": extraction.get("printedTextValidated", False),
                "printedRuleStatus": extraction.get("printedRuleStatus", "NEEDS_REVIEW"),
                "engineImplementationStatus": extraction.get("engineImplementationStatus", "NEEDS_REVIEW"),
                "effectClassification": extraction["effectClassification"],
                "parsedEffect": extraction["parsedEffect"],
                "requiresPlayerScan": extraction["requiresPlayerScan"],
                "requiresPropertyScan": extraction["requiresPropertyScan"],
                "extractionStatus": extraction["extractionStatus"],
                "notes": extraction["notes"],
            }
        )

    events.sort(key=lambda item: item["sequence"])
    return events


def write_properties_json(properties: list[dict]) -> None:
    output = DATA_DIR / "properties.json"
    with output.open("w", encoding="utf-8") as handle:
        json.dump({"schemaVersion": 1, "properties": properties}, handle, indent=2)
        handle.write("\n")
    print(f"Wrote {output}")


def write_events_json(events: list[dict]) -> None:
    output = DATA_DIR / "events.json"
    with output.open("w", encoding="utf-8") as handle:
        json.dump({"schemaVersion": 1, "events": events}, handle, indent=2)
        handle.write("\n")
    print(f"Wrote {output}")


def write_properties_csv(properties: list[dict]) -> None:
    output = DATA_DIR / "properties.csv"
    fieldnames = [
        "propertyId",
        "name",
        "sequence",
        "qrPayload",
        "frontAsset",
        "qrAsset",
        "colorGroup",
        "colorGroupLabel",
        "purchasePrice",
        "initialRentLevel",
        "maximumRentLevel",
        "rentLevels",
    ]
    rows = []
    for prop in properties:
        row = dict(prop)
        row["rentLevels"] = json.dumps(prop["rentLevels"], ensure_ascii=False)
        rows.append(row)

    with output.open("w", newline="", encoding="utf-8") as handle:
        writer = csv.DictWriter(handle, fieldnames=fieldnames)
        writer.writeheader()
        writer.writerows(rows)
    print(f"Wrote {output}")


def write_events_csv(events: list[dict]) -> None:
    output = DATA_DIR / "events.csv"
    fieldnames = [
        "eventId",
        "name",
        "sequence",
        "qrPayload",
        "frontAsset",
        "qrAsset",
        "eventSubtitle",
        "eventDescription",
        "printedTextValidated",
        "printedRuleStatus",
        "engineImplementationStatus",
        "effectClassification",
        "parsedEffect",
        "requiresPlayerScan",
        "requiresPropertyScan",
        "extractionStatus",
        "notes",
    ]
    rows = []
    for event in events:
        row = dict(event)
        row["effectClassification"] = json.dumps(event["effectClassification"], ensure_ascii=False)
        row["parsedEffect"] = json.dumps(event["parsedEffect"], ensure_ascii=False)
        rows.append(row)

    with output.open("w", newline="", encoding="utf-8") as handle:
        writer = csv.DictWriter(handle, fieldnames=fieldnames)
        writer.writeheader()
        writer.writerows(rows)
    print(f"Wrote {output}")


def format_rent_levels(rent_levels: list[dict]) -> str:
    return ", ".join(f"L{level['level']}={level['amount']}" for level in rent_levels)


def write_property_report(properties: list[dict]) -> None:
    output = DATA_DIR / "property_extraction_report.txt"
    issues: list[str] = []

    lines = [
        "PROPERTY EXTRACTION REPORT",
        "==========================",
        "",
        "Expected properties: 22",
        f"Processed: {len(properties)}",
        "",
    ]

    for prop in properties:
        purchase = prop["purchasePrice"] if prop["purchasePrice"] is not None else "TO_BE_CONFIRMED"
        initial_rent = prop.get("initialRentLevel", "TO_BE_CONFIRMED")
        lines.extend(
            [
                f"{prop['propertyId']} {prop['name']}",
                f"Purchase Price: {purchase}",
                f"Initial Rent Level: {initial_rent}",
                f"Rent Levels: {format_rent_levels(prop['rentLevels'])}",
                f"Color Group: {prop['colorGroup']} ({prop['colorGroupLabel']})",
                "",
            ]
        )
        if prop["purchasePrice"] is None:
            issues.append(f"{prop['propertyId']}: purchase price missing")

    lines.append("Issues:")
    if issues:
        lines.extend(f"- {issue}" for issue in issues)
    else:
        lines.append("- None")

    output.write_text("\n".join(lines) + "\n", encoding="utf-8")
    print(f"Wrote {output}")


def write_event_report(events: list[dict]) -> None:
    output = DATA_DIR / "event_extraction_report.txt"
    status_counts = {"COMPLETE": 0, "NEEDS_REVIEW": 0, "UNREADABLE": 0}
    issues: list[str] = []

    lines = [
        "EVENT EXTRACTION REPORT",
        "=======================",
        "",
        "Expected events: 23",
        f"Processed: {len(events)}",
        "",
    ]

    for event in events:
        status_counts[event["extractionStatus"]] = status_counts.get(event["extractionStatus"], 0) + 1

    lines.extend(
        [
            f"COMPLETE: {status_counts.get('COMPLETE', 0)}",
            f"NEEDS_REVIEW: {status_counts.get('NEEDS_REVIEW', 0)}",
            f"UNREADABLE: {status_counts.get('UNREADABLE', 0)}",
            "",
        ]
    )

    for event in events:
        lines.extend(
            [
                f"{event['eventId']} {event['name']}",
                f"Status: {event['extractionStatus']}",
                f"Subtitle: {event.get('eventSubtitle', '')}",
                f"Description: {event.get('eventDescription', '')}",
                f"Printed rule: {event.get('printedRuleStatus', 'UNKNOWN')}",
                f"Engine implementation: {event.get('engineImplementationStatus', 'UNKNOWN')}",
                f"Classification: {', '.join(event['effectClassification'])}",
                f"Requires Player Scan: {event['requiresPlayerScan']}",
                f"Requires Property Scan: {event['requiresPropertyScan']}",
                "",
            ]
        )
        if event["extractionStatus"] != "COMPLETE":
            issues.append(f"{event['eventId']}: {event['notes']}")
        if event["parsedEffect"].get("action") == "TO_BE_CONFIRMED":
            issues.append(f"{event['eventId']}: parsed effect action not confirmed")

    lines.append("Issues:")
    if issues:
        lines.extend(f"- {issue}" for issue in issues)
    else:
        lines.append("- None")

    output.write_text("\n".join(lines) + "\n", encoding="utf-8")
    print(f"Wrote {output}")


def main() -> int:
    cards = load_cards()
    properties = build_properties(cards)
    events = build_events(cards)

    if len(properties) != 22:
        print(f"ERROR: expected 22 properties, got {len(properties)}", file=sys.stderr)
        return 1
    if len(events) != 23:
        print(f"ERROR: expected 23 events, got {len(events)}", file=sys.stderr)
        return 1

    write_properties_json(properties)
    write_events_json(events)
    write_properties_csv(properties)
    write_events_csv(events)
    write_property_report(properties)
    write_event_report(events)

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
