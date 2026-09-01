#!/usr/bin/env python3
"""Render Energy Grid board-space HTML tiles to portrait PNG assets."""

from __future__ import annotations

import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT / "src"
if str(SRC) not in sys.path:
    sys.path.insert(0, str(SRC))

from monopoly_edition_generator.paths import (  # noqa: E402
    ENERGY_GRID_ASSETS_DIR,
    ENERGY_GRID_BOARD_DIR,
    ENERGY_GRID_HEIGHT_CM,
    ENERGY_GRID_HTML_SOURCES,
    ENERGY_GRID_WIDTH_CM,
    configure_stdio,
)
from monopoly_edition_generator.renderer import render_card_pngs  # noqa: E402


def main() -> int:
    configure_stdio()
    ENERGY_GRID_ASSETS_DIR.mkdir(parents=True, exist_ok=True)

    jobs: list[tuple[Path, Path]] = []
    for grid_id, html_name in ENERGY_GRID_HTML_SOURCES.items():
        html_path = ENERGY_GRID_BOARD_DIR / html_name
        if not html_path.is_file():
            raise FileNotFoundError(f"Missing Energy Grid HTML: {html_path}")
        output_name = {
            "ENG_01": "eng_01_solar.png",
            "ENG_02": "eng_02_wind.png",
            "ENG_03": "eng_03_hydroelectric.png",
            "ENG_04": "eng_04_biomass.png",
        }[grid_id]
        jobs.append((html_path, ENERGY_GRID_ASSETS_DIR / output_name))

    width_mm = ENERGY_GRID_WIDTH_CM * 10
    height_mm = ENERGY_GRID_HEIGHT_CM * 10
    created = render_card_pngs(jobs, ".energy-grid-tile", width_mm=width_mm, height_mm=height_mm, dpi=300)

    for path in created:
        print(f"Created: {path}")

    print()
    print(f"Rendered {len(created)} Energy Grid board-space PNG(s) at {ENERGY_GRID_WIDTH_CM}cm × {ENERGY_GRID_HEIGHT_CM}cm, 300 DPI.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
