"""India board Energy Grid layout, assets, and rendering checks."""

from __future__ import annotations

import re
from pathlib import Path

import pytest

from monopoly_edition_generator.energy_grids import load_energy_grids
from monopoly_edition_generator.generators.board import (
    INDIA_BOARD_VALIDATION,
    INDIA_ENERGY_GRID_BY_SEQUENCE,
    board_pattern_for_edition,
    generate_board,
    generate_board_spaces,
)
from monopoly_edition_generator.paths import (
    ENERGY_GRID_ASSET_FILES,
    ENERGY_GRID_ASSETS_DIR,
    ENERGY_GRID_HEIGHT_CM,
    ENERGY_GRID_WIDTH_CM,
    board_tile_colors,
    currency_symbol,
    load_edition_json,
    load_theme,
)
from monopoly_edition_generator.renderer import render_board_png, target_pixels_for_cm

PILLOW = pytest.importorskip("PIL.Image")


def _india_spaces_js() -> str:
    properties = sorted(load_edition_json("india", "properties.json")["properties"], key=lambda p: p["sequence"])
    banking = load_edition_json("india", "banking_values.json")
    theme = load_theme()
    return generate_board_spaces(
        "india",
        properties,
        currency_symbol(banking),
        banking["locationFee"],
        board_tile_colors(theme),
    )


def _parse_space_sequences(spaces_js: str, space_type: str) -> list[int]:
    pattern = rf'sequence:\s*(\d+),\s*side:\s*"[^"]+",\s*type:\s*"{re.escape(space_type)}"'
    return sorted(int(match) for match in re.findall(pattern, spaces_js))


def _parse_energy_grid_assignments(spaces_js: str) -> dict[int, str]:
    pattern = (
        r'sequence:\s*(\d+),\s*side:\s*"[^"]+",\s*type:\s*"energy-grid",\s*energyGridId:\s*"([^"]+)"'
    )
    return {int(seq): grid_id for seq, grid_id in re.findall(pattern, spaces_js)}


def test_india_board_space_counts() -> None:
    spaces = _india_spaces_js()
    assert spaces.count('type: "property"') == 22
    assert spaces.count('type: "event"') == 4
    assert spaces.count('type: "location"') == 2
    assert spaces.count('type: "energy-grid"') == 4


def test_india_special_space_positions() -> None:
    spaces = _india_spaces_js()
    assert _parse_space_sequences(spaces, "event") == INDIA_BOARD_VALIDATION["eventSequences"]
    assert _parse_space_sequences(spaces, "location") == INDIA_BOARD_VALIDATION["locationSequences"]
    assert _parse_space_sequences(spaces, "energy-grid") == INDIA_BOARD_VALIDATION["energyGridSequences"]


def test_india_energy_grid_ids_resolve() -> None:
    data = load_energy_grids()
    known = {grid["energyGridId"] for grid in data["energyGrids"]}
    assignments = _parse_energy_grid_assignments(_india_spaces_js())
    assert assignments == INDIA_ENERGY_GRID_BY_SEQUENCE
    assert set(assignments.values()) == set(INDIA_BOARD_VALIDATION["energyGridIds"])
    assert set(assignments.values()).issubset(known)


def test_energy_grid_png_assets_exist_and_are_portrait() -> None:
    target_w = target_pixels_for_cm(ENERGY_GRID_WIDTH_CM, 300)
    target_h = target_pixels_for_cm(ENERGY_GRID_HEIGHT_CM, 300)
    for grid_id, filename in ENERGY_GRID_ASSET_FILES.items():
        path = ENERGY_GRID_ASSETS_DIR / filename
        assert path.is_file(), f"Missing Energy Grid PNG for {grid_id}: {path}"
        with PILLOW.open(path) as image:
            width, height = image.size
            assert height > width, f"{filename} should be portrait, got {width}x{height}"
            assert abs(width - target_w) <= 4, f"{filename} width {width}, expected ~{target_w}"
            assert abs(height - target_h) <= 4, f"{filename} height {height}, expected ~{target_h}"


def test_india_board_html_includes_energy_grid_support(tmp_path: Path) -> None:
    board_html = generate_board("india", tmp_path / "board" / "Board_India.html")
    text = board_html.read_text(encoding="utf-8")
    assert "const BOARD_VALIDATION" in text
    assert '"energyGrid": 4' in text
    assert "const ENERGY_GRID_DATA" in text
    assert "const ENERGY_GRID_ASSETS" in text
    assert "function renderEnergyGridTile" in text
    assert 'type: "energy-grid"' in text
    assert "energyGridId:" in text
    assert "space-01" in text and 'type: "property"' in text
    assert "space-32" in text


@pytest.mark.skipif(
    not pytest.importorskip("playwright", reason="Playwright required for board PNG render"),
    reason="Playwright not installed",
)
def test_india_board_png_render_dimensions(tmp_path: Path) -> None:
    board_html = generate_board("india", tmp_path / "board" / "Board_India.html")
    output_png = tmp_path / "board" / "Board_India_300DPI.png"
    render_board_png(board_html, output_png, dpi=300)
    with PILLOW.open(output_png) as image:
        width, height = image.size
        dpi = image.info.get("dpi", (0, 0))
    assert width == height == 5906
    assert dpi[0] == pytest.approx(300, abs=1)
    assert dpi[1] == pytest.approx(300, abs=1)


def test_one_event_and_energy_grid_per_side() -> None:
    pattern = board_pattern_for_edition("india")
    sides = ["bottom", "left", "top", "right"]
    for side_index, side in enumerate(sides):
        start = side_index * 8
        chunk = pattern[start : start + 8]
        assert chunk.count("event") == 1
        assert chunk.count("energy-grid") == 1
