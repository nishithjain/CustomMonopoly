#!/usr/bin/env python3
"""Compatibility wrapper. Prefer: python generate.py <edition> --only board"""

from __future__ import annotations

import argparse
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SRC = ROOT / "src"
if str(SRC) not in sys.path:
    sys.path.insert(0, str(SRC))

from monopoly_edition_generator.generators.board import (
    apply_inner_box,
    find_declaration_block,
    generate_board_spaces,
    generate_go_data,
)
from monopoly_edition_generator.paths import (
    BOARD_TEMPLATE,
    GeneratorError,
    board_tile_colors,
    configure_stdio,
    currency_symbol,
    load_json,
    load_theme,
    numeric_banking_value,
    read_text,
    rewrite_board_asset_paths,
    theme_id_for_edition,
)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Populate board HTML from properties.json and banking_values.json."
    )
    parser.add_argument("--properties", type=Path, default=Path("properties.json"))
    parser.add_argument("--banking", type=Path, default=Path("banking_values.json"))
    parser.add_argument(
        "--template",
        type=Path,
        default=BOARD_TEMPLATE,
        help="Path to board HTML template (never overwritten).",
    )
    parser.add_argument(
        "--output",
        type=Path,
        required=True,
        help="Output HTML path. The source template is never modified.",
    )
    return parser.parse_args()


def main() -> None:
    configure_stdio()
    args = parse_args()

    for label, path in (
        ("properties", args.properties),
        ("banking", args.banking),
        ("template", args.template),
    ):
        if not path.is_file():
            raise FileNotFoundError(f"{label} file not found: {path}")

    edition_json = args.properties.parent / "edition.json"
    if edition_json.is_file():
        theme = load_theme(theme_id_for_edition(load_json(edition_json)))
    else:
        theme = load_theme()

    properties_data = load_json(args.properties)
    banking_data = load_json(args.banking)
    properties = sorted(properties_data["properties"], key=lambda item: item["sequence"])
    currency = currency_symbol(banking_data)
    location_fee = numeric_banking_value(banking_data, "locationFee")
    go_salary = numeric_banking_value(banking_data, "goSalary")
    tile_colors = board_tile_colors(theme)

    html = read_text(args.template)
    board_start, board_end = find_declaration_block(html, "const boardSpaces", "[", "]")
    updated_html = (
        html[:board_start]
        + generate_board_spaces(properties, currency, location_fee, tile_colors)
        + html[board_end:]
    )
    go_start, go_end = find_declaration_block(updated_html, "const GO_DATA", "{", "}")
    updated_html = (
        updated_html[:go_start]
        + generate_go_data(currency, go_salary)
        + updated_html[go_end:]
    )

    args.output.parent.mkdir(parents=True, exist_ok=True)
    edition_id = "custom"
    if edition_json.is_file():
        edition_id = str(load_json(edition_json).get("editionId") or args.properties.parent.name)
    else:
        edition_id = args.properties.parent.name
    updated_html, _status = apply_inner_box(updated_html, edition_id, args.output)
    updated_html = rewrite_board_asset_paths(updated_html, args.output)
    args.output.write_text(updated_html, encoding="utf-8")
    print(f"Updated HTML: {args.output}")
    print(f"Properties loaded: {len(properties)}")
    print(f"Currency symbol: {currency}")
    print(f"GO salary: {go_salary}")
    print(f"Location fee: {location_fee}")


if __name__ == "__main__":
    main()
