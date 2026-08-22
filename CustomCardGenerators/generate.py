#!/usr/bin/env python3
"""
Generate Monopoly property cards, event cards, and board art for an edition.

Usage:
    python generate.py india
    python generate.py india --only properties
    python generate.py india --only events
    python generate.py india --only board
    python generate.py india --validate
    python generate.py --gui
"""

from __future__ import annotations

import argparse
import sys

from generator.pipeline import GenerationOptions, run_generation
from generator.utils import GENERATOR_ROOT, configure_stdio
from generator.validator import ValidationResult


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Generate edition property cards, event cards, and board files."
    )
    parser.add_argument(
        "edition",
        nargs="?",
        help="Edition id, for example india or uk",
    )
    parser.add_argument(
        "--only",
        choices=("properties", "events", "board"),
        help="Generate only one artifact type.",
    )
    parser.add_argument(
        "--validate",
        action="store_true",
        help="Validate edition data and templates without generating files.",
    )
    parser.add_argument(
        "--no-png",
        action="store_true",
        help="Write HTML only; skip Playwright PNG rendering.",
    )
    parser.add_argument(
        "--dpi",
        type=float,
        default=300,
        help="PNG DPI (default: 300)",
    )
    parser.add_argument(
        "--gui",
        action="store_true",
        help="Launch the desktop GUI instead of the command line.",
    )
    return parser.parse_args()


def print_banner(edition_id: str) -> None:
    print("=" * 50)
    print(" Custom Card Generator")
    print("=" * 50)
    print()
    print(f"Edition: {edition_id}")
    print()


def print_validation(result: ValidationResult) -> None:
    if not any(issue.source == "edition.json" and issue.level == "error" for issue in result.issues):
        print("[OK] edition.json")

    if result.property_count and not any(issue.source == "properties.json" and issue.level == "error" for issue in result.issues):
        print("[OK] properties.json")
        print(f"     {result.property_count} properties detected")
        print()
    elif not any(issue.source == "properties.json" and issue.level == "error" for issue in result.issues):
        print("[OK] properties.json")
        print()

    if result.event_count and not any(issue.source == "events.json" and issue.level == "error" for issue in result.issues):
        print("[OK] events.json")
        print(f"     {result.event_count} events detected")
        print()
    elif not any(issue.source == "events.json" and issue.level == "error" for issue in result.issues):
        print("[OK] events.json")
        print()

    if not any(issue.source == "banking_values.json" and issue.level == "error" for issue in result.issues):
        print("[OK] banking_values.json")
    if not any(issue.source == "board_relationships.json" and issue.level == "error" for issue in result.issues):
        print("[OK] board relationships")
    if not any(issue.source == "templates" and issue.level == "error" for issue in result.issues):
        print("[OK] templates")

    inner = result.inner_box or {}
    if inner.get("found"):
        print("[OK] InnerBox.png")
        print(f"     {inner.get('expectedPath') or inner.get('path')}")
    else:
        print("[WARN] InnerBox.png not found")
        print("       Center board area will remain empty.")
    print()

    if result.ok:
        if result.warnings:
            print(f"Validation successful with {len(result.warnings)} warning(s).")
        else:
            print("Validation successful.")
        print()
    else:
        print("Validation failed.")
        print()
        for issue in result.errors:
            print(issue.format_block())
            print()
        for issue in result.warnings:
            print(issue.format_block())
            print()


def main() -> int:
    configure_stdio()
    args = parse_args()

    if sys.path[0] != str(GENERATOR_ROOT):
        sys.path.insert(0, str(GENERATOR_ROOT))

    if args.gui:
        from gui import main as gui_main

        return gui_main()

    if not args.edition:
        print("ERROR: edition is required unless --gui is used.", file=sys.stderr)
        return 2

    print_banner(args.edition)

    options = GenerationOptions(
        edition_id=args.edition,
        generate_properties=args.only in (None, "properties"),
        generate_events=args.only in (None, "events"),
        generate_board=args.only in (None, "board"),
        generate_pngs=not args.no_png and not args.validate,
        dpi=args.dpi,
        validate_only=args.validate,
    )
    if args.validate:
        options.generate_properties = False
        options.generate_events = False
        options.generate_board = False
        options.generate_pngs = False

    outcome = run_generation(options, after_validate=print_validation)
    if outcome.error and not outcome.ok:
        print(f"[ERROR] {outcome.error}")
        return 1

    if args.validate or outcome.status == "validated":
        return 0 if outcome.ok else 1

    if not outcome.ok:
        return 1

    print("Files written:")
    labels = (
        ("propertyHtml", "property card HTML"),
        ("propertyPng", "property card PNG"),
        ("eventHtml", "event card HTML"),
        ("eventPng", "event card PNG"),
        ("boardHtml", "board HTML"),
        ("boardPng", "board PNG"),
    )
    for key, label in labels:
        paths = outcome.artifacts.get(key) or []
        if not paths:
            continue
        if len(paths) == 1:
            print(f"     {label}: {paths[0]}")
        else:
            print(f"     {len(paths)} {label} files in {paths[0].parent}")
    if not outcome.written_files():
        print("     Nothing was written.")
    for note in outcome.png_notes:
        print(f"     Note: {note}")
    print()
    print("Output:")
    print(f"output/{args.edition}/")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
