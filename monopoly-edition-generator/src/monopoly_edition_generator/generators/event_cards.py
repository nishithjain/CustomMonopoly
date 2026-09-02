"""Generate event-card HTML from edition events.json."""

from __future__ import annotations

import html
import re
from pathlib import Path
from typing import Any

from monopoly_edition_generator.event_artwork import build_artwork_block
from monopoly_edition_generator.event_balance import load_event_balance_config
from monopoly_edition_generator.event_text import resolve_event_description
from monopoly_edition_generator.paths import (
    EVENT_CARD_TEMPLATE,
    GeneratorError,
    edition_output_dir,
    load_edition_json,
    read_text,
)

DESCRIPTION_LONG_THRESHOLD = 96


def safe_filename(name: str) -> str:
    name = name.strip()
    name = re.sub(r"[^\w\s-]", "", name, flags=re.UNICODE)
    name = re.sub(r"[\s_-]+", "_", name)
    return name.strip("_") or "Event"


def text_to_html(value: str) -> str:
    return html.escape(str(value), quote=True).replace("\n", "<br>\n")


def title_class(name: str) -> str:
    length = len(name.strip())
    if length <= 12:
        return "title-short"
    if length <= 18:
        return "title-medium"
    if length <= 24:
        return "title-long"
    return "title-extra-long"


def description_class(description: str) -> str:
    return " is-long" if len(description.strip()) > DESCRIPTION_LONG_THRESHOLD else ""


def populate_template(
    template: str,
    event: dict[str, Any],
    *,
    edition_id: str,
    html_output_path: Path | None = None,
) -> str:
    name = str(event["name"])
    subtitle = str(event["eventSubtitle"])
    description = str(event["eventDescription"])
    replacements = {
        "@@PAGE_TITLE@@": html.escape(name, quote=True),
        "@@EVENT_NAME@@": text_to_html(name),
        "@@EVENT_SUBTITLE@@": text_to_html(subtitle),
        "@@EVENT_DESCRIPTION@@": text_to_html(description),
        "@@TITLE_CLASS@@": title_class(name),
        "@@DESCRIPTION_CLASS@@": description_class(description),
        "@@ARTWORK_BLOCK@@": build_artwork_block(event, edition_id),
    }
    html_text = template
    for token, value in replacements.items():
        if token not in html_text:
            raise GeneratorError(f"Event template is missing placeholder {token}.")
        html_text = html_text.replace(token, value)
    leftover = re.findall(r"@@[A-Z_]+@@", html_text)
    if leftover:
        raise GeneratorError("Unreplaced event template placeholders: " + ", ".join(sorted(set(leftover))))
    return html_text


def generate_event_cards(
    edition_id: str,
    output_html_dir: Path | None = None,
    template_path: Path | None = None,
    events: list[dict[str, Any]] | None = None,
) -> list[Path]:
    banking = load_edition_json(edition_id, "banking_values.json")
    balance_lookup = load_event_balance_config(edition_id)

    if events is None:
        data = load_edition_json(edition_id, "events.json")
        events = data.get("events")
        if not isinstance(events, list):
            raise GeneratorError('Expected events.json to contain an "events" array.')

    template_path = template_path or EVENT_CARD_TEMPLATE
    template = read_text(template_path)

    if output_html_dir is None:
        output_html_dir = edition_output_dir(edition_id) / "event_cards" / "html"
    output_html_dir.mkdir(parents=True, exist_ok=True)

    generated: list[Path] = []
    for event in events:
        sequence = int(event["sequence"])
        name = str(event["name"])
        filename = f"E{sequence:02d}_{safe_filename(name)}.html"
        output_path = output_html_dir / filename
        resolved_event = {
            **event,
            "eventDescription": resolve_event_description(event, edition_id, banking, balance_lookup),
        }
        output_path.write_text(
            populate_template(
                template,
                resolved_event,
                edition_id=edition_id,
                html_output_path=output_path,
            ),
            encoding="utf-8",
        )
        generated.append(output_path)
        print(f"Created: {output_path}")

    print()
    print(f"Done. Generated {len(generated)} HTML file(s).")
    print(f"Output folder: {output_html_dir.resolve()}")
    return generated
