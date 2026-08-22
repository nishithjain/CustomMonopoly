#!/usr/bin/env python3
"""Compatibility wrapper. Prefer: python generate.py <edition> --only events"""

from __future__ import annotations

import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from generator.event_generator import generate_event_cards
from generator.utils import load_json, configure_stdio, GeneratorError


def main() -> None:
    configure_stdio()
    if len(sys.argv) < 2:
        json_path = Path("events.json")
    else:
        json_path = Path(sys.argv[1])

    output_dir = Path(sys.argv[2]) if len(sys.argv) >= 3 else Path("generated_event_cards")

    if not json_path.exists():
        raise FileNotFoundError(f"JSON file not found: {json_path.resolve()}")

    data = load_json(json_path)
    events = data.get("events")
    if not isinstance(events, list):
        raise GeneratorError('Expected the JSON file to contain an "events" array.')

    generate_event_cards(
        edition_id="custom",
        output_html_dir=output_dir,
        events=events,
    )


if __name__ == "__main__":
    main()
