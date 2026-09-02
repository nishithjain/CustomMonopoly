#!/usr/bin/env python3
"""Build India event_engine_rules.json from india_event_balance_config.json."""

from __future__ import annotations

import json
from pathlib import Path

PROJECT_ROOT = Path(__file__).resolve().parent.parent
INDIA_DIR = PROJECT_ROOT / "data" / "editions" / "india"
BALANCE_PATH = INDIA_DIR / "india_event_balance_config.json"
OUTPUT_PATH = INDIA_DIR / "event_engine_rules.json"

TARGET_BY_ACTION = {
    "MOVE_TO_SPACE": "NONE",
    "MOVE_BACKWARD": "NONE",
    "BANK_CREDIT": "CURRENT_PLAYER",
    "BANK_DEBIT": "CURRENT_PLAYER",
    "PAY_EACH_PLAYER": "ALL_PLAYERS",
    "COLLECT_FROM_EACH_PLAYER": "ALL_PLAYERS",
    "DEBIT_PER_OWNED_PROPERTY": "CURRENT_PLAYER",
    "CREDIT_PER_OWNED_PROPERTY": "CURRENT_PLAYER",
    "NEXT_RENT_WAIVER": "CURRENT_PLAYER",
    "GET_OUT_OF_JAIL_PASS": "CURRENT_PLAYER",
    "MOVE_TO_JAIL": "CURRENT_PLAYER",
    "INCREASE_SELECTED_PROPERTY_RENT_LEVEL": "OWNED_PROPERTY",
    "DECREASE_SELECTED_PROPERTY_RENT_LEVEL": "OWNED_PROPERTY",
    "DRAW_ANOTHER_EVENT": "NONE",
    "COOPERATIVE_PROPERTY_UPGRADE": "TWO_PLAYERS_AND_PROPERTIES",
    "GAMBLE_ON_DICE_ROLL": "CURRENT_PLAYER",
    "SKIP_NEXT_TURN": "CURRENT_PLAYER",
    "FORCED_PROPERTY_SELLBACK": "OWNED_PROPERTY",
    "TOP_UP_BALANCE_TO_THRESHOLD": "CURRENT_PLAYER",
    "MOVE_TO_NEAREST_STATION": "NONE",
    "EXTRA_TURN": "CURRENT_PLAYER",
    "COMPLETE_COLOR_SET_BONUS_CREDIT": "CURRENT_PLAYER",
}

PHYSICAL_ACTIONS = {
    "MOVE_TO_SPACE",
    "MOVE_BACKWARD",
    "MOVE_TO_JAIL",
    "MOVE_TO_NEAREST_STATION",
    "DRAW_ANOTHER_EVENT",
}

PROPERTY_SCAN_ACTIONS = {
    "INCREASE_SELECTED_PROPERTY_RENT_LEVEL",
    "DECREASE_SELECTED_PROPERTY_RENT_LEVEL",
    "COOPERATIVE_PROPERTY_UPGRADE",
    "FORCED_PROPERTY_SELLBACK",
}

PLAYER_SCAN_ACTIONS = {
    "COOPERATIVE_PROPERTY_UPGRADE",
    "PAY_EACH_PLAYER",
    "COLLECT_FROM_EACH_PLAYER",
}


def amount_for_action(action: dict) -> int | None:
    for key in (
        "amount",
        "amountPerProperty",
        "amountPerOtherPlayer",
        "jackpotAmount",
        "penaltyAmount",
        "thresholdAmount",
        "baseRebateAmount",
    ):
        value = action.get(key)
        if isinstance(value, (int, float)):
            return int(value)
    return None


def build_rule(event: dict) -> dict:
    action = event["actions"][0]
    action_type = action["actionType"]
    parameters = {
        key: value
        for key, value in action.items()
        if key != "actionType"
    }
    return {
        "eventId": event["eventId"],
        "name": event["name"],
        "engineStatus": "RESOLVED",
        "actionType": action_type,
        "targetType": TARGET_BY_ACTION[action_type],
        "requiresPlayerScan": action_type in PLAYER_SCAN_ACTIONS,
        "requiresPropertyScan": action_type in PROPERTY_SCAN_ACTIONS,
        "parameters": parameters,
        "amount": amount_for_action(action),
        "temporaryEffect": action_type == "NEXT_RENT_WAIVER",
        "physicalActionRequired": action_type in PHYSICAL_ACTIONS,
        "ownedPropertiesOnly": action_type
        not in {"FORCED_PROPERTY_SELLBACK", "COOPERATIVE_PROPERTY_UPGRADE"},
        "printedTextValidated": True,
        "notes": "",
    }


def main() -> int:
    balance = json.loads(BALANCE_PATH.read_text(encoding="utf-8"))
    events = [build_rule(event) for event in balance["events"]]
    payload = {
        "schemaVersion": 1,
        "globalEventRules": balance.get("globalEventRules", {}),
        "events": events,
    }
    OUTPUT_PATH.write_text(json.dumps(payload, indent=2) + "\n", encoding="utf-8")
    print(f"Wrote {OUTPUT_PATH} ({len(events)} events)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
