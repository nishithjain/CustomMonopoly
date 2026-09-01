"""Tests for edition validation."""

from __future__ import annotations

import pytest

from monopoly_edition_generator.paths import list_editions
from monopoly_edition_generator.validator import validate_edition


@pytest.mark.parametrize("edition_id", [item["folderName"] for item in list_editions()])
def test_validate_discovered_editions(edition_id: str) -> None:
    result = validate_edition(edition_id)
    assert result.edition_id == edition_id
    assert result.property_count == 22
    assert result.event_count >= 1
    assert result.ok, [issue.format_block() for issue in result.errors]
