#!/usr/bin/env python3
"""Validate Step 4 rule specification consistency across docs and data files."""

from __future__ import annotations

import json
import sys
from pathlib import Path

PROJECT_ROOT = Path(__file__).resolve().parent.parent
DATA_DIR = PROJECT_ROOT / "data"
DOCS_DIR = PROJECT_ROOT / "docs"

EXPECTED_PROPERTIES = 22
EXPECTED_EVENTS = 23
EXPECTED_EVENT_ENGINE = 23

ENGINE_RESOLVED_STATUSES = {"RESOLVED", "BOARD_LAYOUT_DERIVED"}
ENGINE_NEEDS_CONFIRMATION: set[str] = set()

CRITICAL_RULE_KEYS = [
    ("setup", "startingBalance"),
    ("setup", "minimumPlayers"),
    ("go", "passingGoPayment"),
    ("go", "landingOnGoPayment"),
    ("jail", "jailPaymentAmount"),
    ("properties", "ownerOnOwnPropertyRule"),
    ("auction", "rulesDefined"),
    ("locationSpaces", "rulesDefined"),
    ("debt", "insufficientFundsFlow"),
    ("bankruptcy", "triggerRules"),
    ("endGame", "endCondition"),
]


def load_json(path: Path) -> dict:
    with path.open(encoding="utf-8") as handle:
        return json.load(handle)


def validate_property_ids(properties: list[dict]) -> list[str]:
    errors = []
    expected = {f"PRP_{index:02d}" for index in range(1, EXPECTED_PROPERTIES + 1)}
    found = {item["propertyId"] for item in properties}
    missing = expected - found
    extra = found - expected
    if missing:
        errors.append(f"Missing property IDs: {sorted(missing)}")
    if extra:
        errors.append(f"Unexpected property IDs: {sorted(extra)}")
    return errors


def validate_event_ids(events: list[dict]) -> list[str]:
    errors = []
    expected = {f"EVT_{index:02d}" for index in range(1, EXPECTED_EVENTS + 1)}
    found = {item["eventId"] for item in events}
    missing = expected - found
    extra = found - expected
    if missing:
        errors.append(f"Missing event IDs: {sorted(missing)}")
    if extra:
        errors.append(f"Unexpected event IDs: {sorted(extra)}")
    return errors


def validate_color_groups(properties: list[dict], board_relationships: dict) -> list[str]:
    errors = []
    color_groups = board_relationships.get("colorGroups", {})
    all_grouped = {property_id for ids in color_groups.values() for property_id in ids}
    property_ids = {item["propertyId"] for item in properties}
    if all_grouped != property_ids:
        errors.append("Color group property IDs do not match properties.json")
    for item in properties:
        group = item.get("colorGroup")
        if group not in color_groups:
            errors.append(f"{item['propertyId']} has unknown colorGroup {group}")
        elif item["propertyId"] not in color_groups[group]:
            errors.append(f"{item['propertyId']} not listed in color group {group}")
    return errors


def validate_neighbours(board_relationships: dict) -> list[str]:
    errors = []
    neighbours = board_relationships.get("neighbours", {})
    if neighbours.get("status") != "RESOLVED":
        errors.append("Neighbour relationships are not RESOLVED")
        return errors
    mappings = neighbours.get("mappings", {})
    for index in range(1, EXPECTED_PROPERTIES + 1):
        property_id = f"PRP_{index:02d}"
        if property_id not in mappings:
            errors.append(f"Missing neighbour mapping for {property_id}")
            continue
        expected_prev = f"PRP_{index - 1:02d}" if index > 1 else "PRP_22"
        expected_next = f"PRP_{index + 1:02d}" if index < EXPECTED_PROPERTIES else "PRP_01"
        if set(mappings[property_id]) != {expected_prev, expected_next}:
            errors.append(f"Incorrect neighbours for {property_id}: {mappings[property_id]}")
    return errors


def validate_board_sides(board_relationships: dict) -> list[str]:
    errors = []
    board_sides = board_relationships.get("boardSides", {})
    if board_sides.get("status") != "RESOLVED":
        errors.append("Board side relationships are not RESOLVED")
        return errors
    expected_groups = {
        "SIDE_1_GO_TO_JAIL": [f"PRP_{index:02d}" for index in range(1, 6)],
        "SIDE_2_JAIL_TO_FREE_PARKING": [f"PRP_{index:02d}" for index in range(6, 12)],
        "SIDE_3_FREE_PARKING_TO_GO_TO_JAIL": [f"PRP_{index:02d}" for index in range(12, 18)],
        "SIDE_4_GO_TO_JAIL_TO_GO": [f"PRP_{index:02d}" for index in range(18, 23)],
    }
    mappings = board_sides.get("mappings", {})
    for side, properties in expected_groups.items():
        if mappings.get(side) != properties:
            errors.append(f"Board side mapping mismatch for {side}")
    property_to_side = board_sides.get("propertyToSide", {})
    for property_id, side in property_to_side.items():
        if property_id not in mappings.get(side, []):
            errors.append(f"{property_id} propertyToSide does not match side membership")
    return errors


def validate_event_engine(events: list[dict], engine_rules: list[dict]) -> list[str]:
    errors = []
    if len(engine_rules) != EXPECTED_EVENT_ENGINE:
        errors.append(
            f"Expected {EXPECTED_EVENT_ENGINE} event engine records, found {len(engine_rules)}"
        )
    events_by_id = {item["eventId"]: item for item in events}
    for record in engine_rules:
        event_id = record["eventId"]
        if event_id not in events_by_id:
            errors.append(f"Event engine record references unknown event {event_id}")
            continue
        if record.get("printedTextValidated") is not True:
            errors.append(f"{event_id} engine record missing printedTextValidated=true")
        engine_status = record.get("engineStatus")
        if event_id in ENGINE_NEEDS_CONFIRMATION:
            if engine_status != "NEEDS_CONFIRMATION":
                errors.append(f"{event_id} engineStatus={engine_status} expected NEEDS_CONFIRMATION")
        elif engine_status not in ENGINE_RESOLVED_STATUSES:
            errors.append(f"{event_id} engineStatus={engine_status} is not executable")
        if engine_status in ENGINE_RESOLVED_STATUSES and record.get("actionType") in {
            None,
            "",
            "TO_BE_CONFIRMED",
        }:
            errors.append(f"{event_id} marked executable but actionType is unresolved")
    return errors


def validate_game_rules(game_rules: dict) -> list[str]:
    errors = []
    for section, key in CRITICAL_RULE_KEYS:
        section_data = game_rules.get(section, {})
        status_key = f"{key}Status" if f"{key}Status" in section_data else "status"
        status = section_data.get(status_key)
        value = section_data.get(key)
        if status == "NEEDS_CONFIRMATION" and isinstance(value, (int, float)) and value > 0:
            errors.append(f"{section}.{key} has fabricated numeric value {value}")
        if status == "RESOLVED" and value in (None, False) and key.endswith("rulesDefined"):
            errors.append(f"{section}.{key} marked RESOLVED but value is unresolved")
    if game_rules.get("setup", {}).get("startingBalance") != 1500:
        errors.append("startingBalance must be 1500")
    if game_rules.get("setup", {}).get("minimumPlayers") != 2:
        errors.append("minimumPlayers must be 2")
    return errors


def count_resolved_rules(game_rules: dict) -> tuple[int, int]:
    resolved = 0
    needs_confirmation = 0

    def walk(node: object) -> None:
        nonlocal resolved, needs_confirmation
        if isinstance(node, dict):
            if node.get("status") == "NEEDS_CONFIRMATION":
                needs_confirmation += 1
            for key, value in node.items():
                if key.endswith("Status"):
                    if value == "RESOLVED":
                        resolved += 1
                    elif value == "BOARD_LAYOUT_DERIVED":
                        resolved += 1
                    elif value == "NEEDS_CONFIRMATION":
                        needs_confirmation += 1
                else:
                    walk(value)
        elif isinstance(node, list):
            for item in node:
                walk(item)

    walk(game_rules)
    return resolved, needs_confirmation


def count_critical_blockers(game_rules: dict) -> int:
    blockers = 0
    for section, key in CRITICAL_RULE_KEYS:
        section_data = game_rules.get(section, {})
        status_key = f"{key}Status" if f"{key}Status" in section_data else "status"
        if section_data.get(status_key) == "NEEDS_CONFIRMATION":
            blockers += 1
    return blockers


def validate_docs_exist() -> list[str]:
    required = [
        DOCS_DIR / "GAME_RULES.md",
        DOCS_DIR / "EVENT_ENGINE_RULES.md",
        DOCS_DIR / "TRANSACTION_RULES.md",
        DOCS_DIR / "GAME_ENGINE_DESIGN.md",
        DOCS_DIR / "RULE_TEST_SCENARIOS.md",
        DOCS_DIR / "RULE_GAPS.md",
    ]
    return [f"Missing document: {path}" for path in required if not path.exists()]


def validate_game_rules_doc() -> list[str]:
    errors = []
    content = (DOCS_DIR / "GAME_RULES.md").read_text(encoding="utf-8")
    required_prefixes = [
        "GR-SETUP-",
        "GR-PLAYER-",
        "GR-PROPERTY-",
        "GR-RENT-",
        "GR-BANK-",
        "GR-EVENT-",
        "GR-GO-",
        "GR-JAIL-",
        "GR-LOCATION-",
        "GR-AUCTION-",
        "GR-DEBT-",
        "GR-BANKRUPTCY-",
        "GR-ENDGAME-",
        "GR-UNDO-",
        "GR-SAVE-",
        "GR-COLORSET-",
    ]
    for prefix in required_prefixes:
        if prefix not in content:
            errors.append(f"GAME_RULES.md missing rule category prefix {prefix}")
    return errors


def write_report(
    *,
    structural_pass: bool,
    property_count: int,
    event_count: int,
    engine_count: int,
    resolved_engine: int,
    board_layout_derived_engine: int,
    needs_engine: int,
    resolved_global: int,
    needs_global: int,
    critical_blockers: int,
    errors: list[str],
) -> None:
    readiness = (
        "READY_FOR_IMPLEMENTATION"
        if structural_pass and critical_blockers == 0 and needs_engine == 0
        else "BLOCKED_BY_RULE_CONFIRMATION"
    )
    lines = [
        "RULE SPECIFICATION VALIDATION",
        "=============================",
        "",
        f"Property definitions: {property_count}/{EXPECTED_PROPERTIES}",
        f"Event definitions:    {event_count}/{EXPECTED_EVENTS}",
        f"Event engine records: {engine_count}/{EXPECTED_EVENT_ENGINE}",
        "",
        "Structural validation:",
        "PASS" if structural_pass and not errors else "FAIL",
        "",
        f"Resolved engine rules: {resolved_engine}",
        f"Board-layout-derived engine rules: {board_layout_derived_engine}",
        f"Rules needing confirmation: {needs_engine}",
        "",
        f"Global banking/game rules resolved: {resolved_global}",
        f"Global banking/game rules needing confirmation: {needs_global}",
        "",
        f"Critical implementation blockers: {critical_blockers}",
        f"Event implementation gaps: {needs_engine}",
        "",
        "IMPLEMENTATION READINESS:",
        readiness,
        "",
    ]
    if errors:
        lines.append("ERRORS:")
        lines.extend(f"- {error}" for error in errors)
        lines.append("")
    output = DATA_DIR / "rule_spec_validation.txt"
    output.write_text("\n".join(lines), encoding="utf-8")
    print(output.read_text(encoding="utf-8"))


def main() -> int:
    errors: list[str] = []
    errors.extend(validate_docs_exist())
    errors.extend(validate_game_rules_doc())

    properties = load_json(DATA_DIR / "editions" / "uk" / "properties.json")["properties"]
    events = load_json(DATA_DIR / "editions" / "uk" / "events.json")["events"]
    engine_data = load_json(DATA_DIR / "common" / "event_engine_rules.json")
    game_rules = load_json(DATA_DIR / "common" / "game_rules.json")
    board_relationships = load_json(DATA_DIR / "editions" / "uk" / "board_relationships.json")

    property_count = len(properties)
    event_count = len(events)
    engine_count = len(engine_data["events"])

    if property_count != EXPECTED_PROPERTIES:
        errors.append(f"Expected {EXPECTED_PROPERTIES} properties, found {property_count}")
    if event_count != EXPECTED_EVENTS:
        errors.append(f"Expected {EXPECTED_EVENTS} events, found {event_count}")

    errors.extend(validate_property_ids(properties))
    errors.extend(validate_event_ids(events))
    errors.extend(validate_color_groups(properties, board_relationships))
    errors.extend(validate_neighbours(board_relationships))
    errors.extend(validate_board_sides(board_relationships))
    errors.extend(validate_event_engine(events, engine_data["events"]))
    errors.extend(validate_game_rules(game_rules))

    resolved_engine = sum(
        1 for item in engine_data["events"] if item.get("engineStatus") == "RESOLVED"
    )
    board_layout_derived_engine = sum(
        1
        for item in engine_data["events"]
        if item.get("engineStatus") == "BOARD_LAYOUT_DERIVED"
    )
    needs_engine = sum(
        1 for item in engine_data["events"] if item.get("engineStatus") == "NEEDS_CONFIRMATION"
    )
    resolved_global, needs_global = count_resolved_rules(game_rules)
    critical_blockers = count_critical_blockers(game_rules)
    structural_pass = not any(
        error
        for error in errors
        if "Missing" in error or "Expected" in error or "do not match" in error
    )

    write_report(
        structural_pass=structural_pass and not errors,
        property_count=property_count,
        event_count=event_count,
        engine_count=engine_count,
        resolved_engine=resolved_engine,
        board_layout_derived_engine=board_layout_derived_engine,
        needs_engine=needs_engine,
        resolved_global=resolved_global,
        needs_global=needs_global,
        critical_blockers=critical_blockers,
        errors=errors,
    )
    return 0 if not errors else 1


if __name__ == "__main__":
    sys.exit(main())
