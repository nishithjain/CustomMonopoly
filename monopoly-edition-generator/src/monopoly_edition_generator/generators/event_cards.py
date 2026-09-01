"""Generate event-card HTML from edition events.json."""

from __future__ import annotations

import html
import re
from pathlib import Path
from typing import Any

from monopoly_edition_generator.paths import (
    EVENT_CARD_TEMPLATE,
    GeneratorError,
    edition_output_dir,
    load_edition_json,
    read_text,
)


def safe_filename(name: str) -> str:
    name = name.strip()
    name = re.sub(r"[^\w\s-]", "", name, flags=re.UNICODE)
    name = re.sub(r"[\s_-]+", "_", name)
    return name.strip("_") or "Event"


def text_to_html(value: str) -> str:
    return html.escape(str(value), quote=True).replace("\n", "<br>\n")


def title_font_size(name: str) -> str:
    length = len(name.strip())
    if length <= 12:
        return "9.2mm"
    if length <= 18:
        return "7.8mm"
    if length <= 24:
        return "6.7mm"
    return "5.8mm"


def subtitle_font_size(text: str) -> str:
    length = len(text.strip())
    if length <= 38:
        return "5.7mm"
    if length <= 58:
        return "5.0mm"
    if length <= 82:
        return "4.4mm"
    return "3.9mm"


def description_font_size(text: str) -> str:
    length = len(text.strip())
    if length <= 85:
        return "4.1mm"
    if length <= 125:
        return "3.7mm"
    if length <= 165:
        return "3.35mm"
    return "3.0mm"


def populate_template(template: str, event: dict[str, Any]) -> str:
    name = str(event["name"])
    subtitle = str(event["eventSubtitle"])
    description = str(event["eventDescription"])
    replacements = {
        "@@PAGE_TITLE@@": html.escape(name, quote=True),
        "@@EVENT_NAME@@": text_to_html(name),
        "@@EVENT_SUBTITLE@@": text_to_html(subtitle),
        "@@EVENT_DESCRIPTION@@": text_to_html(description),
        "@@TITLE_FONT_SIZE@@": title_font_size(name),
        "@@SUBTITLE_FONT_SIZE@@": subtitle_font_size(subtitle),
        "@@DESCRIPTION_FONT_SIZE@@": description_font_size(description),
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
        output_path.write_text(populate_template(template, event), encoding="utf-8")
        generated.append(output_path)
        print(f"Created: {output_path}")

    print()
    print(f"Done. Generated {len(generated)} HTML file(s).")
    print(f"Output folder: {output_html_dir.resolve()}")
    return generated
