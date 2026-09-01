"""Tests for board generation."""

from __future__ import annotations

from monopoly_edition_generator.generators.board import (
    BOARD_PATTERN,
    INDIA_BOARD_PATTERN,
    INDIA_BOARD_VALIDATION,
    INDIA_ENERGY_GRID_BY_SEQUENCE,
    PROPERTY_BOARD_SEQUENCES,
    UK_BOARD_PATTERN,
    generate_board_spaces,
    split_property_name,
)
from monopoly_edition_generator.paths import load_edition_json, load_theme, board_tile_colors, currency_symbol
from monopoly_edition_generator.validator import validate_edition


def test_board_pattern_has_22_properties() -> None:
    assert BOARD_PATTERN.count("property") == 22
    assert len(PROPERTY_BOARD_SEQUENCES) == 22


def test_split_property_name_two_words() -> None:
    assert split_property_name("PARK LANE") == ["PARK LANE"]


def test_generate_board_spaces_for_india() -> None:
    result = validate_edition("india")
    assert result.ok

    properties = sorted(load_edition_json("india", "properties.json")["properties"], key=lambda p: p["sequence"])
    banking = load_edition_json("india", "banking_values.json")
    theme = load_theme()
    spaces = generate_board_spaces(
        "india",
        properties,
        currency_symbol(banking),
        banking["locationFee"],
        board_tile_colors(theme),
    )
    assert "const boardSpaces = [" in spaces
    assert spaces.count('type: "property"') == 22
    assert spaces.count('type: "event"') == 4
    assert spaces.count('type: "location"') == 2
    assert spaces.count('type: "energy-grid"') == 4


def test_india_board_pattern_counts() -> None:
    assert INDIA_BOARD_PATTERN.count("property") == 22
    assert INDIA_BOARD_PATTERN.count("event") == 4
    assert INDIA_BOARD_PATTERN.count("location") == 2
    assert INDIA_BOARD_PATTERN.count("energy-grid") == 4
    assert UK_BOARD_PATTERN.count("event") == 6
    assert UK_BOARD_PATTERN.count("location") == 4


def test_india_energy_grid_sequences() -> None:
    assert INDIA_BOARD_VALIDATION["energyGridSequences"] == [4, 15, 23, 29]
    assert INDIA_ENERGY_GRID_BY_SEQUENCE == {
        4: "ENG_04",
        15: "ENG_01",
        23: "ENG_03",
        29: "ENG_02",
    }
