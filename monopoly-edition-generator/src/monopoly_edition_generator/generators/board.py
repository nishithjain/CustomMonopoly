"""Generate board HTML from edition properties and banking values."""

from __future__ import annotations

import json
from pathlib import Path
from typing import Any

from monopoly_edition_generator.energy_grids import asset_filename_for_grid, load_energy_grids
from monopoly_edition_generator.paths import (
    BOARD_TEMPLATE,
    ENERGY_GRID_ASSET_PREFIX,
    GeneratorError,
    board_tile_colors,
    currency_symbol,
    edition_display_token,
    edition_output_dir,
    inner_box_path,
    inner_box_status,
    load_edition_config,
    load_edition_json,
    load_theme,
    numeric_banking_value,
    posix_relative,
    read_text,
    rewrite_board_asset_paths,
    theme_id_for_edition,
)

UK_BOARD_PATTERN = [
    "property", "event", "property", "event",
    "property", "property", "location", "property",
    "property", "event", "property", "property",
    "property", "property", "location", "property",
    "property", "event", "property", "property",
    "property", "property", "location", "property",
    "property", "event", "property", "property",
    "event", "property", "location", "property",
]

INDIA_BOARD_PATTERN = [
    "property", "event", "property", "energy-grid",
    "property", "property", "location", "property",
    "property", "event", "property", "property",
    "property", "property", "energy-grid", "property",
    "property", "event", "property", "property",
    "property", "property", "energy-grid", "property",
    "property", "event", "property", "property",
    "energy-grid", "property", "location", "property",
]

INDIA_ENERGY_GRID_BY_SEQUENCE = {
    4: "ENG_04",
    15: "ENG_01",
    23: "ENG_03",
    29: "ENG_02",
}

BOARD_PATTERN = UK_BOARD_PATTERN

INDIA_BOARD_VALIDATION = {
    "property": 22,
    "event": 4,
    "location": 2,
    "energyGrid": 4,
    "eventSequences": [2, 10, 18, 26],
    "locationSequences": [7, 31],
    "energyGridSequences": [4, 15, 23, 29],
    "requireOneEventPerSide": True,
    "requireOneEnergyGridPerSide": True,
    "energyGridIds": ["ENG_01", "ENG_02", "ENG_03", "ENG_04"],
}

UK_BOARD_VALIDATION = {
    "property": 22,
    "event": 6,
    "location": 4,
    "energyGrid": 0,
    "eventSequences": [],
    "locationSequences": [],
    "energyGridSequences": [],
    "requireOneEventPerSide": False,
    "requireOneEnergyGridPerSide": False,
    "energyGridIds": [],
}

INNER_BOX_TOKEN = "__INNER_BOX_IMAGE__"
BOARD_VALIDATION_TOKEN = "__BOARD_VALIDATION__"
ENERGY_GRID_DATA_TOKEN = "__ENERGY_GRID_DATA__"
ENERGY_GRID_ASSETS_TOKEN = "__ENERGY_GRID_ASSETS__"


def board_pattern_for_edition(edition_id: str) -> list[str]:
    if edition_id == "india":
        return INDIA_BOARD_PATTERN
    return UK_BOARD_PATTERN


def property_board_sequences(pattern: list[str]) -> list[int]:
    return [index + 1 for index, space_type in enumerate(pattern) if space_type == "property"]


PROPERTY_BOARD_SEQUENCES = property_board_sequences(UK_BOARD_PATTERN)


def split_property_name(name: str, max_lines: int = 2) -> list[str]:
    name = " ".join(str(name).strip().split()).upper()
    if not name:
        raise GeneratorError("Property name cannot be empty.")

    words = name.split()
    if len(words) == 1 or max_lines == 1:
        return [name]

    if len(words) == 2 and len(name) <= 12:
        return [name]

    best_index = 1
    best_score = float("inf")
    for index in range(1, len(words)):
        left = " ".join(words[:index])
        right = " ".join(words[index:])
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


def js_string(value: str) -> str:
    return json.dumps(value, ensure_ascii=False)


def format_property_entry(
    board_sequence: int,
    side: str,
    source_property: dict[str, Any],
    currency: str,
    tile_colors: dict[str, dict[str, str]],
) -> str:
    property_sequence = source_property["sequence"]
    name_lines = split_property_name(source_property["name"])
    color_group = str(source_property["colorGroup"]).upper()
    colors = tile_colors[color_group]
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
        f'currency: {js_string(currency)}, '
        f'activeRentLevel: 0, '
        f'colors: {{ dark: "{colors["dark"]}", light: "{colors["light"]}" }} '
        f'}} }},'
    )


def side_for_board_sequence(sequence: int) -> str:
    if 1 <= sequence <= 8:
        return "bottom"
    if 9 <= sequence <= 16:
        return "left"
    if 17 <= sequence <= 24:
        return "top"
    if 25 <= sequence <= 32:
        return "right"
    raise GeneratorError(f"Invalid board sequence: {sequence}")


def generate_board_spaces(
    edition_id: str,
    properties: list[dict[str, Any]],
    currency: str,
    location_fee: int | float,
    tile_colors: dict[str, dict[str, str]],
) -> str:
    pattern = board_pattern_for_edition(edition_id)
    property_iter = iter(properties)
    lines = [
        "        const boardSpaces = [",
        "            // --- BOTTOM (seq 1-8) movement: RIGHT → LEFT ---",
    ]
    side_headings = {
        9: "            // --- LEFT (seq 9-16) movement: BOTTOM → TOP ---",
        17: "            // --- TOP (seq 17-24) movement: LEFT → RIGHT ---",
        25: "            // --- RIGHT (seq 25-32) movement: TOP → BOTTOM ---",
    }

    for board_sequence, space_type in enumerate(pattern, start=1):
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
                    currency,
                    tile_colors,
                )
            )
        elif space_type == "location":
            lines.append(
                f'            {{ id: "space-{board_sequence:02d}", '
                f'sequence: {board_sequence}, side: "{side}", type: "location", '
                f'location: {{ fee: {json.dumps(location_fee, ensure_ascii=False)}, '
                f'currency: {js_string(currency)} }} }},'
            )
        elif space_type == "energy-grid":
            grid_id = INDIA_ENERGY_GRID_BY_SEQUENCE[board_sequence]
            lines.append(
                f'            {{ id: "space-{board_sequence:02d}", '
                f'sequence: {board_sequence}, side: "{side}", type: "energy-grid", '
                f'energyGridId: {js_string(grid_id)} }},'
            )
        else:
            lines.append(
                f'            {{ id: "space-{board_sequence:02d}", '
                f'sequence: {board_sequence}, side: "{side}", type: "{space_type}" }},'
            )

    lines[-1] = lines[-1].rstrip(",")
    lines.append("        ];")
    return "\n".join(lines)


def generate_go_data(currency: str, go_salary: int | float) -> str:
    return "\n".join(
        [
            "        const GO_DATA = {",
            f"            salary: {json.dumps(go_salary, ensure_ascii=False)},",
            f"            currency: {js_string(currency)}",
            "        };",
        ]
    )


def generate_board_validation(edition_id: str) -> str:
    payload = INDIA_BOARD_VALIDATION if edition_id == "india" else UK_BOARD_VALIDATION
    return "        const BOARD_VALIDATION = " + json.dumps(payload, ensure_ascii=False, indent=4) + ";"


def generate_energy_grid_data(edition_id: str) -> str:
    if edition_id != "india":
        return "        const ENERGY_GRID_DATA = null;"
    data = load_energy_grids()
    return "        const ENERGY_GRID_DATA = " + json.dumps(data, ensure_ascii=False, indent=4) + ";"


def generate_energy_grid_assets(edition_id: str) -> str:
    if edition_id != "india":
        return "        const ENERGY_GRID_ASSETS = {};"
    assets = {
        grid_id: f"{ENERGY_GRID_ASSET_PREFIX}/{asset_filename_for_grid(grid_id)}"
        for grid_id in INDIA_BOARD_VALIDATION["energyGridIds"]
    }
    return "        const ENERGY_GRID_ASSETS = " + json.dumps(assets, ensure_ascii=False, indent=4) + ";"


def find_declaration_block(
    html: str,
    marker: str,
    open_char: str,
    close_char: str,
) -> tuple[int, int]:
    marker_index = html.find(marker)
    if marker_index == -1:
        raise GeneratorError(f"Could not find '{marker}' in board template.")

    block_start = html.find(open_char, marker_index)
    if block_start == -1:
        raise GeneratorError(f"Could not find the opening '{open_char}' for {marker!r}.")

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
        raise GeneratorError(f"Could not find the closing '{close_char}' for {marker!r}.")

    semicolon = html.find(";", block_end)
    if semicolon == -1:
        raise GeneratorError(f"Could not find the terminating ';' after {marker!r}.")

    line_start = html.rfind("\n", 0, marker_index) + 1
    return line_start, semicolon + 1


def apply_token(html: str, token: str, replacement: str) -> str:
    if token not in html:
        raise GeneratorError(f"Board template is missing the {token} placeholder.")
    return html.replace(token, replacement)


def apply_inner_box(html: str, edition_id: str, html_output_path: Path) -> tuple[str, dict]:
    status = inner_box_status(edition_id)
    if INNER_BOX_TOKEN not in html:
        raise GeneratorError("Board template is missing the __INNER_BOX_IMAGE__ placeholder.")

    if status["found"]:
        relative = posix_relative(html_output_path.parent, inner_box_path(edition_id))
        replacement = f'url("{relative}")'
        print(f"InnerBox.png found: {status['expectedPath']}")
    else:
        replacement = "none"
        print("InnerBox.png not found. Center board area will remain empty.")

    return html.replace(INNER_BOX_TOKEN, replacement), status


def generate_board(
    edition_id: str,
    output_path: Path | None = None,
    template_path: Path | None = None,
) -> Path:
    properties_data = load_edition_json(edition_id, "properties.json")
    banking_data = load_edition_json(edition_id, "banking_values.json")
    edition_config = load_edition_config(edition_id)
    theme = load_theme(theme_id_for_edition(edition_config))
    tile_colors = board_tile_colors(theme)
    pattern = board_pattern_for_edition(edition_id)

    properties = sorted(properties_data["properties"], key=lambda item: item["sequence"])
    currency = currency_symbol(banking_data)
    location_fee = numeric_banking_value(banking_data, "locationFee")
    go_salary = numeric_banking_value(banking_data, "goSalary")

    template_path = template_path or BOARD_TEMPLATE
    html = read_text(template_path)

    html = apply_token(html, BOARD_VALIDATION_TOKEN, generate_board_validation(edition_id))
    html = apply_token(html, ENERGY_GRID_DATA_TOKEN, generate_energy_grid_data(edition_id))
    html = apply_token(html, ENERGY_GRID_ASSETS_TOKEN, generate_energy_grid_assets(edition_id))

    board_start, board_end = find_declaration_block(html, "const boardSpaces", "[", "]")
    updated_html = (
        html[:board_start]
        + generate_board_spaces(edition_id, properties, currency, location_fee, tile_colors)
        + html[board_end:]
    )
    go_start, go_end = find_declaration_block(updated_html, "const GO_DATA", "{", "}")
    updated_html = (
        updated_html[:go_start]
        + generate_go_data(currency, go_salary)
        + updated_html[go_end:]
    )

    if output_path is None:
        token = edition_display_token(edition_id, edition_config)
        output_path = edition_output_dir(edition_id) / "board" / f"Board_{token}.html"

    output_path.parent.mkdir(parents=True, exist_ok=True)
    updated_html, _inner_status = apply_inner_box(updated_html, edition_id, output_path)
    updated_html = rewrite_board_asset_paths(updated_html, output_path)
    output_path.write_text(updated_html, encoding="utf-8")

    print(f"Updated HTML: {output_path}")
    print(f"Properties loaded: {len(properties)}")
    print(f"Currency symbol: {currency}")
    print(f"GO salary: {go_salary}")
    print(f"Location fee: {location_fee}")
    sequences = property_board_sequences(pattern)
    print("Property board positions: " + ", ".join(map(str, sequences)))
    return output_path
