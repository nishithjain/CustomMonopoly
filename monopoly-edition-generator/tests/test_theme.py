"""Tests for theme loading."""

from __future__ import annotations

from monopoly_edition_generator.paths import DEFAULT_THEME_ID, THEMES_DIR, load_theme, property_card_base_colors, board_tile_colors


def test_theme_file_exists() -> None:
    path = THEMES_DIR / f"{DEFAULT_THEME_ID}.json"
    assert path.is_file(), path


def test_load_theme_preserves_palette() -> None:
    theme = load_theme()
    assert theme["themeId"] == DEFAULT_THEME_ID
    assert theme["colors"]["propertyCardBase"]["BROWN"] == "#A16434"
    assert theme["colors"]["boardTiles"]["BROWN"]["dark"] == "#7b3e20"
    assert property_card_base_colors(theme)["LIGHT_BLUE"] == "#77BFE8"
    assert board_tile_colors(theme)["DARK_BLUE"]["light"] == "#4f78c8"
