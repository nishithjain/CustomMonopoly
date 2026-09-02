#!/usr/bin/env python3
"""Validate QR card-back images by decoding pixels and matching edition JSON payloads.

Reuses decode_qr_assets.decode_all_qr_cards() for pixel-level OpenCV decoding.
Exits non-zero when any referenced QR cannot be decoded, mismatches its JSON
payload, or shares a duplicate payload within the same edition scope.
"""

from __future__ import annotations

import argparse
import json
import sys
from collections import Counter, defaultdict
from pathlib import Path

from decode_qr_assets import decode_all_qr_cards

PROJECT_ROOT = Path(__file__).resolve().parent.parent


def filter_results(results: list[dict], edition: str | None) -> list[dict]:
    if edition is None or edition == "all":
        return results
    if edition == "common":
        return [row for row in results if not row["edition_id"]]
    return [row for row in results if row["edition_id"] == edition]


def summarize(results: list[dict]) -> dict:
    by_scope: dict[str, Counter] = defaultdict(Counter)
    for row in results:
        scope = row["edition_id"] or "common"
        category = row["category"]
        status = row["decode_status"]
        by_scope[scope][f"{category}_{status}"] += 1
        by_scope[scope][f"{category}_total"] += 1
        by_scope[scope]["total"] += 1
        if status == "SUCCESS":
            by_scope[scope]["success"] += 1
        else:
            by_scope[scope]["failed"] += 1
    return dict(by_scope)


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--edition",
        default="all",
        help="Edition to validate: india, uk, common, or all (default: all)",
    )
    parser.add_argument(
        "--write-csv",
        action="store_true",
        help="Also refresh data/qr_decode_results.csv (decode_qr_assets side effect)",
    )
    args = parser.parse_args(argv)

    edition = args.edition.lower()
    valid_editions = {"all", "common", "india", "uk"}
    if edition not in valid_editions:
        print(f"ERROR: Unknown edition '{args.edition}'. Use: {', '.join(sorted(valid_editions))}", file=sys.stderr)
        return 1

    all_results = decode_all_qr_cards()
    if args.write_csv:
        from decode_qr_assets import write_qr_decode_csv

        write_qr_decode_csv(all_results)

    results = filter_results(all_results, edition if edition != "all" else None)
    if not results:
        print(f"ERROR: No QR decode targets found for edition '{args.edition}'", file=sys.stderr)
        return 1

    stats = summarize(results)
    failures = [row for row in results if row["decode_status"] != "SUCCESS"]

    print(f"QR pixel validation for edition: {args.edition}")
    print(json.dumps(stats, indent=2))
    print()
    print(
        f"Totals: {len(results)} referenced QR images, "
        f"{len(results) - len(failures)} decoded OK, {len(failures)} failed"
    )

    if failures:
        print()
        print("Failures:")
        for row in failures:
            print(
                f"  - [{row['edition_id'] or 'common'}] {row['card_id']} "
                f"({row['category']}): {row['error'] or row['decode_status']} "
                f"({row['qr_file']})"
            )
        return 1

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
