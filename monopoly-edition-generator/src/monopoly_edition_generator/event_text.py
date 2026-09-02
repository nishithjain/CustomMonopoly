"""Resolve event-card description placeholders from balance and banking configuration."""

from __future__ import annotations

import re
from typing import Any

from monopoly_edition_generator.event_balance import action_for_event
from monopoly_edition_generator.money import (
    BANKING_PLACEHOLDER_KEYS,
    MONETARY_PLACEHOLDER_KEYS,
    format_money,
)
from monopoly_edition_generator.paths import GeneratorError, numeric_banking_value

PLACEHOLDER_PATTERN = re.compile(r"\{([a-zA-Z][a-zA-Z0-9]*)\}")


def compute_reward_amount(action: dict[str, Any], event_id: str) -> int:
    base = action.get("baseRebateAmount")
    multiplier = action.get("rewardMultiplier", 1)
    maximum = action.get("maximumCreditAmount")

    if not isinstance(base, (int, float)) or isinstance(base, bool):
        raise GeneratorError(f"{event_id}: baseRebateAmount must be numeric in balance configuration.")
    if not isinstance(multiplier, (int, float)) or isinstance(multiplier, bool):
        raise GeneratorError(f"{event_id}: rewardMultiplier must be numeric in balance configuration.")

    reward = int(round(base * multiplier))
    if maximum is not None:
        if not isinstance(maximum, (int, float)) or isinstance(maximum, bool):
            raise GeneratorError(f"{event_id}: maximumCreditAmount must be numeric in balance configuration.")
        reward = min(reward, int(maximum))
    return reward


def build_placeholder_values(
    event_id: str,
    action: dict[str, Any],
    banking: dict[str, Any],
    placeholders: set[str],
) -> dict[str, int | float]:
    values: dict[str, int | float] = {}

    for key in placeholders:
        if key in BANKING_PLACEHOLDER_KEYS:
            values[key] = numeric_banking_value(banking, BANKING_PLACEHOLDER_KEYS[key])
            continue

        if key == "rewardAmount":
            values[key] = compute_reward_amount(action, event_id)
            continue

        if key not in action:
            raise GeneratorError(
                f"{event_id}: unresolved placeholder {{{key}}} — field missing from balance configuration action."
            )

        raw = action[key]
        if not isinstance(raw, (int, float)) or isinstance(raw, bool):
            raise GeneratorError(
                f"{event_id}: placeholder {{{key}}} requires a numeric balance-configuration value, got {raw!r}."
            )
        values[key] = raw

    return values


def resolve_event_description(
    event: dict[str, Any],
    edition_id: str,
    banking: dict[str, Any],
    balance_lookup: dict[str, dict[str, Any]] | None,
) -> str:
    description = str(event.get("eventDescription") or "")
    placeholders = set(PLACEHOLDER_PATTERN.findall(description))
    if not placeholders:
        return description

    if balance_lookup is None:
        raise GeneratorError(
            f"{event.get('eventId')}: description contains placeholders {sorted(placeholders)} "
            f"but no balance configuration exists for edition {edition_id!r}."
        )

    event_id = str(event.get("eventId") or "").strip()
    if not event_id:
        raise GeneratorError("Event is missing eventId.")

    action = action_for_event(balance_lookup, event_id)
    values = build_placeholder_values(event_id, action, banking, placeholders)

    resolved = description
    for key in placeholders:
        raw_value = values[key]
        replacement = format_money(raw_value, banking) if key in MONETARY_PLACEHOLDER_KEYS else str(raw_value)
        resolved = resolved.replace(f"{{{key}}}", replacement)

    if PLACEHOLDER_PATTERN.search(resolved):
        remaining = sorted(set(PLACEHOLDER_PATTERN.findall(resolved)))
        raise GeneratorError(f"{event_id}: unresolved placeholders remain: {', '.join(remaining)}")

    if re.search(r"\bconfigured\b", resolved, flags=re.IGNORECASE):
        raise GeneratorError(f"{event_id}: description still contains the word 'configured' after placeholder resolution.")

    return resolved
