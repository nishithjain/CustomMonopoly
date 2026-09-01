"""Tests for event card generation."""

from __future__ import annotations

from monopoly_edition_generator.generators.event_cards import populate_template, safe_filename
from monopoly_edition_generator.paths import EVENT_CARD_TEMPLATE, load_edition_json, read_text


def test_safe_filename_strips_special_chars() -> None:
    assert safe_filename("Tax Refund!") == "Tax_Refund"


def test_populate_event_template() -> None:
    template = read_text(EVENT_CARD_TEMPLATE)
    event = load_edition_json("india", "events.json")["events"][0]
    html = populate_template(template, event)
    assert "@@" not in html
    assert str(event["name"]) in html or event["name"].replace("&", "&amp;") in html
