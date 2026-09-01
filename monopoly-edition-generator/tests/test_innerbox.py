"""Tests confirming legacy common InnerBox is unused."""

from __future__ import annotations

from monopoly_edition_generator.paths import LEGACY_COMMON_INNERBOX, inner_box_path


def test_legacy_common_innerbox_is_not_present() -> None:
    assert not LEGACY_COMMON_INNERBOX.exists()


def test_inner_box_comes_from_resources_not_assets() -> None:
    path = inner_box_path("india")
    assert "Resources" in path.parts
    assert "assets" not in path.parts
