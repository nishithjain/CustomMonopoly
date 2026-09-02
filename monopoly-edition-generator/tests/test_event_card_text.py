"""Tests for event-card monetary placeholder resolution."""

from __future__ import annotations

import re

import pytest

from monopoly_edition_generator.event_text import resolve_event_description
from monopoly_edition_generator.generators.event_cards import generate_event_cards, populate_template
from monopoly_edition_generator.money import format_money
from monopoly_edition_generator.paths import EVENT_CARD_TEMPLATE, GeneratorError, load_edition_json, read_text

PLACEHOLDER_PATTERN = re.compile(r"\{[a-zA-Z][a-zA-Z0-9]*\}")


def _india_banking() -> dict:
    return load_edition_json("india", "banking_values.json")


def _india_event(event_id: str) -> dict:
    events = load_edition_json("india", "events.json")["events"]
    return next(event for event in events if event["eventId"] == event_id)


def test_format_money_india_uses_symbol_and_grouping() -> None:
    assert format_money(10000, _india_banking()) == "₹10,000"


def test_hospital_expense_displays_configured_amount() -> None:
    text = resolve_event_description(_india_event("EVT_05"), "india", _india_banking(), _balance_lookup())
    assert text == "Pay ₹10,000 to the bank."


def test_festival_contribution_displays_amount_per_player() -> None:
    text = resolve_event_description(_india_event("EVT_06"), "india", _india_banking(), _balance_lookup())
    assert text == "Pay ₹5,000 to each other player."


def test_municipal_maintenance_displays_amount_per_property() -> None:
    text = resolve_event_description(_india_event("EVT_08"), "india", _india_banking(), _balance_lookup())
    assert text == "Pay ₹2,000 to the bank for each Property you own."


def test_lucky_break_displays_jackpot_and_penalty() -> None:
    text = resolve_event_description(_india_event("EVT_17"), "india", _india_banking(), _balance_lookup())
    assert "₹15,000" in text
    assert "₹5,000" in text
    assert "configured" not in text.lower()


def test_economic_relief_grant_displays_threshold() -> None:
    text = resolve_event_description(_india_event("EVT_20"), "india", _india_banking(), _balance_lookup())
    assert text.count("₹30,000") == 2


def test_advance_to_go_reads_go_salary_from_banking_values() -> None:
    text = resolve_event_description(_india_event("EVT_01"), "india", _india_banking(), _balance_lookup())
    assert text == f"Move directly to GO and collect {format_money(20000, _india_banking())} once."


def test_get_out_of_jail_pass_reads_jail_fee_from_banking_values() -> None:
    text = resolve_event_description(_india_event("EVT_11"), "india", _india_banking(), _balance_lookup())
    assert format_money(10000, _india_banking()) in text


def test_green_energy_rebate_displays_multiplied_reward() -> None:
    text = resolve_event_description(_india_event("EVT_25"), "india", _india_banking(), _balance_lookup())
    assert text == f"If you own at least one complete colour group, collect {format_money(10000, _india_banking())} from the bank."


def test_printed_descriptions_exclude_engine_rules() -> None:
    for event_id in ("EVT_17", "EVT_19", "EVT_24"):
        text = resolve_event_description(_india_event(event_id), "india", _india_banking(), _balance_lookup())
        assert "Recommended" not in text
        assert "SKIP_NEXT_TURN" not in text
        assert "Do not move the token" not in text


def test_changing_json_amount_changes_generated_card_text(tmp_path, monkeypatch) -> None:
    banking = _india_banking()
    balance = _balance_lookup()
    event = _india_event("EVT_05")
    action = balance["EVT_05"]["actions"][0]
    action["amount"] = 12345

    text = resolve_event_description(event, "india", banking, balance)
    assert text == f"Pay {format_money(12345, banking)} to the bank."


def test_missing_configuration_raises_clear_error() -> None:
    event = _india_event("EVT_05")
    with pytest.raises(GeneratorError, match="EVT_05"):
        resolve_event_description(event, "india", _india_banking(), None)


def test_generated_india_event_cards_have_no_placeholders_or_configured(tmp_path, monkeypatch) -> None:
    from monopoly_edition_generator import paths as paths_module

    monkeypatch.setattr(paths_module, "OUTPUT_ROOT", tmp_path)
    monkeypatch.setattr(
        paths_module,
        "edition_output_dir",
        lambda edition_id: tmp_path / edition_id,
    )

    generated = generate_event_cards("india")
    assert len(generated) == 25

    for path in generated:
        html = path.read_text(encoding="utf-8")
        assert not PLACEHOLDER_PATTERN.search(html), path.name
        assert "configured" not in html.lower(), path.name
        assert "Recommended rules" not in html, path.name
        assert "Recommended resolution" not in html, path.name
        assert "SKIP_NEXT_TURN" not in html, path.name
        assert "grid-template-rows: auto auto 1fr" not in html, path.name
        assert html.count('class="event-artwork"') == 1, path.name
        assert 'src="data:image/' in html, path.name
        assert "C:\\Personal\\" not in html, path.name
        assert "file:///" not in html, path.name
        assert "../../assets" not in html, path.name


def _balance_lookup():
    from monopoly_edition_generator.event_balance import load_event_balance_config

    lookup = load_event_balance_config("india")
    assert lookup is not None
    return lookup
