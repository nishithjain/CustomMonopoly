#!/usr/bin/env python3
"""
Generate 4 Energy Grid board-space HTML files from:

    Energy_Grid_Template_Space.html
    energy_grids.json

The script reads Energy Grid data from the JSON and inserts:
    - Energy name
    - Purchase price
    - Energy Grid ID
    - Matching artwork in the middle artwork frame

Run:
    python generate_energy_grid_spaces.py

Generated files are written to:
    generated_energy_grid_spaces/
"""

from __future__ import annotations

import json
import re
from pathlib import Path
from typing import Any

BASE_DIR = Path(__file__).resolve().parent

TEMPLATE_FILE = BASE_DIR / "Energy_Grid_Template_Space.html"
DATA_FILE = BASE_DIR / "energy_grids.json"

if not TEMPLATE_FILE.exists():
    fallback = BASE_DIR / "Energy_Grid_Template_Space(1).html"
    if fallback.exists():
        TEMPLATE_FILE = fallback

if not DATA_FILE.exists():
    fallback = BASE_DIR / "energy_grids(1).json"
    if fallback.exists():
        DATA_FILE = fallback

OUTPUT_DIR = BASE_DIR / "generated_energy_grid_spaces"


def format_indian_number(value: int) -> str:
    value = int(value)
    sign = "-" if value < 0 else ""
    digits = str(abs(value))

    if len(digits) <= 3:
        return sign + digits

    last_three = digits[-3:]
    remaining = digits[:-3]
    groups = []

    while len(remaining) > 2:
        groups.insert(0, remaining[-2:])
        remaining = remaining[:-2]

    if remaining:
        groups.insert(0, remaining)

    return sign + ",".join(groups + [last_three])


def safe_filename(text: str) -> str:
    cleaned = re.sub(r"[^A-Za-z0-9]+", "_", text).strip("_")
    return cleaned or "Energy_Grid"


def find_artwork(grid: dict[str, Any]) -> Path:
    requested = str(grid["artwork"]).strip()
    exact = BASE_DIR / requested

    if exact.exists():
        return exact

    requested_path = Path(requested)
    stem = requested_path.stem
    suffix = requested_path.suffix or ".png"

    candidates = [
        BASE_DIR / f"{stem}(1){suffix}",
    ]

    if "hydroelectric" in str(grid["energyName"]).lower():
        candidates.extend([
            BASE_DIR / "Hydro.png",
            BASE_DIR / "Hydro(1).png",
        ])

    energy_stem = re.sub(r"\s+Energy$", "", str(grid["energyName"]), flags=re.I)
    candidates.extend([
        BASE_DIR / f"{energy_stem}.png",
        BASE_DIR / f"{energy_stem}(1).png",
    ])

    for candidate in candidates:
        if candidate.exists():
            return candidate

    requested_lower = requested.lower()
    for path in BASE_DIR.iterdir():
        if path.is_file() and path.name.lower() == requested_lower:
            return path

    raise FileNotFoundError(
        f"Artwork not found for {grid['energyGridId']} - {grid['energyName']}. "
        f"JSON requests '{requested}'. Keep the PNG in the same folder as this script."
    )


def validate_data(data: dict[str, Any]) -> None:
    for key in ("currency", "purchasePrice", "energyGrids"):
        if key not in data:
            raise ValueError(f"Missing required JSON field: {key}")

    if "symbol" not in data["currency"]:
        raise ValueError("JSON currency.symbol is required.")

    grids = data["energyGrids"]

    if not isinstance(grids, list) or len(grids) != 4:
        raise ValueError(f"Expected exactly 4 energyGrids, found {len(grids) if isinstance(grids, list) else 0}.")

    seen_ids = set()

    for grid in grids:
        for field in ("energyGridId", "energyName", "artwork"):
            if field not in grid or not str(grid[field]).strip():
                raise ValueError(f"Energy Grid is missing '{field}': {grid}")

        if grid["energyGridId"] in seen_ids:
            raise ValueError(f"Duplicate energyGridId: {grid['energyGridId']}")

        seen_ids.add(grid["energyGridId"])


def build_html(
    template: str,
    data: dict[str, Any],
    grid: dict[str, Any],
    artwork_path: Path,
) -> str:
    replacements = {
        "@@ENERGY_NAME@@": str(grid["energyName"]),
        "@@ENERGY_IMAGE@@": artwork_path.name,
        "@@PURCHASE_PRICE@@": format_indian_number(data["purchasePrice"]),
        "@@ENERGY_GRID_ID@@": str(grid["energyGridId"]),
    }

    html = template

    for placeholder, value in replacements.items():
        html = html.replace(placeholder, value)

    if len(str(grid["energyName"])) > 18:
        html = html.replace(
            '<div class="energy-name">',
            '<div class="energy-name long-name">',
            1,
        )

    unresolved = sorted(set(re.findall(r"@@[A-Z0-9_]+@@", html)))

    if unresolved:
        raise ValueError(
            f"Unresolved placeholders for {grid['energyGridId']}: {', '.join(unresolved)}"
        )

    return html


def main() -> None:
    if not TEMPLATE_FILE.exists():
        raise FileNotFoundError("Energy_Grid_Template_Space.html was not found.")

    if not DATA_FILE.exists():
        raise FileNotFoundError("energy_grids.json was not found.")

    with DATA_FILE.open("r", encoding="utf-8") as f:
        data = json.load(f)

    validate_data(data)

    template = TEMPLATE_FILE.read_text(encoding="utf-8")

    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)

    print(f"Template: {TEMPLATE_FILE.name}")
    print(f"Data:     {DATA_FILE.name}")
    print()

    for grid in data["energyGrids"]:
        artwork_path = find_artwork(grid)

        html = build_html(
            template=template,
            data=data,
            grid=grid,
            artwork_path=artwork_path,
        )

        output_name = f"{grid['energyGridId']}_{safe_filename(grid['energyName'])}.html"
        output_path = OUTPUT_DIR / output_name
        output_path.write_text(html, encoding="utf-8")

        print(
            f"{grid['energyGridId']} | {grid['energyName']} | "
            f"{artwork_path.name} -> {output_path.relative_to(BASE_DIR)}"
        )

    print()
    print("Done. Each generated tile remains exactly 4.625 cm x 6.5 cm.")


if __name__ == "__main__":
    main()
