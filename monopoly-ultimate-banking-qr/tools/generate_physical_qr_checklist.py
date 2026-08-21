#!/usr/bin/env python3
"""Regenerate docs/PHYSICAL_QR_TEST_CHECKLIST.md from data/common/card_registry.json."""

from __future__ import annotations

import json
import re
import sys
from pathlib import Path

PROJECT_ROOT = Path(__file__).resolve().parent.parent
DATA_PATH = PROJECT_ROOT / "data" / "common" / "card_registry.json"
OUTPUT_PATH = PROJECT_ROOT / "docs" / "PHYSICAL_QR_TEST_CHECKLIST.md"


def load_existing_results(path: Path) -> dict[str, dict[str, str]]:
    if not path.is_file():
        return {}
    text = path.read_text(encoding="utf-8")
    results: dict[str, dict[str, str]] = {}
    for line in text.splitlines():
        if not line.startswith("|") or line.startswith("| Card ID") or line.startswith("|---"):
            continue
        parts = [part.strip() for part in line.strip("|").split("|")]
        if len(parts) < 2:
            continue
        card_id = parts[0]
        results[card_id] = {
            "scan": parts[3] if len(parts) > 3 else "NOT_TESTED",
            "resolved": parts[4] if len(parts) > 4 else "",
            "pass_fail": parts[5] if len(parts) > 5 else "NOT_TESTED",
            "notes": parts[6] if len(parts) > 6 else "",
        }
    return results


def main() -> int:
    cards = json.loads(DATA_PATH.read_text(encoding="utf-8"))["cards"]
    existing = load_existing_results(OUTPUT_PATH)

    lines = [
        "# Physical QR Test Checklist",
        "",
        "Manual device testing for all printed QR cards.",
        "",
        "Mark **PASS** only after a real camera scan on a physical device.",
        "Automated CardResolver coverage (49/49) is separate from this checklist.",
        "",
        "| Card ID | Name | Expected Type | Camera Scan | Resolved ID | Pass/Fail | Notes |",
        "|---|---|---|---|---|---|---|",
    ]

    type_order = {"USER": 0, "PROPERTY": 1, "EVENT": 2}
    for card in sorted(cards, key=lambda c: (type_order.get(c["cardType"], 9), c["sequence"])):
        card_id = card["cardId"]
        prior = existing.get(card_id, {})
        scan = prior.get("scan", "NOT_TESTED")
        resolved = prior.get("resolved", "")
        pass_fail = prior.get("pass_fail", "NOT_TESTED")
        notes = prior.get("notes", "")
        lines.append(
            f"| {card_id} | {card['name']} | {card['cardType']} | {scan} | {resolved} | {pass_fail} | {notes} |"
        )

    lines.extend(
        [
            "",
            "## Summary",
            "",
            "| Category | Physically Tested (PASS) | Total |",
            "|---|---:|---:|",
            "| USER | _fill after testing_ | 4 |",
            "| PROPERTY | _fill after testing_ | 22 |",
            "| EVENT | _fill after testing_ | 23 |",
            "| **TOTAL** | **_fill after testing_** | **49** |",
            "",
            "## QR Reliability Notes",
            "",
            "Record difficult cards or lighting conditions here after tabletop testing.",
            "",
        ]
    )

    OUTPUT_PATH.write_text("\n".join(lines) + "\n", encoding="utf-8")
    print(f"Wrote {OUTPUT_PATH}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
