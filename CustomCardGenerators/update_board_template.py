#!/usr/bin/env python3
"""
Update the property data inside Board_Template.html from:
  - properties.json
  - banking_values.json

Fields read from properties.json:
  - name
  - sequence
  - colorGroup
  - purchasePrice

Fields read from banking_values.json:
  - currency.symbol
  - goSalary
  - locationFee

The board layout itself is preserved as:
  GO -> P E P E P P L P -> corner
     -> P E P P P P L P -> corner
     -> P E P P P P L P -> corner
     -> P E P P E P L P -> GO

By default the template is updated IN PLACE and a .bak backup is created.
Use --output to write to a different HTML file instead.
"""

from __future__ import annotations

import argparse
import json
import shutil
import sys
from pathlib import Path


# ---------------------------------------------------------------------------
# Board layout: 32 normal spaces, clockwise from GO at bottom-right.
# P = property, E = event, L = location
# ---------------------------------------------------------------------------
BOARD_PATTERN = [
    # Bottom: right -> left
    "property", "event", "property", "event",
    "property", "property", "location", "property",

    # Left: bottom -> top
    "property", "event", "property", "property",
    "property", "property", "location", "property",

    # Top: left -> right
    "property", "event", "property", "property",
    "property", "property", "location", "property",

    # Right: top -> bottom
    "property", "event", "property", "property",
    "event", "property", "location", "property",
]

PROPERTY_BOARD_SEQUENCES = [
    index + 1
    for index, space_type in enumerate(BOARD_PATTERN)
    if space_type == "property"
]

# Visual color mapping used by the existing Board_Template.html property tiles.
# Adjust these hex values here if you want different shades.
COLOR_GROUP_STYLES = {
    "BROWN":      {"dark": "#7b3e20", "light": "#b66536"},
    "LIGHT_BLUE": {"dark": "#4f91ad", "light": "#8fc9df"},
    "PINK":       {"dark": "#a92f70", "light": "#df66a1"},
    "ORANGE":     {"dark": "#c25a2a", "light": "#e88a5a"},
    "RED":        {"dark": "#a52d2d", "light": "#dc5b5b"},
    "YELLOW":     {"dark": "#b89600", "light": "#e3c93e"},
    "GREEN":      {"dark": "#237547", "light": "#55aa73"},
    "DARK_BLUE":  {"dark": "#244c92", "light": "#4f78c8"},
}


def load_json(path: Path) -> dict:
    """Load a UTF-8 JSON file and return the root object."""
    with path.open("r", encoding="utf-8-sig") as file:
        return json.load(file)


def split_property_name(name: str, max_lines: int = 2) -> list[str]:
    """
    Convert a property name to the name-array format expected by the template.

 

    The split is chosen near the middle of the text so the two lines are
    reasonably balanced. Single-word names stay on one line.
    """
    name = " ".join(str(name).strip().split()).upper()

    if not name:
        raise ValueError("Property name cannot be empty.")

    words = name.split()

    if len(words) == 1 or max_lines == 1:
        return [name]

    # Keep short two-word names on one line when practical.
    if len(words) == 2 and len(name) <= 12:
        return [name]

    best_index = 1
    best_score = float("inf")

    for index in range(1, len(words)):
        left = " ".join(words[:index])
        right = " ".join(words[index:])

        # Prefer balanced line lengths while mildly penalising very long lines.
        score = abs(len(left) - len(right))
        score += max(0, len(left) - 14) * 2
        score += max(0, len(right) - 14) * 2

        if score < best_score:
            best_score = score
            best_index = index

    return [
        " ".join(words[:best_index]),
        " ".join(words[best_index:]),
    ]


def validate_and_sort_properties(properties_data: dict) -> list[dict]:
    """Validate the source property data and return properties sorted by sequence."""
    properties = properties_data.get("properties")

    if not isinstance(properties, list):
        raise ValueError("properties.json must contain a top-level 'properties' array.")

    if len(properties) != 22:
        raise ValueError(
            f"Expected exactly 22 properties, but properties.json contains {len(properties)}."
        )

    required_fields = {"name", "sequence", "colorGroup", "purchasePrice"}
    sequences = set()

    for index, prop in enumerate(properties, start=1):
        missing = required_fields - prop.keys()
        if missing:
            raise ValueError(
                f"Property entry #{index} is missing: {', '.join(sorted(missing))}"
            )

        sequence = prop["sequence"]

        if not isinstance(sequence, int) or not 1 <= sequence <= 22:
            raise ValueError(
                f"Invalid property sequence {sequence!r} for {prop.get('name', index)!r}; "
                "expected an integer from 1 to 22."
            )

        if sequence in sequences:
            raise ValueError(f"Duplicate property sequence: {sequence}")

        sequences.add(sequence)

        color_group = str(prop["colorGroup"]).upper()
        if color_group not in COLOR_GROUP_STYLES:
            raise ValueError(
                f"Unsupported colorGroup {color_group!r} for {prop['name']!r}. "
                f"Supported groups: {', '.join(COLOR_GROUP_STYLES)}"
            )

    expected = set(range(1, 23))
    if sequences != expected:
        missing = sorted(expected - sequences)
        extra = sorted(sequences - expected)
        raise ValueError(
            f"Property sequences must be exactly 1..22. Missing={missing}, extra={extra}"
        )

    return sorted(properties, key=lambda item: item["sequence"])


def get_currency_symbol(banking_data: dict) -> str:
    """Read currency.symbol from banking_values.json."""
    try:
        symbol = banking_data["currency"]["symbol"]
    except (KeyError, TypeError) as exc:
        raise ValueError(
            "banking_values.json must contain currency.symbol"
        ) from exc

    symbol = str(symbol)

    if not symbol:
        raise ValueError("currency.symbol cannot be empty.")

    return symbol


def get_numeric_value(banking_data: dict, key: str):
    """Read a numeric top-level value from banking_values.json."""
    if key not in banking_data:
        raise ValueError(f"banking_values.json must contain {key}")

    value = banking_data[key]

    if not isinstance(value, (int, float)) or isinstance(value, bool):
        raise ValueError(f"{key} must be numeric.")

    return value


def js_string(value: str) -> str:
    """Return a safely escaped JavaScript string literal using JSON encoding."""
    return json.dumps(value, ensure_ascii=False)


def format_property_entry(
    board_sequence: int,
    side: str,
    source_property: dict,
    currency_symbol: str,
) -> str:
    """Create one property entry for the JavaScript boardSpaces array."""
    property_sequence = source_property["sequence"]
    name_lines = split_property_name(source_property["name"])
    color_group = str(source_property["colorGroup"]).upper()
    colors = COLOR_GROUP_STYLES[color_group]

    name_js = "[" + ", ".join(js_string(line) for line in name_lines) + "]"

    return (
        f'            {{ id: "space-{board_sequence:02d}", '
        f'sequence: {board_sequence}, side: "{side}", type: "property", '
        f'property: {{ '
        f'number: {property_sequence}, '
        f'sequence: {property_sequence}, '
        f'name: {name_js}, '
        f'colorGroup: {js_string(color_group)}, '
        f'purchasePrice: {json.dumps(source_property["purchasePrice"], ensure_ascii=False)}, '
        f'currency: {js_string(currency_symbol)}, '
        f'activeRentLevel: 0, '
        f'colors: {{ dark: "{colors["dark"]}", light: "{colors["light"]}" }} '
        f'}} }},'
    )


def side_for_board_sequence(sequence: int) -> str:
    """Return the physical side for board movement sequence 1..32."""
    if 1 <= sequence <= 8:
        return "bottom"
    if 9 <= sequence <= 16:
        return "left"
    if 17 <= sequence <= 24:
        return "top"
    if 25 <= sequence <= 32:
        return "right"
    raise ValueError(f"Invalid board sequence: {sequence}")


def generate_board_spaces(
    properties: list[dict],
    currency_symbol: str,
    location_fee,
) -> str:
    """Generate the complete JavaScript boardSpaces declaration."""
    property_iter = iter(properties)
    lines = [
        "        const boardSpaces = [",
        "            // --- BOTTOM (seq 1-8) movement: RIGHT → LEFT ---",
    ]

    side_headings = {
        9:  "            // --- LEFT (seq 9-16) movement: BOTTOM → TOP ---",
        17: "            // --- TOP (seq 17-24) movement: LEFT → RIGHT ---",
        25: "            // --- RIGHT (seq 25-32) movement: TOP → BOTTOM ---",
    }

    for board_sequence, space_type in enumerate(BOARD_PATTERN, start=1):
        if board_sequence in side_headings:
            lines.append("")
            lines.append(side_headings[board_sequence])

        side = side_for_board_sequence(board_sequence)

        if space_type == "property":
            source_property = next(property_iter)
            lines.append(
                format_property_entry(
                    board_sequence,
                    side,
                    source_property,
                    currency_symbol,
                )
            )
        elif space_type == "location":
            lines.append(
                f'            {{ id: "space-{board_sequence:02d}", '
                f'sequence: {board_sequence}, side: "{side}", type: "location", '
                f'location: {{ fee: {json.dumps(location_fee, ensure_ascii=False)}, '
                f'currency: {js_string(currency_symbol)} }} }},'
            )
        else:
            lines.append(
                f'            {{ id: "space-{board_sequence:02d}", '
                f'sequence: {board_sequence}, side: "{side}", type: "{space_type}" }},'
            )

    # Remove trailing comma from the final array entry.
    lines[-1] = lines[-1].rstrip(",")

    lines.append("        ];")
    return "\n".join(lines)


def generate_go_data(currency_symbol: str, go_salary) -> str:
    """Generate the JavaScript GO_DATA declaration shown on the GO corner."""
    return "\n".join(
        [
            "        const GO_DATA = {",
            f"            salary: {json.dumps(go_salary, ensure_ascii=False)},",
            f"            currency: {js_string(currency_symbol)}",
            "        };",
        ]
    )


def find_declaration_block(
    html: str,
    marker: str,
    open_char: str,
    close_char: str,
) -> tuple[int, int]:
    """
    Locate a `const <name> = <open_char> ... <close_char>;` declaration.

    Uses bracket matching that skips string literals, so nested arrays or
    objects inside the declaration do not terminate the match early.
    Returns (start_index, end_index), where end_index is exclusive.
    """
    marker_index = html.find(marker)

    if marker_index == -1:
        raise ValueError(f"Could not find '{marker}' in Board_Template.html.")

    block_start = html.find(open_char, marker_index)
    if block_start == -1:
        raise ValueError(f"Could not find the opening '{open_char}' for {marker!r}.")

    depth = 0
    in_string = False
    quote = ""
    escape = False
    block_end = None

    for index in range(block_start, len(html)):
        char = html[index]

        if in_string:
            if escape:
                escape = False
            elif char == "\\":
                escape = True
            elif char == quote:
                in_string = False
            continue

        if char in ('"', "'", "`"):
            in_string = True
            quote = char
            continue

        if char == open_char:
            depth += 1
        elif char == close_char:
            depth -= 1
            if depth == 0:
                block_end = index
                break

    if block_end is None:
        raise ValueError(f"Could not find the closing '{close_char}' for {marker!r}.")

    semicolon = html.find(";", block_end)
    if semicolon == -1:
        raise ValueError(f"Could not find the terminating ';' after {marker!r}.")

    # Start at the indentation before the marker so formatting stays clean.
    line_start = html.rfind("\n", 0, marker_index) + 1

    return line_start, semicolon + 1


def update_template(
    properties_path: Path,
    banking_path: Path,
    template_path: Path,
    output_path: Path | None,
) -> Path:
    """Read source JSON files, replace boardSpaces, and save the updated HTML."""
    properties_data = load_json(properties_path)
    banking_data = load_json(banking_path)

    properties = validate_and_sort_properties(properties_data)
    currency_symbol = get_currency_symbol(banking_data)
    location_fee = get_numeric_value(banking_data, "locationFee")
    go_salary = get_numeric_value(banking_data, "goSalary")

    html = template_path.read_text(encoding="utf-8")

    board_start, board_end = find_declaration_block(html, "const boardSpaces", "[", "]")
    updated_html = (
        html[:board_start]
        + generate_board_spaces(properties, currency_symbol, location_fee)
        + html[board_end:]
    )

    go_start, go_end = find_declaration_block(updated_html, "const GO_DATA", "{", "}")
    updated_html = (
        updated_html[:go_start]
        + generate_go_data(currency_symbol, go_salary)
        + updated_html[go_end:]
    )

    if output_path is None:
        output_path = template_path
        backup_path = template_path.with_suffix(template_path.suffix + ".bak")
        shutil.copy2(template_path, backup_path)
        print(f"Backup created: {backup_path}")

    output_path.write_text(updated_html, encoding="utf-8")

    print(f"Updated HTML: {output_path}")
    print(f"Properties loaded: {len(properties)}")
    print(f"Currency symbol: {currency_symbol}")
    print(f"GO salary: {go_salary}")
    print(f"Location fee: {location_fee}")
    print(
        "Property board positions: "
        + ", ".join(map(str, PROPERTY_BOARD_SEQUENCES))
    )

    return output_path


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Populate Board_Template.html from properties.json and banking_values.json."
    )

    parser.add_argument(
        "--properties",
        type=Path,
        default=Path("properties.json"),
        help="Path to properties.json (default: properties.json)",
    )
    parser.add_argument(
        "--banking",
        type=Path,
        default=Path("banking_values.json"),
        help="Path to banking_values.json (default: banking_values.json)",
    )
    parser.add_argument(
        "--template",
        type=Path,
        default=Path("Board_Template.html"),
        help="Path to Board_Template.html (default: Board_Template.html)",
    )
    parser.add_argument(
        "--output",
        type=Path,
        default=None,
        help=(
            "Optional output HTML path. "
            "If omitted, Board_Template.html is updated in place and a .bak backup is created."
        ),
    )

    return parser.parse_args()


def main() -> None:
    # Currency symbols such as ₹ are unprintable on legacy Windows consoles.
    for stream in (sys.stdout, sys.stderr):
        try:
            stream.reconfigure(encoding="utf-8", errors="replace")
        except (AttributeError, ValueError):
            pass

    args = parse_args()

    for label, path in (
        ("properties", args.properties),
        ("banking", args.banking),
        ("template", args.template),
    ):
        if not path.is_file():
            raise FileNotFoundError(f"{label} file not found: {path}")

    update_template(
        properties_path=args.properties,
        banking_path=args.banking,
        template_path=args.template,
        output_path=args.output,
    )


if __name__ == "__main__":
    main()
