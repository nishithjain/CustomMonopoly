#!/usr/bin/env python3
"""Validate master card registry counts, fields, assets, and QR uniqueness."""

from __future__ import annotations

import csv
import json
import sys
from collections import Counter, defaultdict
from pathlib import Path

WORKSPACE_ROOT = Path(__file__).resolve().parent.parent.parent
PROJECT_ROOT = Path(__file__).resolve().parent.parent
DATA_DIR = PROJECT_ROOT / "data"

EXPECTED_COUNTS = {
    "USER": 4,
    "EVENT": 23,
    "PROPERTY": 22,
}
EXPECTED_TOTAL = 49

SEQUENCE_RANGES = {
    "USER": (1, 4),
    "EVENT": (1, 23),
    "PROPERTY": (1, 22),
}

REQUIRED_FIELDS = [
    "cardId",
    "cardType",
    "sequence",
    "name",
    "qrPayload",
]


def load_cards() -> list[dict]:
    cards_path = DATA_DIR / "cards.json"
    with cards_path.open(encoding="utf-8") as handle:
        payload = json.load(handle)
    return payload["cards"]


def load_decode_results() -> list[dict]:
    decode_path = DATA_DIR / "qr_decode_results.csv"
    with decode_path.open(encoding="utf-8", newline="") as handle:
        return list(csv.DictReader(handle))


def validate(cards: list[dict], decode_rows: list[dict]) -> tuple[list[str], dict]:
    problems: list[str] = []
    stats: dict = {
        "counts": Counter(),
        "decode_success": 0,
        "decode_failed": 0,
        "decode_duplicates": 0,
        "unique_payloads": 0,
        "missing_front": 0,
        "missing_qr": 0,
        "duplicate_card_ids": 0,
        "duplicate_payloads": 0,
    }

    card_ids = [card["cardId"] for card in cards]
    duplicate_ids = [card_id for card_id, count in Counter(card_ids).items() if count > 1]
    if duplicate_ids:
        stats["duplicate_card_ids"] = len(duplicate_ids)
        problems.append(f"Duplicate card IDs: {', '.join(sorted(duplicate_ids))}")

    for card_type, expected in EXPECTED_COUNTS.items():
        found = sum(1 for card in cards if card["cardType"] == card_type)
        stats["counts"][card_type] = found
        if found != expected:
            problems.append(f"{card_type} card count mismatch: expected {expected}, found {found}")

    if len(cards) != EXPECTED_TOTAL:
        problems.append(f"Total card count mismatch: expected {EXPECTED_TOTAL}, found {len(cards)}")

    for card_type, (start, end) in SEQUENCE_RANGES.items():
        sequences = sorted(
            card["sequence"] for card in cards if card["cardType"] == card_type
        )
        expected_sequences = list(range(start, end + 1))
        if sequences != expected_sequences:
            missing = sorted(set(expected_sequences) - set(sequences))
            extra = sorted(set(sequences) - set(expected_sequences))
            detail = []
            if missing:
                detail.append(f"missing {missing}")
            if extra:
                detail.append(f"extra {extra}")
            problems.append(f"{card_type} sequence validation failed ({'; '.join(detail)})")

    payload_map: dict[str, list[str]] = defaultdict(list)

    for card in cards:
        for field in REQUIRED_FIELDS:
            if field not in card or card[field] in (None, ""):
                problems.append(f"{card.get('cardId', 'UNKNOWN')}: missing required field '{field}'")

        assets = card.get("assets", {})
        front = assets.get("front", "")
        qr = assets.get("qr", "")
        if not front:
            stats["missing_front"] += 1
            problems.append(f"{card['cardId']}: missing front asset")
        elif not (WORKSPACE_ROOT / front).exists():
            stats["missing_front"] += 1
            problems.append(f"{card['cardId']}: front asset not found: {front}")

        if not qr:
            stats["missing_qr"] += 1
            problems.append(f"{card['cardId']}: missing QR asset")
        elif not (WORKSPACE_ROOT / qr).exists():
            stats["missing_qr"] += 1
            problems.append(f"{card['cardId']}: QR asset not found: {qr}")

        if "legacy" in front.lower() or "_Back.png" in front and "_Back_QR" not in front:
            problems.append(f"{card['cardId']}: legacy back asset referenced as front")

        payload = card.get("qrPayload", "")
        if payload:
            payload_map[payload].append(card["cardId"])

    for row in decode_rows:
        status = row["decode_status"]
        if status == "SUCCESS":
            stats["decode_success"] += 1
        elif status == "FAILED":
            stats["decode_failed"] += 1
            problems.append(
                f"QR decode failed: {row['card_id']} ({row['canonical_name']}) "
                f"file={row['qr_file']} reason={row['error']}"
            )
        elif status == "DUPLICATE_PAYLOAD":
            stats["decode_duplicates"] += 1
            problems.append(
                f"Duplicate QR payload: {row['card_id']} ({row['canonical_name']}) "
                f"payload={row['qr_payload']} {row['error']}"
            )

    duplicate_payload_groups = {
        payload: ids for payload, ids in payload_map.items() if len(ids) > 1
    }
    if duplicate_payload_groups:
        stats["duplicate_payloads"] = sum(len(ids) for ids in duplicate_payload_groups.values())
        for payload, ids in duplicate_payload_groups.items():
            problems.append(
                f"Duplicate QR payload '{payload}' shared by: {', '.join(sorted(ids))}"
            )

    stats["unique_payloads"] = len(payload_map)

    if stats["decode_success"] != EXPECTED_TOTAL:
        problems.append(
            f"Successful QR decodes: expected {EXPECTED_TOTAL}, found {stats['decode_success']}"
        )

    if stats["unique_payloads"] != EXPECTED_TOTAL:
        problems.append(
            f"Unique QR payloads: expected {EXPECTED_TOTAL}, found {stats['unique_payloads']}"
        )

    return problems, stats


def write_report(problems: list[str], stats: dict) -> Path:
    output = DATA_DIR / "card_registry_validation.txt"
    result = "PASS" if not problems else "FAIL"

    lines = [
        "CARD REGISTRY VALIDATION",
        "========================",
        "",
        "User Cards",
        f"Expected: {EXPECTED_COUNTS['USER']}",
        f"Found:    {stats['counts'].get('USER', 0)}",
        "",
        "Event Cards",
        f"Expected: {EXPECTED_COUNTS['EVENT']}",
        f"Found:    {stats['counts'].get('EVENT', 0)}",
        "",
        "Property Cards",
        f"Expected: {EXPECTED_COUNTS['PROPERTY']}",
        f"Found:    {stats['counts'].get('PROPERTY', 0)}",
        "",
        "Total Active QR Cards",
        f"Expected: {EXPECTED_TOTAL}",
        f"Found:    {sum(stats['counts'].values())}",
        "",
        "QR Decode",
        f"Successful: {stats['decode_success']}",
        f"Failed:     {stats['decode_failed']}",
        "",
        "Unique QR Payloads",
        f"Expected: {EXPECTED_TOTAL}",
        f"Found:    {stats['unique_payloads']}",
        "",
        f"Missing Front Assets: {stats['missing_front']}",
        f"Missing QR Assets:    {stats['missing_qr']}",
        "",
        f"Duplicate Card IDs:     {stats['duplicate_card_ids']}",
        f"Duplicate QR Payloads:  {len([p for p in problems if p.startswith('Duplicate QR payload')])}",
        "",
        f"RESULT: {result}",
    ]

    if problems:
        lines.extend(["", "Problems:", "---------"])
        lines.extend(f"- {problem}" for problem in problems)

    output.write_text("\n".join(lines) + "\n", encoding="utf-8")
    print(f"Wrote {output}")
    return output


def main() -> int:
    cards = load_cards()
    decode_rows = load_decode_results()
    problems, stats = validate(cards, decode_rows)
    write_report(problems, stats)

    if problems:
        print(f"Validation FAILED with {len(problems)} problem(s).")
        for problem in problems:
            print(f"  - {problem}")
        return 1

    print("Validation PASSED.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
