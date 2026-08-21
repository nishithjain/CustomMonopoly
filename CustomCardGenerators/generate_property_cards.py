#!/usr/bin/env python3
"""
Generate Monopoly property-card HTML files from properties.json.

Usage:
    python generate_property_cards.py properties.json HTML_Template.html output_cards

Optional:
    python generate_property_cards.py properties.json HTML_Template.html output_cards \
        --single-line-threshold 12

The script reads only these property fields:
    name
    sequence
    colorGroup
    purchasePrice
    rentLevels

Each generated HTML file keeps the attached template structure and replaces
CARD_DATA plus the color palette for that property's colorGroup.
"""

from __future__ import annotations

import argparse
import html
import json
import re
import sys
from pathlib import Path
from typing import Any


# Base color used for each Monopoly color group.
# The remaining dark/light/header/rent shades are derived automatically.
COLOR_GROUP_BASE = {
    "BROWN": "#A16434",
    "LIGHT_BLUE": "#77BFE8",
    "PINK": "#D9569E",
    "ORANGE": "#E9923A",
    "RED": "#D94B43",
    "YELLOW": "#E7BE35",
    "GREEN": "#3E9762",
    "DARK_BLUE": "#355B91",
}

REQUIRED_FIELDS = (
    "name",
    "sequence",
    "colorGroup",
    "purchasePrice",
    "rentLevels",
)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Generate one HTML property card per entry in properties.json."
    )
    parser.add_argument("properties_json", type=Path, help="Path to properties.json")
    parser.add_argument("html_template", type=Path, help="Path to HTML template")
    parser.add_argument("output_dir", type=Path, help="Directory for generated HTML files")
    parser.add_argument(
        "--single-line-threshold",
        type=int,
        default=12,
        help=(
            "Names with this many characters or fewer stay on one line. "
            "Longer multi-word names are balanced across two lines. Default: 12"
        ),
    )
    return parser.parse_args()


def normalize_name(name: str) -> str:
    """Normalize spaces and convert the displayed property name to uppercase."""
    return " ".join(str(name).strip().split()).upper()


def split_name(name: str, single_line_threshold: int = 12) -> list[str]:
    """
    Return either one or two display lines.

    Rules:
      1. Explicit newline in the JSON name is respected.
      2. Short names remain on one line.
      3. Long multi-word names are split at the word boundary that gives
         the most visually balanced two-line result.
      4. A single long word is never broken in the middle.
    """
    raw_name = str(name).strip()

    # Allow future JSON values to explicitly control the split with \n.
    if "\n" in raw_name:
        explicit = [normalize_name(part) for part in raw_name.splitlines() if part.strip()]
        if not explicit:
            raise ValueError("Property name cannot be empty.")
        if len(explicit) > 2:
            raise ValueError(
                f"Property name has {len(explicit)} explicit lines; maximum supported is 2."
            )
        return explicit

    display_name = normalize_name(raw_name)
    words = display_name.split()

    if len(display_name) <= single_line_threshold or len(words) <= 1:
        return [display_name]

    # Try every word-boundary split and choose the most balanced result.
    candidates: list[tuple[tuple[int, int, int], list[str]]] = []

    for split_at in range(1, len(words)):
        first = " ".join(words[:split_at])
        second = " ".join(words[split_at:])

        # Primary goal: balance line lengths.
        # Secondary goal: avoid an unnecessarily long line.
        # Tertiary goal: prefer the first line to be no shorter than the second
        # when two choices are otherwise equivalent.
        score = (
            abs(len(first) - len(second)),
            max(len(first), len(second)),
            0 if len(first) >= len(second) else 1,
        )
        candidates.append((score, [first, second]))

    return min(candidates, key=lambda item: item[0])[1]


def hex_to_rgb(value: str) -> tuple[int, int, int]:
    value = value.lstrip("#")
    if len(value) != 6:
        raise ValueError(f"Expected 6-digit hex color, got: {value!r}")
    return tuple(int(value[i : i + 2], 16) for i in (0, 2, 4))


def rgb_to_hex(rgb: tuple[int, int, int]) -> str:
    return "#{:02X}{:02X}{:02X}".format(*rgb)


def blend(color: str, target: str, target_ratio: float) -> str:
    """
    Blend `color` toward `target`.

    target_ratio=0.0 -> original color
    target_ratio=1.0 -> target color
    """
    c = hex_to_rgb(color)
    t = hex_to_rgb(target)
    ratio = max(0.0, min(1.0, target_ratio))

    result = tuple(
        round(source * (1.0 - ratio) + destination * ratio)
        for source, destination in zip(c, t)
    )
    return rgb_to_hex(result)


def palette_for_color_group(color_group: str) -> dict[str, Any]:
    """
    Build the header and five rent-row gradients from one colorGroup base color.
    """
    group = str(color_group).strip().upper()

    if group not in COLOR_GROUP_BASE:
        supported = ", ".join(sorted(COLOR_GROUP_BASE))
        raise ValueError(
            f"Unsupported colorGroup {color_group!r}. Supported values: {supported}"
        )

    base = COLOR_GROUP_BASE[group]

    # Header: slightly darker / lighter variations of the group color.
    property_dark = blend(base, "#000000", 0.20)
    property_light = blend(base, "#FFFFFF", 0.10)

    # Rent rows progress from very light to stronger versions of the same group.
    lightening_pairs = [
        (0.90, 0.84),
        (0.80, 0.72),
        (0.68, 0.58),
        (0.54, 0.42),
        (0.38, 0.26),
    ]

    rent_rows = [
        (blend(base, "#FFFFFF", a), blend(base, "#FFFFFF", b))
        for a, b in lightening_pairs
    ]

    return {
        "property_dark": property_dark,
        "property_light": property_light,
        "rent_rows": rent_rows,
    }


def validate_property(prop: dict[str, Any], index: int) -> None:
    missing = [field for field in REQUIRED_FIELDS if field not in prop]
    if missing:
        raise ValueError(
            f"Property entry #{index} is missing required field(s): {', '.join(missing)}"
        )

    if not isinstance(prop["rentLevels"], list) or not prop["rentLevels"]:
        raise ValueError(
            f"Property {prop.get('name', index)!r}: rentLevels must be a non-empty list."
        )

    for rent_index, rent_level in enumerate(prop["rentLevels"], start=1):
        if "level" not in rent_level or "amount" not in rent_level:
            raise ValueError(
                f"Property {prop['name']!r}, rentLevels[{rent_index - 1}] "
                "must contain 'level' and 'amount'."
            )

    # Validate color group now so errors are reported before generating files.
    palette_for_color_group(prop["colorGroup"])


def replace_css_variable(template: str, variable: str, value: str) -> str:
    pattern = rf"(--{re.escape(variable)}\s*:\s*)[^;]+;"
    replacement = rf"\g<1>{value};"
    result, count = re.subn(pattern, replacement, template, count=1)

    if count != 1:
        raise ValueError(f"Could not find CSS variable --{variable} in template.")

    return result


def apply_palette(template: str, color_group: str) -> str:
    palette = palette_for_color_group(color_group)

    result = replace_css_variable(
        template, "property-dark", palette["property_dark"]
    )
    result = replace_css_variable(
        result, "property-light", palette["property_light"]
    )

    for row_number, (start, end) in enumerate(palette["rent_rows"], start=1):
        result = replace_css_variable(
            result,
            f"rent-row-{row_number}",
            f"{start}, {end}",
        )

    return result


def make_card_data(prop: dict[str, Any], single_line_threshold: int) -> dict[str, Any]:
    name_lines = split_name(prop["name"], single_line_threshold)

    return {
        "currency": "₹",
        "labels": {
            "purchasePrice": "Purchase Price",
            "rentLevel": "RENT LEVEL",
            "rent": "RENT",
        },
        "property": {
            "number": int(prop["sequence"]),
            "name": name_lines,
            "purchasePrice": prop["purchasePrice"],
        },
        "rentLevels": [
            {
                "level": int(item["level"]),
                "rent": item["amount"],
            }
            for item in prop["rentLevels"]
        ],
    }


def replace_card_data(template: str, card_data: dict[str, Any]) -> str:
    """
    Replace the template's const CARD_DATA = {...}; block without changing
    the renderer or the rest of the HTML.
    """
    pretty_json = json.dumps(card_data, ensure_ascii=False, indent=4)

    # Indent JSON to match the script block.
    lines = pretty_json.splitlines()
    indented_json = "\n".join(
        ("  " + line) if line else line
        for line in lines
    )

    replacement = "const CARD_DATA = " + indented_json.lstrip() + ";"

    pattern = re.compile(
        r"const\s+CARD_DATA\s*=\s*\{.*?\n\s*\};",
        flags=re.DOTALL,
    )

    result, count = pattern.subn(replacement, template, count=1)

    if count != 1:
        raise ValueError(
            "Could not locate the template's 'const CARD_DATA = {...};' block."
        )

    return result


def replace_document_title(template: str, property_name: str) -> str:
    safe_name = html.escape(normalize_name(property_name))
    title = f"<title>{safe_name} · Monopoly Property Card</title>"

    result, count = re.subn(
        r"<title>.*?</title>",
        title,
        template,
        count=1,
        flags=re.DOTALL | re.IGNORECASE,
    )

    if count != 1:
        raise ValueError("Could not locate <title> in HTML template.")

    return result


def safe_filename_component(value: str) -> str:
    value = normalize_name(value)
    value = re.sub(r"[^A-Z0-9]+", "_", value)
    return value.strip("_")


def output_filename(prop: dict[str, Any]) -> str:
    sequence = int(prop["sequence"])
    name = safe_filename_component(prop["name"])
    return f"{sequence:02d}_{name}.html"


def generate_card(
    template: str,
    prop: dict[str, Any],
    output_dir: Path,
    single_line_threshold: int,
) -> Path:
    card_data = make_card_data(prop, single_line_threshold)

    html_text = template
    html_text = apply_palette(html_text, prop["colorGroup"])
    html_text = replace_card_data(html_text, card_data)
    html_text = replace_document_title(html_text, prop["name"])

    output_path = output_dir / output_filename(prop)
    output_path.write_text(html_text, encoding="utf-8")
    return output_path


def load_properties(path: Path) -> list[dict[str, Any]]:
    with path.open("r", encoding="utf-8") as handle:
        data = json.load(handle)

    if not isinstance(data, dict) or "properties" not in data:
        raise ValueError("JSON root must be an object containing a 'properties' array.")

    properties = data["properties"]

    if not isinstance(properties, list):
        raise ValueError("'properties' must be a JSON array.")

    return properties


def main() -> int:
    args = parse_args()

    if args.single_line_threshold < 1:
        print("ERROR: --single-line-threshold must be at least 1.", file=sys.stderr)
        return 2

    try:
        template = args.html_template.read_text(encoding="utf-8")
        properties = load_properties(args.properties_json)

        for index, prop in enumerate(properties, start=1):
            validate_property(prop, index)

        args.output_dir.mkdir(parents=True, exist_ok=True)

        generated: list[Path] = []

        for prop in properties:
            output_path = generate_card(
                template=template,
                prop=prop,
                output_dir=args.output_dir,
                single_line_threshold=args.single_line_threshold,
            )
            generated.append(output_path)

            lines = split_name(prop["name"], args.single_line_threshold)
            print(
                f"[OK] {int(prop['sequence']):02d}  "
                f"{prop['name']}  ->  {output_path.name}  "
                f"| color={prop['colorGroup']} "
                f"| title={' / '.join(lines)}"
            )

        print(f"\nGenerated {len(generated)} HTML file(s) in: {args.output_dir.resolve()}")
        return 0

    except (OSError, json.JSONDecodeError, ValueError, TypeError) as exc:
        print(f"ERROR: {exc}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
