#!/usr/bin/env python3
"""
Generate four Renewable Energy Grid HTML cards from:
  - Energy_Grid_Template_Card.html
  - energy_grids.json
  - Solar.png
  - Wind.png
  - Hydroelectric.png
  - Biomass.png

Place this script, the JSON file, the template, and all four PNG files
in the same directory, then run:

    python generate_energy_grid_cards.py

Generated HTML files are written to:
    generated_energy_grid_cards/
"""

from __future__ import annotations

import json
import re
from pathlib import Path
from typing import Any


BASE_DIR = Path(__file__).resolve().parent
TEMPLATE_FILE = BASE_DIR / "Energy_Grid_Template_Card.html"
DATA_FILE = BASE_DIR / "energy_grids.json"
OUTPUT_DIR = BASE_DIR / "generated_energy_grid_cards"


def format_money(amount: int, symbol: str) -> str:
    """Format an integer using Indian digit grouping, e.g. ₹20,000 or ₹1,20,000."""
    sign = "-" if amount < 0 else ""
    digits = str(abs(int(amount)))

    if len(digits) <= 3:
        grouped = digits
    else:
        last_three = digits[-3:]
        remaining = digits[:-3]
        pairs = []

        while len(remaining) > 2:
            pairs.insert(0, remaining[-2:])
            remaining = remaining[:-2]

        if remaining:
            pairs.insert(0, remaining)

        grouped = ",".join(pairs + [last_three])

    return f"{symbol}{sign}{grouped}"


def choose_title_font_size(name: str) -> str:
    """
    Return a CSS physical font size suitable for the existing card title banner.
    The template expects the replacement including the CSS unit.
    """
    length = len(name.strip())

    if length <= 12:
        return "6.0mm"
    if length <= 18:
        return "5.5mm"
    if length <= 22:
        return "4.9mm"
    return "4.4mm"


def purchase_font_size() -> str:
    return "5.5mm"


def load_data() -> dict[str, Any]:
    if not DATA_FILE.exists():
        raise FileNotFoundError(f"Missing data file: {DATA_FILE.name}")

    with DATA_FILE.open("r", encoding="utf-8") as f:
        return json.load(f)


def validate_data(data: dict[str, Any]) -> None:
    required_top_level = {
        "currency",
        "groupId",
        "groupName",
        "purchasePrice",
        "rent",
        "energyGrids",
    }

    missing = required_top_level.difference(data.keys())
    if missing:
        raise ValueError(f"Missing JSON keys: {', '.join(sorted(missing))}")

    grids = data["energyGrids"]
    rents = data["rent"]

    if len(grids) != 4:
        raise ValueError(f"Expected 4 energy grids, found {len(grids)}.")

    if len(rents) != 4:
        raise ValueError(f"Expected 4 rent levels, found {len(rents)}.")

    expected_counts = [1, 2, 3, 4]
    actual_counts = [int(item["ownedCount"]) for item in rents]

    if actual_counts != expected_counts:
        raise ValueError(
            f"Rent ownedCount values must be {expected_counts}, found {actual_counts}."
        )

    ids = [grid["energyGridId"] for grid in grids]
    if len(ids) != len(set(ids)):
        raise ValueError("energyGridId values must be unique.")

    for grid in grids:
        for key in ("energyGridId", "energyName", "artwork"):
            if key not in grid or not str(grid[key]).strip():
                raise ValueError(
                    f"Energy grid entry is missing a valid '{key}': {grid}"
                )

        artwork_path = BASE_DIR / grid["artwork"]
        if not artwork_path.exists():
            raise FileNotFoundError(
                f"Artwork '{grid['artwork']}' for {grid['energyName']} was not found "
                f"in {BASE_DIR}"
            )


def inject_artwork(html: str, image_filename: str, alt_text: str) -> str:
    """
    Activate the artwork area in the existing template.

    The current template intentionally contains a commented-out <img> example.
    This function replaces the CONTENT of .energy-image with a live image tag
    while leaving the surrounding template design unchanged.
    """
    replacement = (
        '<div class="energy-image">\n'
        f'            <img src="../{image_filename}" alt="{alt_text}">\n'
        '        </div>'
    )

    pattern = re.compile(
        r'<div class="energy-image">\s*'
        r'(?:<!--.*?-->\s*)?'
        r'</div>',
        flags=re.DOTALL,
    )

    updated, count = pattern.subn(replacement, html, count=1)

    if count != 1:
        raise ValueError(
            "Could not locate the .energy-image block in "
            "Energy_Grid_Template_Card.html."
        )

    return updated


def build_card_html(
    template: str,
    data: dict[str, Any],
    grid: dict[str, Any],
) -> str:
    symbol = data["currency"]["symbol"]
    purchase_price = int(data["purchasePrice"])
    rents = {int(x["ownedCount"]): int(x["amount"]) for x in data["rent"]}

    replacements = {
        "@@PAGE_TITLE@@": f"{grid['energyName']} - Renewable Energy Grid",
        "@@ENERGY_NAME@@": grid["energyName"],
        "@@PURCHASE_PRICE@@": format_money(purchase_price, symbol),
        "@@FEE_1@@": format_money(rents[1], symbol),
        "@@FEE_2@@": format_money(rents[2], symbol),
        "@@FEE_3@@": format_money(rents[3], symbol),
        "@@FEE_4@@": format_money(rents[4], symbol),
        "@@ENERGY_GRID_ID@@": grid["energyGridId"],
        "@@TITLE_FONT_SIZE@@": choose_title_font_size(grid["energyName"]),
        "@@PURCHASE_FONT_SIZE@@": purchase_font_size(),
        # This placeholder exists in the template's explanatory comment.
        # It is replaced as well so there are no unresolved placeholders.
        "@@ENERGY_IMAGE@@": grid["artwork"],
    }

    html = template

    for placeholder, value in replacements.items():
        html = html.replace(placeholder, str(value))

    html = inject_artwork(
        html,
        image_filename=grid["artwork"],
        alt_text=grid["energyName"],
    )

    unresolved = sorted(set(re.findall(r"@@[A-Z0-9_]+@@", html)))
    if unresolved:
        raise ValueError(
            f"Unresolved placeholders for {grid['energyName']}: {unresolved}"
        )

    return html


def safe_filename(name: str) -> str:
    cleaned = re.sub(r"[^A-Za-z0-9]+", "_", name).strip("_")
    return f"{cleaned}.html"


def main() -> None:
    if not TEMPLATE_FILE.exists():
        raise FileNotFoundError(f"Missing template: {TEMPLATE_FILE.name}")

    data = load_data()
    validate_data(data)

    template = TEMPLATE_FILE.read_text(encoding="utf-8")

    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)

    generated_files = []

    for grid in data["energyGrids"]:
        html = build_card_html(template, data, grid)
        output_path = OUTPUT_DIR / safe_filename(grid["energyName"])
        output_path.write_text(html, encoding="utf-8")
        generated_files.append(output_path)

    print("Energy Grid cards generated successfully:")
    for path in generated_files:
        print(f"  - {path.relative_to(BASE_DIR)}")


if __name__ == "__main__":
    main()
