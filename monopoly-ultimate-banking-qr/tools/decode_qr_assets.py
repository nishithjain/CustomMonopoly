#!/usr/bin/env python3
"""Development utility: inventory card assets, decode QR images, build master registry."""

from __future__ import annotations

import csv
import json
import re
import sys
from collections import defaultdict
from pathlib import Path

import cv2
import numpy as np

from card_definitions import ALL_CARDS, CATEGORY_DIRS, CardSpec

WORKSPACE_ROOT = Path(__file__).resolve().parent.parent.parent
PROJECT_ROOT = Path(__file__).resolve().parent.parent
RESOURCES_ROOT = WORKSPACE_ROOT / "Resources"
DATA_DIR = PROJECT_ROOT / "data"

FRONT_ROLE = "FRONT"
QR_ROLE = "QR"
LEGACY_BACK_ROLE = "LEGACY_BACK"
OTHER_ROLE = "OTHER"

NEW_FRONT_PATTERN = re.compile(r"_front_(new|New)", re.IGNORECASE)


def relative_resource_path(path: Path) -> str:
    return str(path.relative_to(WORKSPACE_ROOT)).replace("\\", "/")


def is_legacy_back(filename: str) -> bool:
    lower = filename.lower()
    return lower.endswith("_back.png") and "_back_qr" not in lower


def is_qr_back(filename: str) -> bool:
    return filename.endswith("_Back_QR.png")


def is_front_asset(filename: str) -> bool:
    lower = filename.lower()
    return "_front" in lower and lower.endswith((".png", ".jpg", ".jpeg"))


def front_preference_key(filename: str) -> tuple[int, str]:
    """Prefer _New/_new variants over original front assets."""
    if NEW_FRONT_PATTERN.search(filename):
        return (0, filename.lower())
    return (1, filename.lower())


def find_preferred_front(category_dir: Path, base_front: str) -> str | None:
    """Resolve preferred front asset, preferring _New/_new variants when present."""
    if not category_dir.exists():
        return None

    prefix = Path(base_front).stem
    if prefix.endswith("_Front"):
        card_prefix = prefix[: -len("_Front")]
    else:
        card_prefix = prefix

    candidates: list[Path] = []
    for path in category_dir.iterdir():
        if not path.is_file():
            continue
        name = path.name
        if not is_front_asset(name):
            continue
        stem_lower = Path(name).stem.lower()
        if stem_lower == prefix.lower() or stem_lower.startswith(card_prefix.lower() + "_front"):
            candidates.append(path)

    if not candidates:
        fallback = category_dir / base_front
        return base_front if fallback.exists() else None

    preferred = sorted(candidates, key=lambda p: front_preference_key(p.name))[0]
    return preferred.name


def decode_qr_image(image_path: Path) -> tuple[str | None, str | None]:
    """Decode QR payload from image using OpenCV QRCodeDetector."""
    image = cv2.imread(str(image_path), cv2.IMREAD_COLOR)
    if image is None:
        return None, "Failed to load image"

    detector = cv2.QRCodeDetector()

    for variant_name, variant in (
        ("color", image),
        ("grayscale", cv2.cvtColor(image, cv2.COLOR_BGR2GRAY)),
    ):
        data, points, _ = detector.detectAndDecode(variant)
        if data:
            return data, None

        retval, decoded_info, _points, _ = detector.detectAndDecodeMulti(variant)
        if retval and decoded_info is not None:
            for item in decoded_info:
                if item:
                    return item, None

    return None, "QR code not detected by OpenCV QRCodeDetector"


def infer_inventory_entry(category: str, path: Path) -> dict | None:
    filename = path.name
    rel_path = relative_resource_path(path)

    if is_qr_back(filename):
        role = QR_ROLE
        preferred = True
        notes = ""
    elif is_legacy_back(filename):
        role = LEGACY_BACK_ROLE
        preferred = False
        notes = "Ignored legacy hardware barcode"
    elif is_front_asset(filename):
        role = FRONT_ROLE
        preferred = False
        notes = "Front variant inventoried; preferred flag resolved per card"
    else:
        role = OTHER_ROLE
        preferred = False
        notes = "Non-card asset"

    sequence = ""
    canonical_name = ""

    for card in ALL_CARDS:
        card_dir = CATEGORY_DIRS[card.card_type]
        if category != card.card_type:
            continue
        card_folder = RESOURCES_ROOT / card_dir
        if path.parent.resolve() != card_folder.resolve():
            continue

        if filename == card.qr_filename:
            sequence = card.sequence
            canonical_name = card.name
            preferred = True
            notes = notes or ""
            break

        base_front = card.front_filename
        prefix = Path(base_front).stem
        card_prefix = prefix[: -len("_Front")] if prefix.endswith("_Front") else prefix
        stem_lower = Path(filename).stem.lower()
        if stem_lower == prefix.lower() or stem_lower.startswith(card_prefix.lower() + "_front"):
            sequence = card.sequence
            canonical_name = card.name
            if role == FRONT_ROLE:
                preferred_front = find_preferred_front(card_folder, base_front)
                preferred = filename == preferred_front
                if preferred:
                    notes = "Preferred front display asset"
                else:
                    notes = "Alternate front artwork variant"
            break

    return {
        "category": category,
        "sequence": sequence,
        "canonical_name": canonical_name,
        "asset_role": role,
        "filename": filename,
        "relative_path": rel_path,
        "preferred": str(preferred).lower(),
        "notes": notes,
    }


def build_inventory_rows() -> list[dict]:
    rows: list[dict] = []
    for category, folder_name in CATEGORY_DIRS.items():
        category_dir = RESOURCES_ROOT / folder_name
        if not category_dir.exists():
            continue
        for path in sorted(category_dir.iterdir()):
            if not path.is_file():
                continue
            entry = infer_inventory_entry(category, path)
            if entry:
                rows.append(entry)
    return rows


def write_inventory_csv(rows: list[dict]) -> None:
    output = DATA_DIR / "card_asset_inventory.csv"
    fieldnames = [
        "category",
        "sequence",
        "canonical_name",
        "asset_role",
        "filename",
        "relative_path",
        "preferred",
        "notes",
    ]
    with output.open("w", newline="", encoding="utf-8") as handle:
        writer = csv.DictWriter(handle, fieldnames=fieldnames)
        writer.writeheader()
        writer.writerows(rows)
    print(f"Wrote {output}")


def decode_all_qr_cards() -> list[dict]:
    results: list[dict] = []
    payload_to_cards: dict[str, list[str]] = defaultdict(list)

    for card in ALL_CARDS:
        category_dir = RESOURCES_ROOT / CATEGORY_DIRS[card.card_type]
        qr_path = category_dir / card.qr_filename
        rel_qr = relative_resource_path(qr_path)

        if not qr_path.exists():
            results.append(
                {
                    "category": card.card_type,
                    "card_id": card.card_id,
                    "canonical_name": card.name,
                    "qr_file": rel_qr,
                    "qr_payload": "",
                    "decode_status": "FAILED",
                    "error": "QR file not found",
                }
            )
            continue

        payload, error = decode_qr_image(qr_path)
        if payload:
            payload_to_cards[payload].append(card.card_id)
            results.append(
                {
                    "category": card.card_type,
                    "card_id": card.card_id,
                    "canonical_name": card.name,
                    "qr_file": rel_qr,
                    "qr_payload": payload,
                    "decode_status": "SUCCESS",
                    "error": "",
                }
            )
        else:
            results.append(
                {
                    "category": card.card_type,
                    "card_id": card.card_id,
                    "canonical_name": card.name,
                    "qr_file": rel_qr,
                    "qr_payload": "",
                    "decode_status": "FAILED",
                    "error": error or "Unknown decode failure",
                }
            )

    for row in results:
        payload = row["qr_payload"]
        if payload and len(payload_to_cards[payload]) > 1:
            row["decode_status"] = "DUPLICATE_PAYLOAD"
            row["error"] = (
                "Duplicate payload shared with: "
                + ", ".join(payload_to_cards[payload])
            )

    return results


def write_qr_decode_csv(results: list[dict]) -> None:
    output = DATA_DIR / "qr_decode_results.csv"
    fieldnames = [
        "category",
        "card_id",
        "canonical_name",
        "qr_file",
        "qr_payload",
        "decode_status",
        "error",
    ]
    with output.open("w", newline="", encoding="utf-8") as handle:
        writer = csv.DictWriter(handle, fieldnames=fieldnames)
        writer.writeheader()
        writer.writerows(results)
    print(f"Wrote {output}")


def build_registry(decode_results: list[dict]) -> tuple[list[dict], list[dict]]:
    decode_by_id = {row["card_id"]: row for row in decode_results}
    json_cards: list[dict] = []
    csv_rows: list[dict] = []

    for card in ALL_CARDS:
        category_dir = RESOURCES_ROOT / CATEGORY_DIRS[card.card_type]
        preferred_front = find_preferred_front(category_dir, card.front_filename)
        front_filename = preferred_front or card.front_filename
        front_rel = relative_resource_path(category_dir / front_filename)
        qr_rel = relative_resource_path(category_dir / card.qr_filename)
        decode = decode_by_id.get(card.card_id, {})

        json_cards.append(
            {
                "cardId": card.card_id,
                "cardType": card.card_type,
                "sequence": card.sequence,
                "name": card.name,
                "qrPayload": decode.get("qr_payload", ""),
                "assets": {
                    "front": front_rel,
                    "qr": qr_rel,
                },
            }
        )

        csv_rows.append(
            {
                "card_id": card.card_id,
                "card_type": card.card_type,
                "sequence": card.sequence,
                "name": card.name,
                "qr_payload": decode.get("qr_payload", ""),
                "front_asset": front_rel,
                "qr_asset": qr_rel,
                "decode_status": decode.get("decode_status", "FAILED"),
            }
        )

    return json_cards, csv_rows


def write_cards_json(cards: list[dict]) -> None:
    output = DATA_DIR / "cards.json"
    payload = {"schemaVersion": 1, "cards": cards}
    with output.open("w", encoding="utf-8") as handle:
        json.dump(payload, handle, indent=2)
        handle.write("\n")
    print(f"Wrote {output}")


def write_card_registry_csv(rows: list[dict]) -> None:
    output = DATA_DIR / "card_registry.csv"
    fieldnames = [
        "card_id",
        "card_type",
        "sequence",
        "name",
        "qr_payload",
        "front_asset",
        "qr_asset",
        "decode_status",
    ]
    with output.open("w", newline="", encoding="utf-8") as handle:
        writer = csv.DictWriter(handle, fieldnames=fieldnames)
        writer.writeheader()
        writer.writerows(rows)
    print(f"Wrote {output}")


def main() -> int:
    if not RESOURCES_ROOT.exists():
        print(f"ERROR: Resources directory not found at {RESOURCES_ROOT}", file=sys.stderr)
        return 1

    DATA_DIR.mkdir(parents=True, exist_ok=True)

    inventory_rows = build_inventory_rows()
    write_inventory_csv(inventory_rows)

    decode_results = decode_all_qr_cards()
    write_qr_decode_csv(decode_results)

    json_cards, csv_rows = build_registry(decode_results)
    write_cards_json(json_cards)
    write_card_registry_csv(csv_rows)

    success = sum(1 for row in decode_results if row["decode_status"] == "SUCCESS")
    failed = sum(1 for row in decode_results if row["decode_status"] == "FAILED")
    duplicates = sum(1 for row in decode_results if row["decode_status"] == "DUPLICATE_PAYLOAD")

    print()
    print(f"QR decode summary: success={success}, failed={failed}, duplicates={duplicates}")
    return 0 if failed == 0 and duplicates == 0 else 2


if __name__ == "__main__":
    raise SystemExit(main())
