"""Tests for property card generation."""

from __future__ import annotations

from monopoly_edition_generator.generators.property_cards import split_name
from monopoly_edition_generator.paths import load_edition_json, load_theme, property_card_base_colors
from monopoly_edition_generator.generators.property_cards import palette_for_color_group


def test_split_name_short_stays_single_line() -> None:
    assert split_name("MAYFAIR") == ["MAYFAIR"]


def test_palette_for_known_color_group() -> None:
    theme = load_theme()
    colors = property_card_base_colors(theme)
    palette = palette_for_color_group("BROWN", colors)
    assert palette["property_dark"].startswith("#")
    assert len(palette["rent_rows"]) == 5


def test_india_properties_have_required_fields() -> None:
    properties = load_edition_json("india", "properties.json")["properties"]
    assert len(properties) == 22
    for prop in properties:
        assert prop["name"]
        assert prop["colorGroup"]
        assert prop["rentLevels"]
