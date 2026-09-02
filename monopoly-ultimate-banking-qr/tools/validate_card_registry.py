#!/usr/bin/env python3
"""Validate common + edition card registries, assets, and QR uniqueness."""

from __future__ import annotations

import csv
import json
import sys
from collections import Counter, defaultdict
from pathlib import Path

WORKSPACE_ROOT = Path(__file__).resolve().parent.parent.parent
PROJECT_ROOT = Path(__file__).resolve().parent.parent
DATA_DIR = PROJECT_ROOT / "data"
EDITIONS_DIR = DATA_DIR / "editions"

COMMON_USER_COUNT = 4
COMMON_SEQUENCE_RANGE = (1, 4)

REQUIRED_FIELDS = [
    "cardId",
    "cardType",
    "sequence",
    "name",
    "qrPayload",
]


def load_json(path: Path) -> dict:
    with path.open(encoding="utf-8") as handle:
        return json.load(handle)


def load_common_cards() -> list[dict]:
    return load_json(DATA_DIR / "common" / "card_registry.json")["cards"]


def list_edition_ids() -> list[str]:
    if not EDITIONS_DIR.is_dir():
        return []
    return sorted(
        path.name
        for path in EDITIONS_DIR.iterdir()
        if path.is_dir() and (path / "edition.json").is_file()
    )


def load_edition_cards(edition_id: str) -> list[dict]:
    return load_json(EDITIONS_DIR / edition_id / "card_registry.json")["cards"]


def load_edition_config(edition_id: str) -> dict:
    edition = load_json(EDITIONS_DIR / edition_id / "edition.json")
    config = edition.get("cardConfiguration")
    if not isinstance(config, dict):
        raise ValueError(f"Edition '{edition_id}': cardConfiguration is missing or invalid.")
    return config


def load_decode_results() -> list[dict]:
    decode_path = DATA_DIR / "qr_decode_results.csv"
    with decode_path.open(encoding="utf-8", newline="") as handle:
        return list(csv.DictReader(handle))


def validate_common_registry(cards: list[dict], problems: list[str], stats: dict) -> None:
    stats["counts"]["USER"] = sum(1 for card in cards if card["cardType"] == "USER")

    if any(card.get("cardType") != "USER" for card in cards):
        problems.append("common/card_registry.json must contain USER cards only")

    if len(cards) != COMMON_USER_COUNT:
        problems.append(
            f"Common USER card count mismatch: expected {COMMON_USER_COUNT}, found {len(cards)}"
        )

    sequences = sorted(card["sequence"] for card in cards if card["cardType"] == "USER")
    expected_sequences = list(range(COMMON_SEQUENCE_RANGE[0], COMMON_SEQUENCE_RANGE[1] + 1))
    if sequences != expected_sequences:
        missing = sorted(set(expected_sequences) - set(sequences))
        extra = sorted(set(sequences) - set(expected_sequences))
        detail = []
        if missing:
            detail.append(f"missing {missing}")
        if extra:
            detail.append(f"extra {extra}")
        problems.append(f"Common USER sequence validation failed ({'; '.join(detail)})")

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

        if "legacy" in front.lower() or ("_Back.png" in front and "_Back_QR" not in front):
            problems.append(f"{card['cardId']}: legacy back asset referenced as front")


def validate_edition_registry(
    edition_id: str,
    cards: list[dict],
    problems: list[str],
    stats: dict,
) -> int:
    config = load_edition_config(edition_id)
    expected_counts = {
        "USER": config["playerCardCount"],
        "EVENT": config["eventCardCount"],
        "PROPERTY": config["propertyCardCount"],
    }
    expected_total = sum(expected_counts.values())

    if any(card.get("cardType") == "USER" for card in cards):
        problems.append(
            f"Edition '{edition_id}': card_registry.json must not contain USER cards "
            "(those live in common/card_registry.json)"
        )

    for card_type, expected in expected_counts.items():
        if card_type == "USER":
            continue
        found = sum(1 for card in cards if card["cardType"] == card_type)
        stats["counts"][f"{edition_id}:{card_type}"] = found
        if found != expected:
            problems.append(
                f"Edition '{edition_id}' {card_type} card count mismatch: "
                f"expected {expected}, found {found}"
            )

    if len(cards) != expected_total - expected_counts["USER"]:
        problems.append(
            f"Edition '{edition_id}' total card count mismatch: "
            f"expected {expected_total - expected_counts['USER']}, found {len(cards)}"
        )

    for card_type in ("EVENT", "PROPERTY"):
        expected = expected_counts[card_type]
        if expected <= 0:
            continue
        sequences = sorted(
            card["sequence"] for card in cards if card["cardType"] == card_type
        )
        expected_sequences = list(range(1, expected + 1))
        if sequences != expected_sequences:
            missing = sorted(set(expected_sequences) - set(sequences))
            extra = sorted(set(sequences) - set(expected_sequences))
            detail = []
            if missing:
                detail.append(f"missing {missing}")
            if extra:
                detail.append(f"extra {extra}")
            problems.append(
                f"Edition '{edition_id}' {card_type} sequence validation failed ({'; '.join(detail)})"
            )

    for card in cards:
        for field in REQUIRED_FIELDS:
            if field not in card or card[field] in (None, ""):
                problems.append(f"{card.get('cardId', 'UNKNOWN')}: missing required field '{field}'")

    return expected_counts["EVENT"] + expected_counts["PROPERTY"]


def validate(
    common_cards: list[dict],
    edition_cards: dict[str, list[dict]],
    decode_rows: list[dict],
) -> tuple[list[str], dict]:
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
        "expected_decode_total": 0,
    }

    validate_common_registry(common_cards, problems, stats)

    expected_decode_total = COMMON_USER_COUNT
    for edition_id in sorted(edition_cards):
        expected_decode_total += validate_edition_registry(
            edition_id,
            edition_cards[edition_id],
            problems,
            stats,
        )

    stats["expected_decode_total"] = expected_decode_total

    card_ids = [card["cardId"] for card in common_cards]
    duplicate_ids = [card_id for card_id, count in Counter(card_ids).items() if count > 1]
    if duplicate_ids:
        stats["duplicate_card_ids"] = len(duplicate_ids)
        problems.append(f"Duplicate common card IDs: {', '.join(sorted(duplicate_ids))}")

    for edition_id, cards in edition_cards.items():
        edition_ids = [card["cardId"] for card in cards]
        edition_duplicates = [
            card_id for card_id, count in Counter(edition_ids).items() if count > 1
        ]
        if edition_duplicates:
            stats["duplicate_card_ids"] += len(edition_duplicates)
            problems.append(
                f"Edition '{edition_id}' duplicate card IDs: "
                f"{', '.join(sorted(edition_duplicates))}"
            )

    payload_map: dict[str, list[str]] = defaultdict(list)
    for card in common_cards:
        payload = card.get("qrPayload", "")
        if payload:
            payload_map[payload].append(card["cardId"])

    for edition_id, cards in edition_cards.items():
        edition_payloads = [card.get("qrPayload", "") for card in cards]
        if len(edition_payloads) != len(set(edition_payloads)):
            stats["duplicate_payloads"] += 1
            problems.append(f"Edition '{edition_id}': QR payloads are not unique within the edition")

    if len(payload_map) != len(common_cards):
        problems.append("Common USER QR payloads are not unique")

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
        stats["duplicate_payloads"] += sum(len(ids) for ids in duplicate_payload_groups.values())
        for payload, ids in duplicate_payload_groups.items():
            problems.append(
                f"Duplicate common QR payload '{payload}' shared by: {', '.join(sorted(ids))}"
            )

    stats["unique_payloads"] = len(payload_map) + sum(
        len({card.get("qrPayload", "") for card in cards}) for cards in edition_cards.values()
    )

    if stats["decode_success"] != expected_decode_total:
        problems.append(
            f"Successful QR decodes: expected {expected_decode_total}, "
            f"found {stats['decode_success']}"
        )

    if stats["unique_payloads"] != expected_decode_total:
        problems.append(
            f"Unique QR payloads across registries: expected {expected_decode_total}, "
            f"found {stats['unique_payloads']}"
        )

    return problems, stats


def write_report(problems: list[str], stats: dict, edition_ids: list[str]) -> Path:
    output = DATA_DIR / "card_registry_validation.txt"
    result = "PASS" if not problems else "FAIL"

    lines = [
        "CARD REGISTRY VALIDATION",
        "========================",
        "",
        "Common User Cards",
        f"Expected: {COMMON_USER_COUNT}",
        f"Found:    {stats['counts'].get('USER', 0)}",
        "",
    ]

    for edition_id in edition_ids:
        config = load_edition_config(edition_id)
        lines.extend(
            [
                f"Edition: {edition_id}",
                f"  Event cards expected:    {config['eventCardCount']}",
                f"  Event cards found:       {stats['counts'].get(f'{edition_id}:EVENT', 0)}",
                f"  Property cards expected: {config['propertyCardCount']}",
                f"  Property cards found:    {stats['counts'].get(f'{edition_id}:PROPERTY', 0)}",
                "",
            ]
        )

    lines.extend(
        [
            "Total QR Cards (common + all editions)",
            f"Expected decode targets: {stats['expected_decode_total']}",
            "",
            "QR Decode",
            f"Successful: {stats['decode_success']}",
            f"Failed:     {stats['decode_failed']}",
            "",
            "Unique QR Payloads",
            f"Expected: {stats['expected_decode_total']}",
            f"Found:    {stats['unique_payloads']}",
            "",
            f"Missing Front Assets: {stats['missing_front']}",
            f"Missing QR Assets:    {stats['missing_qr']}",
            "",
            f"Duplicate Card IDs:     {stats['duplicate_card_ids']}",
            f"Duplicate QR Payloads:  {stats['duplicate_payloads']}",
            "",
            f"RESULT: {result}",
        ]
    )

    if problems:
        lines.extend(["", "Problems:", "---------"])
        lines.extend(f"- {problem}" for problem in problems)

    output.write_text("\n".join(lines) + "\n", encoding="utf-8")
    print(f"Wrote {output}")
    return output


def main() -> int:
    common_cards = load_common_cards()
    edition_ids = list_edition_ids()
    edition_cards = {edition_id: load_edition_cards(edition_id) for edition_id in edition_ids}
    decode_rows = load_decode_results()
    problems, stats = validate(common_cards, edition_cards, decode_rows)
    write_report(problems, stats, edition_ids)

    if problems:
        print(f"Validation FAILED with {len(problems)} problem(s).")
        for problem in problems[:20]:
            print(f"  - {problem}")
        if len(problems) > 20:
            print(f"  ... and {len(problems) - 20} more (see report)")
        return 1

    print("Validation PASSED.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
