#!/usr/bin/env python3
"""Build India events.json from EventsForIndia.csv and india_event_balance_config.json."""

from __future__ import annotations

import csv
import json
import re
from pathlib import Path

PROJECT_ROOT = Path(__file__).resolve().parent.parent
INDIA_DIR = PROJECT_ROOT / "data" / "editions" / "india"
CSV_PATH = INDIA_DIR / "EventsForIndia.csv"
BALANCE_PATH = INDIA_DIR / "india_event_balance_config.json"
OUTPUT_PATH = INDIA_DIR / "events.json"

OLD_EVENT_NAMES = {
    "Boom Town",
    "Crime Down",
    "Deal Of The Week",
    "Demolished",
    "Grand Designs",
    "Haunted House",
    "Highway Tax",
    "House Party",
    "In The Money",
    "It's A Boy!",
    "Love Is In The Air",
    "On The Map",
    "On The Run",
    "Pick Your Own",
    "Pong! What A Stinker",
    "Rover's Revenge",
    "Stargazing",
    "Stop The Presses",
    "'Tis The Season",
    "Tornado Alley",
    "Total Gridlock",
    "What A Ride!",
    "Wibble Wobble",
}

EVENT_25_ACTION = "COMPLETE_COLOR_SET_BONUS_CREDIT"

# Player-facing card instructions use named placeholders resolved at generation time.
EVENT_CARD_DESCRIPTIONS: dict[str, str] = {
    "EVT_01": "Move directly to GO and collect {goSalary} once.",
    "EVT_02": (
        "Move backward three spaces and resolve the new landing. "
        "Do not collect the GO amount when moving backward."
    ),
    "EVT_03": "Collect {amount} from the bank.",
    "EVT_04": "Collect {amount} from the bank.",
    "EVT_05": "Pay {amount} to the bank.",
    "EVT_06": "Pay {amountPerOtherPlayer} to each other player.",
    "EVT_07": "Collect {amountPerOtherPlayer} from each other player.",
    "EVT_08": "Pay {amountPerProperty} to the bank for each Property you own.",
    "EVT_09": "Collect {amountPerProperty} from the bank for each Property you own.",
    "EVT_10": "Your next rent payment is waived.",
    "EVT_11": "Keep this pass. Use it once to leave Jail without paying {jailReleaseFee}.",
    "EVT_12": "Move directly to Jail without collecting the GO amount. End your current turn.",
    "EVT_13": "Select any Property you own and increase its rent level by one level.",
    "EVT_14": (
        "Select any Property you own that is above the minimum rent level. "
        "Decrease its rent level by one."
    ),
    "EVT_15": "Draw and resolve one additional Event Card.",
    "EVT_16": (
        "Select one eligible Property you own and one eligible Property owned by another player. "
        "Increase both Properties by one rent level."
    ),
    "EVT_17": (
        "Roll both dice up to three times. Roll doubles to collect {jackpotAmount}; otherwise pay {penaltyAmount}."
    ),
    "EVT_18": "Skip your next scheduled turn.",
    "EVT_19": (
        "The city claims one of your least valuable Properties. The bank buys it for twice its purchase price."
    ),
    "EVT_20": "If your balance is below {thresholdAmount}, the bank increases it to {thresholdAmount}.",
    "EVT_21": (
        "Move forward to the next Energy Station. Collect {goSalary} if you pass GO, "
        "then resolve the Energy Station normally."
    ),
    "EVT_22": "Pay {amount} to the bank.",
    "EVT_23": "Collect {amount} from the bank.",
    "EVT_24": "After your current turn ends, immediately take one additional turn.",
    "EVT_25": "If you own at least one complete colour group, collect {rewardAmount} from the bank.",
}


def safe_filename(name: str) -> str:
    value = re.sub(r"[^\w\s-]", "", name.strip(), flags=re.UNICODE)
    value = re.sub(r"[\s_-]+", "_", value)
    return value.strip("_") or "Event"


def normalize_description(text: str) -> str:
    text = text.replace("\r\n", "\n")
    text = text.replace("edition\uFFFDs", "edition's")
    text = re.sub(r"edition.s initial rent level", "edition's initial rent level", text)
    return text.strip()


def load_balance_lookup() -> dict[str, dict]:
    data = json.loads(BALANCE_PATH.read_text(encoding="utf-8"))
    return {entry["eventId"]: entry for entry in data["events"]}


def effect_classification(action: str) -> list[str]:
    mapping = {
        "MOVE_TO_SPACE": ["MOVE_PLAYER", "MONEY_GAIN", "SPECIAL_RULE"],
        "MOVE_BACKWARD": ["MOVE_PLAYER"],
        "BANK_CREDIT": ["MONEY_GAIN"],
        "BANK_DEBIT": ["MONEY_LOSS"],
        "PAY_EACH_PLAYER": ["MONEY_LOSS", "AFFECT_ALL_PLAYERS"],
        "COLLECT_FROM_EACH_PLAYER": ["MONEY_GAIN", "AFFECT_ALL_PLAYERS"],
        "DEBIT_PER_OWNED_PROPERTY": ["MONEY_LOSS", "AFFECT_PROPERTY"],
        "CREDIT_PER_OWNED_PROPERTY": ["MONEY_GAIN", "AFFECT_PROPERTY"],
        "NEXT_RENT_WAIVER": ["SPECIAL_RULE"],
        "GET_OUT_OF_JAIL_PASS": ["SPECIAL_RULE"],
        "MOVE_TO_JAIL": ["GO_TO_JAIL", "MOVE_PLAYER"],
        "INCREASE_SELECTED_PROPERTY_RENT_LEVEL": ["PROPERTY_RENT_INCREASE", "SELECT_PROPERTY", "AFFECT_PROPERTY"],
        "DECREASE_SELECTED_PROPERTY_RENT_LEVEL": ["PROPERTY_RENT_DECREASE", "SELECT_PROPERTY", "AFFECT_PROPERTY"],
        "DRAW_ANOTHER_EVENT": ["SPECIAL_RULE"],
        "COOPERATIVE_PROPERTY_UPGRADE": ["PROPERTY_RENT_INCREASE", "SELECT_PLAYER", "SELECT_PROPERTY", "AFFECT_PROPERTY"],
        "GAMBLE_ON_DICE_ROLL": ["MONEY_GAIN", "MONEY_LOSS", "SPECIAL_RULE"],
        "SKIP_NEXT_TURN": ["SPECIAL_RULE"],
        "FORCED_PROPERTY_SELLBACK": ["MONEY_GAIN", "SELECT_PROPERTY", "AFFECT_PROPERTY", "SPECIAL_RULE"],
        "TOP_UP_BALANCE_TO_THRESHOLD": ["MONEY_GAIN", "SPECIAL_RULE"],
        "MOVE_TO_NEAREST_STATION": ["MOVE_PLAYER", "MONEY_GAIN", "SPECIAL_RULE"],
        "EXTRA_TURN": ["SPECIAL_RULE"],
        EVENT_25_ACTION: ["MONEY_GAIN", "AFFECT_PROPERTY", "SPECIAL_RULE"],
    }
    return mapping[action]


def scan_requirements(action: str) -> tuple[bool, bool]:
    if action in {
        "INCREASE_SELECTED_PROPERTY_RENT_LEVEL",
        "DECREASE_SELECTED_PROPERTY_RENT_LEVEL",
        "FORCED_PROPERTY_SELLBACK",
    }:
        return False, True
    if action == "COOPERATIVE_PROPERTY_UPGRADE":
        return True, True
    return False, False


def parsed_effect(action: dict) -> dict:
    action_type = action["actionType"]
    if action_type == EVENT_25_ACTION:
        action_type = EVENT_25_ACTION
    elif action_type == "MONOPOLY_GROUP_BONUS_CREDIT":
        action_type = EVENT_25_ACTION

    parameters = {key: value for key, value in action.items() if key != "actionType"}

    amount = None
    target = "CURRENT_PLAYER"

    if action_type == "MOVE_TO_SPACE":
        target = "CURRENT_PLAYER"
    elif action_type == "MOVE_BACKWARD":
        amount = action.get("spaceCount")
    elif action_type in {"BANK_CREDIT", "BANK_DEBIT"}:
        amount = action.get("amount")
    elif action_type in {"PAY_EACH_PLAYER", "COLLECT_FROM_EACH_PLAYER"}:
        amount = action.get("amountPerOtherPlayer")
        target = "ALL_OTHER_PLAYERS"
    elif action_type in {"DEBIT_PER_OWNED_PROPERTY", "CREDIT_PER_OWNED_PROPERTY"}:
        amount = action.get("amountPerProperty")
        target = "OWNED_PROPERTY"
    elif action_type == "NEXT_RENT_WAIVER":
        amount = action.get("waivedPaymentCount")
    elif action_type == "GET_OUT_OF_JAIL_PASS":
        amount = action.get("passCount")
    elif action_type in {
        "INCREASE_SELECTED_PROPERTY_RENT_LEVEL",
        "DECREASE_SELECTED_PROPERTY_RENT_LEVEL",
    }:
        amount = action.get("levelChange")
        target = "OWNED_PROPERTY"
    elif action_type == "DRAW_ANOTHER_EVENT":
        amount = action.get("additionalEventCount")
    elif action_type == "COOPERATIVE_PROPERTY_UPGRADE":
        amount = action.get("levelIncreasePerProperty")
        target = "TWO_PLAYERS_AND_PROPERTIES"
    elif action_type == "GAMBLE_ON_DICE_ROLL":
        amount = action.get("jackpotAmount")
    elif action_type == "SKIP_NEXT_TURN":
        amount = action.get("skipTurnCount")
    elif action_type == "FORCED_PROPERTY_SELLBACK":
        amount = action.get("payoutMultiplier")
        target = "OWNED_PROPERTY"
    elif action_type == "TOP_UP_BALANCE_TO_THRESHOLD":
        amount = action.get("thresholdAmount")
    elif action_type == "EXTRA_TURN":
        amount = action.get("extraTurnCount")
    elif action_type == EVENT_25_ACTION:
        amount = action.get("baseRebateAmount")
        target = "OWNED_PROPERTY"

    return {
        "action": action_type,
        "amount": amount,
        "target": target,
        "parameters": parameters,
    }


def read_csv_rows() -> list[dict[str, str]]:
    rows: list[dict[str, str]] = []
    with CSV_PATH.open(encoding="cp1252", newline="") as handle:
        reader = csv.DictReader(handle)
        for row in reader:
            if not str(row.get("Event name", "")).strip():
                continue
            rows.append(row)
    return rows


def build_events() -> list[dict]:
    balance = load_balance_lookup()
    events: list[dict] = []
    for row in read_csv_rows():
        sequence = int(row["#"])
        event_id = f"EVT_{sequence:02d}"
        name = row["Event name"].strip()
        category = row["Category"].strip()
        description = EVENT_CARD_DESCRIPTIONS.get(event_id)
        if description is None:
            description = normalize_description(row["Suggested card instruction"])
        csv_action = row["ActionType"].strip()
        balance_entry = balance[event_id]
        action = balance_entry["actions"][0]
        action_type = action["actionType"]
        if csv_action == "MONOPOLY_GROUP_BONUS_CREDIT":
            action_type = EVENT_25_ACTION
        elif action_type != csv_action:
            raise ValueError(f"{event_id}: CSV action {csv_action} != balance action {action_type}")

        safe_name = safe_filename(name)
        requires_player_scan, requires_property_scan = scan_requirements(action_type)
        events.append(
            {
                "eventId": event_id,
                "sequence": sequence,
                "name": name,
                "qrPayload": f"MUB:E:E{sequence:02d}",
                "frontAsset": f"Resources/Editions/india/EventCards/{safe_name}_Front.png",
                "qrAsset": f"Resources/Editions/india/EventCards/E{sequence:02d}_{safe_name}_Back_QR.png",
                "artworkAsset": f"assets/cards/editions/india/event-artwork/{event_id}_{safe_name}.png",
                "eventSubtitle": category,
                "eventDescription": description,
                "printedTextValidated": True,
                "printedRuleStatus": "RESOLVED",
                "engineImplementationStatus": "NEEDS_REVIEW",
                "effectClassification": effect_classification(action_type),
                "parsedEffect": parsed_effect(action),
                "requiresPlayerScan": requires_player_scan,
                "requiresPropertyScan": requires_property_scan,
                "extractionStatus": "COMPLETE",
                "notes": (
                    "India edition event deck v1. Engine handler pending for "
                    f"{action_type}."
                ),
            }
        )
    return events


def main() -> int:
    events = build_events()
    if len(events) != 25:
        raise SystemExit(f"Expected 25 events, got {len(events)}")
    names = [event["name"] for event in events]
    if any(name in OLD_EVENT_NAMES for name in names):
        raise SystemExit("Old UK deck event names remain in output")
    if any("TO_BE_CONFIRMED" in json.dumps(event) for event in events):
        raise SystemExit("TO_BE_CONFIRMED placeholder found")
    if any("\ufffd" in json.dumps(event, ensure_ascii=False) for event in events):
        raise SystemExit("Replacement character found in output")
    if any("configured" in event["eventDescription"].lower() for event in events):
        raise SystemExit("Event descriptions must not contain the word 'configured'")

    payload = {"schemaVersion": 1, "events": events}
    OUTPUT_PATH.write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"Wrote {len(events)} events to {OUTPUT_PATH}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
