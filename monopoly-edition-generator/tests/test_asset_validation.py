"""Tests for board-space asset validation."""

from __future__ import annotations

from pathlib import Path

import pytest

from monopoly_edition_generator import validator as validator_module
from monopoly_edition_generator.paths import REQUIRED_BOARD_ASSETS
from monopoly_edition_generator.validator import ValidationResult, _validate_assets


def test_required_board_assets_list_includes_event_and_location() -> None:
    assert "event-space.png" in REQUIRED_BOARD_ASSETS
    assert "location-space.png" in REQUIRED_BOARD_ASSETS
    assert len(REQUIRED_BOARD_ASSETS) == 6


def test_missing_event_asset_fails_validation(tmp_path: Path, monkeypatch: pytest.MonkeyPatch) -> None:
    assets = tmp_path / "board-spaces"
    assets.mkdir()
    for name in REQUIRED_BOARD_ASSETS:
        if name == "event-space.png":
            continue
        (assets / name).write_bytes(b"png")

    monkeypatch.setattr(validator_module, "BOARD_SPACES_DIR", assets)
    result = ValidationResult(edition_id="india", ok=True)
    _validate_assets(result)
    assert not result.ok
    assert any(issue.field == "event-space.png" for issue in result.errors)


def test_missing_location_asset_fails_validation(tmp_path: Path, monkeypatch: pytest.MonkeyPatch) -> None:
    assets = tmp_path / "board-spaces"
    assets.mkdir()
    for name in REQUIRED_BOARD_ASSETS:
        if name == "location-space.png":
            continue
        (assets / name).write_bytes(b"png")

    monkeypatch.setattr(validator_module, "BOARD_SPACES_DIR", assets)
    result = ValidationResult(edition_id="india", ok=True)
    _validate_assets(result)
    assert not result.ok
    assert any(issue.field == "location-space.png" for issue in result.errors)
