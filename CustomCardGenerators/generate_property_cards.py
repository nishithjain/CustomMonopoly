#!/usr/bin/env python3
"""Compatibility wrapper. Prefer: python generate.py <edition> --only properties"""

from __future__ import annotations

import argparse
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from generator.property_generator import generate_property_cards
from generator.utils import currency_symbol, load_json, configure_stdio, GeneratorError


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Generate one HTML property card per entry in properties.json."
    )
    parser.add_argument("properties_json", type=Path, help="Path to properties.json")
    parser.add_argument("html_template", type=Path, help="Path to HTML template")
    parser.add_argument("output_dir", type=Path, help="Directory for generated HTML files")
    parser.add_argument("--banking", type=Path, default=None, help="Path to banking_values.json")
    parser.add_argument(
        "--single-line-threshold",
        type=int,
        default=12,
        help="Names with this many characters or fewer stay on one line. Default: 12",
    )
    return parser.parse_args()


def infer_banking_path(properties_json: Path, explicit: Path | None) -> Path:
    if explicit:
        return explicit
    sibling = properties_json.parent / "banking_values.json"
    if sibling.is_file():
        return sibling
    raise GeneratorError(
        "Could not find banking_values.json. Pass --banking or use: python generate.py <edition> --only properties"
    )


def main() -> int:
    configure_stdio()
    args = parse_args()
    try:
        banking = load_json(infer_banking_path(args.properties_json, args.banking))
        currency = currency_symbol(banking)
        data = load_json(args.properties_json)
        properties = data["properties"]
        generate_property_cards(
            edition_id="custom",
            currency=currency,
            output_html_dir=args.output_dir,
            template_path=args.html_template,
            properties=properties,
            single_line_threshold=args.single_line_threshold,
        )
        return 0
    except (OSError, GeneratorError, KeyError, TypeError, ValueError) as exc:
        print(f"ERROR: {exc}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
