#!/usr/bin/env python3
"""Build machine-readable event engine rules from validated Step 3 event data."""

from __future__ import annotations

import json
from pathlib import Path

PROJECT_ROOT = Path(__file__).resolve().parent.parent
DATA_DIR = PROJECT_ROOT / "data"

ACTION_TYPE_MAP = {
    "MOVE_THEN_PROPERTY_CHOICE": "MOVE_THEN_PROPERTY_CHOICE",
    "INCREASE_COLOR_SET_RENT_LEVEL": "INCREASE_COLOR_SET_RENT_LEVEL",
    "RESET_PROPERTY_RENT_LEVEL": "RESET_PROPERTY_RENT_LEVEL",
    "SET_PROPERTY_RENT_LEVEL": "SET_PROPERTY_RENT_LEVEL",
    "SWAP_PROPERTIES_BETWEEN_PLAYERS": "SWAP_PROPERTIES",
    "PAY_PER_OWNED_PROPERTY": "PAY_PER_OWNED_PROPERTY",
    "CREDIT_BOTH_PLAYERS": "CREDIT_BOTH_PLAYERS",
    "TEMPORARY_RENT_CAP": "TEMPORARY_RENT_CAP",
    "SEND_PLAYER_TO_JAIL": "SEND_PLAYER_TO_JAIL",
    "DECREASE_COLOR_SET_RENT_LEVEL": "DECREASE_COLOR_SET_RENT_LEVEL",
    "MOVE_ALL_TO_FREE_PARKING": "TOTAL_GRIDLOCK_V1",
    "TO_BE_CONFIRMED": "TO_BE_CONFIRMED",
}

TARGET_TYPE_MAP = {
    "SELECTED_PROPERTY": "SELECTED_PROPERTY",
    "OWNED_PROPERTY": "OWNED_PROPERTY",
    "ANY_PROPERTY": "ANY_PROPERTY",
    "TWO_PLAYERS_AND_PROPERTIES": "TWO_PLAYERS_AND_PROPERTIES",
    "CURRENT_PLAYER": "CURRENT_PLAYER",
    "CURRENT_PLAYER_AND_CHOSEN_PLAYER": "TWO_PLAYERS",
    "ALL_PLAYERS": "ALL_PLAYERS",
    "OTHER_PLAYER": "OTHER_PLAYER",
    "OWNED_PROPERTY_AND_NEIGHBOURS": "NEIGHBOURS_OF_SELECTED_PROPERTY",
    "BOARD_SIDE": "BOARD_SIDE_OF_SELECTED_PROPERTY",
}

EVENT_OVERRIDES = {
    "EVT_08": {
        "engineStatus": "RESOLVED",
        "actionType": "ADJUST_SELECTED_AND_NEIGHBOUR_RENT_LEVELS",
        "targetType": "NEIGHBOURS_OF_SELECTED_PROPERTY",
        "parameters": {
            "selectedPropertyRentChange": 1,
            "neighbourRentChange": -1,
            "ownedOnly": True,
        },
        "notes": (
            "Selected owned property +1; each owned neighbour -1. "
            "Neighbours from board_relationships.json. Unowned neighbours unchanged."
        ),
    },
    "EVT_10": {
        "engineStatus": "RESOLVED",
        "actionType": "ADJUST_SELECTED_AND_NEIGHBOUR_RENT_LEVELS",
        "targetType": "NEIGHBOURS_OF_SELECTED_PROPERTY",
        "parameters": {
            "selectedPropertyRentChange": 1,
            "neighbourRentChange": -1,
            "ownedOnly": True,
        },
        "notes": (
            "Same neighbour model as EVT_08. Selected owned property +1; "
            "each owned neighbour -1."
        ),
    },
    "EVT_13": {
        "engineStatus": "RESOLVED",
        "actionType": "TEMPORARY_RENT_CAP",
        "targetType": "ALL_PLAYERS",
        "temporaryEffect": True,
        "parameters": {
            "effectType": "FORCE_LEVEL_1_RENT",
            "durationRentPayments": 2,
            "scope": "GLOBAL",
            "maxRentLevelCharged": 1,
            "doesNotResetStoredRentLevel": True,
            "ownerLandingDoesNotConsume": True,
        },
        "notes": (
            "Global temporary effect: next two completed rent payments charge Level 1 "
            "rent regardless of stored currentRentLevel. Does not reset stored level."
        ),
    },
    "EVT_15": {
        "engineStatus": "RESOLVED",
        "actionType": "DECREASE_BOARD_SIDE_RENT_LEVEL",
        "targetType": "BOARD_SIDE_OF_SELECTED_PROPERTY",
        "parameters": {
            "determinedByScannedProperty": True,
            "ownedOnly": True,
            "delta": -1,
            "minimumRentLevel": 1,
        },
        "notes": (
            "Determine physical board side of scanned property; apply -1 to every owned "
            "property on that side. Unowned properties ignored. Clamp minimum level 1. "
            "Side mapping from board_relationships.json (confirmed physical board layout)."
        ),
    },
    "EVT_21": {
        "engineStatus": "RESOLVED",
        "actionType": "TOTAL_GRIDLOCK_V1",
        "targetType": "ALL_PLAYERS",
        "physicalActionRequired": True,
        "parameters": {"passGo": False, "jailPlayersRemain": True},
        "notes": (
            "Physical tokens move manually to Free Parking. No GO payment. "
            "Players in Jail remain. jailStatus preserved."
        ),
    },
    "EVT_22": {
        "engineStatus": "RESOLVED",
        "actionType": "INCREASE_BOARD_SIDE_RENT_LEVEL",
        "targetType": "BOARD_SIDE_OF_SELECTED_PROPERTY",
        "parameters": {
            "determinedByScannedProperty": True,
            "ownedOnly": True,
            "delta": 1,
            "maximumRentLevel": 5,
        },
        "notes": (
            "Determine physical board side of scanned property; apply +1 to every owned "
            "property on that side. Unowned properties ignored. Clamp maximum level 5. "
            "Side mapping from board_relationships.json (confirmed physical board layout)."
        ),
    },
}


def build_event_engine_rules() -> dict:
    with (DATA_DIR / "editions" / "uk" / "events.json").open(encoding="utf-8") as handle:
        events = json.load(handle)["events"]

    records = []
    for event in sorted(events, key=lambda item: item["sequence"]):
        event_id = event["eventId"]
        parsed = event.get("parsedEffect", {})
        action = parsed.get("action", "TO_BE_CONFIRMED")
        override = EVENT_OVERRIDES.get(event_id, {})

        if override:
            engine_status = override["engineStatus"]
            action_type = override["actionType"]
            target_type = override["targetType"]
            physical_action_required = override.get(
                "physicalActionRequired", action_type == "SWAP_PROPERTIES"
            )
            temporary_effect = override.get(
                "temporaryEffect", action_type == "TEMPORARY_RENT_CAP"
            )
            parameters = override.get("parameters", parsed.get("parameters", {}))
            notes = override.get("notes", event.get("notes", ""))
        elif action == "TO_BE_CONFIRMED":
            engine_status = "NEEDS_CONFIRMATION"
            action_type = "TO_BE_CONFIRMED"
            target_type = TARGET_TYPE_MAP.get(parsed.get("target"), "NEEDS_CONFIRMATION")
            physical_action_required = False
            temporary_effect = False
            parameters = parsed.get("parameters", {})
            notes = event.get("notes", "")
        else:
            engine_status = "RESOLVED"
            action_type = ACTION_TYPE_MAP.get(action, action)
            target_type = TARGET_TYPE_MAP.get(parsed.get("target"), parsed.get("target"))
            physical_action_required = action_type == "SWAP_PROPERTIES"
            temporary_effect = action_type == "TEMPORARY_RENT_CAP"
            parameters = parsed.get("parameters", {})
            notes = event.get("notes", "")

        records.append(
            {
                "eventId": event_id,
                "name": event["name"],
                "engineStatus": engine_status,
                "actionType": action_type,
                "targetType": target_type,
                "requiresPlayerScan": event["requiresPlayerScan"],
                "requiresPropertyScan": event["requiresPropertyScan"],
                "parameters": parameters,
                "amount": parsed.get("amount"),
                "temporaryEffect": temporary_effect,
                "physicalActionRequired": physical_action_required,
                "ownedPropertiesOnly": True,
                "printedTextValidated": event.get("printedTextValidated", False),
                "notes": notes,
            }
        )

    return {"schemaVersion": 1, "events": records}


def main() -> None:
    payload = build_event_engine_rules()
    output = DATA_DIR / "common" / "event_engine_rules.json"
    with output.open("w", encoding="utf-8") as handle:
        json.dump(payload, handle, indent=2)
        handle.write("\n")
    print(f"Wrote {output}")


if __name__ == "__main__":
    main()
