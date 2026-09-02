"""Tests for event card generation."""

from __future__ import annotations

from monopoly_edition_generator.event_balance import load_event_balance_config
from monopoly_edition_generator.event_text import resolve_event_description
from monopoly_edition_generator.generators.event_cards import populate_template, safe_filename, title_class
from monopoly_edition_generator.paths import EVENT_CARD_TEMPLATE, load_edition_json, read_text


def test_safe_filename_strips_special_chars() -> None:
    assert safe_filename("Tax Refund!") == "Tax_Refund"


def test_title_class_extra_long_for_cloud_storage() -> None:
    assert title_class("Cloud Storage Subscription Renewal") == "title-extra-long"


def test_populate_event_template() -> None:
    template = read_text(EVENT_CARD_TEMPLATE)
    event = load_edition_json("india", "events.json")["events"][0]
    banking = load_edition_json("india", "banking_values.json")
    balance = load_event_balance_config("india")
    resolved = {
        **event,
        "eventDescription": resolve_event_description(event, "india", banking, balance),
    }
    html = populate_template(template, resolved, edition_id="india")
    assert "@@" not in html
    assert "title-extra-long" in html or "title-" in html
    assert "var(--artwork-size)" in html
    assert 'src="data:image/' in html
    assert str(event["name"]) in html or event["name"].replace("&", "&amp;") in html
    assert "configured" not in html.lower()
