"""Load per-edition event balance configuration for card text resolution."""

from __future__ import annotations

from typing import Any

from monopoly_edition_generator.paths import GeneratorError, edition_dir, load_json


def event_balance_config_path(edition_id: str):
    return edition_dir(edition_id) / f"{edition_id}_event_balance_config.json"


def load_event_balance_config(edition_id: str) -> dict[str, dict[str, Any]] | None:
    path = event_balance_config_path(edition_id)
    if not path.is_file():
        return None

    data = load_json(path)
    if not isinstance(data, dict):
        raise GeneratorError(f"{path} must be a JSON object.")

    events = data.get("events")
    if not isinstance(events, list):
        raise GeneratorError(f"{path} must contain an 'events' array.")

    lookup: dict[str, dict[str, Any]] = {}
    for entry in events:
        if not isinstance(entry, dict):
            raise GeneratorError(f"{path} contains a non-object event entry.")
        event_id = str(entry.get("eventId") or "").strip()
        if not event_id:
            raise GeneratorError(f"{path} contains an event entry without eventId.")
        if event_id in lookup:
            raise GeneratorError(f"{path} contains duplicate eventId {event_id!r}.")
        lookup[event_id] = entry
    return lookup


def action_for_event(balance_lookup: dict[str, dict[str, Any]], event_id: str) -> dict[str, Any]:
    entry = balance_lookup.get(event_id)
    if entry is None:
        raise GeneratorError(f"{event_id}: missing event balance configuration.")

    actions = entry.get("actions")
    if not isinstance(actions, list) or not actions:
        raise GeneratorError(f"{event_id}: event balance configuration must contain at least one action.")
    if len(actions) != 1:
        raise GeneratorError(f"{event_id}: expected exactly one action in balance configuration.")

    action = actions[0]
    if not isinstance(action, dict):
        raise GeneratorError(f"{event_id}: balance action must be an object.")
    return action
