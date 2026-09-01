"""Tests for board generation."""

from __future__ import annotations

from monopoly_edition_generator.generators.board import (
    BOARD_PATTERN,
    PROPERTY_BOARD_SEQUENCES,
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
        properties,
        currency_symbol(banking),
        banking["locationFee"],
        board_tile_colors(theme),
    )
    assert "const boardSpaces = [" in spaces
    assert spaces.count('type: "property"') == 22
    assert spaces.count('type: "event"') == 6
    assert spaces.count('type: "location"') == 4
