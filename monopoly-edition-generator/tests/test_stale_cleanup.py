"""Tests for stale output cleanup."""

from __future__ import annotations

from pathlib import Path

from monopoly_edition_generator.paths import clean_stale_outputs, edition_output_dir


def test_clean_stale_outputs_only_selected_artifact(tmp_path: Path, monkeypatch) -> None:
    edition_id = "cleanup-test"
    base = tmp_path / "output" / edition_id
    property_html = base / "property_cards" / "html" / "01_TEST.html"
    event_html = base / "event_cards" / "html" / "E01_TEST.html"
    board_html = base / "board" / "Board_Test.html"
    other_edition = tmp_path / "output" / "other" / "property_cards" / "html" / "01_KEEP.html"

    for path in (property_html, event_html, board_html, other_edition):
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text("x", encoding="utf-8")

    monkeypatch.setattr(
        "monopoly_edition_generator.paths.OUTPUT_ROOT",
        tmp_path / "output",
    )
    assert edition_output_dir(edition_id) == base

    clean_stale_outputs(edition_id, properties=True, events=False, board=False)

    assert not property_html.exists()
    assert event_html.exists()
    assert board_html.exists()
    assert other_edition.exists()
