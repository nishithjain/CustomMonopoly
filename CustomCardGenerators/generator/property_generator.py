"""Generate property-card HTML from edition properties.json."""

from __future__ import annotations

import html
import json
import re
from pathlib import Path
from typing import Any

from generator.utils import (
    DEFAULT_THEME_ID,
    TEMPLATES_DIR,
    GeneratorError,
    edition_output_dir,
    load_edition_config,
    load_edition_json,
    load_theme,
    normalize_name,
    property_card_base_colors,
    read_text,
    safe_filename_component,
    theme_id_for_edition,
)


REQUIRED_FIELDS = (
    "name",
    "sequence",
    "colorGroup",
    "purchasePrice",
    "rentLevels",
)


def split_name(name: str, single_line_threshold: int = 12) -> list[str]:
    raw_name = str(name).strip()

    if "\n" in raw_name:
        explicit = [normalize_name(part) for part in raw_name.splitlines() if part.strip()]
        if not explicit:
            raise GeneratorError("Property name cannot be empty.")
        if len(explicit) > 2:
            raise GeneratorError(
                f"Property name has {len(explicit)} explicit lines; maximum supported is 2."
            )
        return explicit

    display_name = normalize_name(raw_name)
    words = display_name.split()

    if len(display_name) <= single_line_threshold or len(words) <= 1:
        return [display_name]

    candidates: list[tuple[tuple[int, int, int], list[str]]] = []
    for split_at in range(1, len(words)):
        first = " ".join(words[:split_at])
        second = " ".join(words[split_at:])
        score = (
            abs(len(first) - len(second)),
            max(len(first), len(second)),
            0 if len(first) >= len(second) else 1,
        )
        candidates.append((score, [first, second]))

    return min(candidates, key=lambda item: item[0])[1]


def hex_to_rgb(value: str) -> tuple[int, int, int]:
    value = value.lstrip("#")
    if len(value) != 6:
        raise GeneratorError(f"Expected 6-digit hex color, got: {value!r}")
    return tuple(int(value[i : i + 2], 16) for i in (0, 2, 4))


def rgb_to_hex(rgb: tuple[int, int, int]) -> str:
    return "#{:02X}{:02X}{:02X}".format(*rgb)


def blend(color: str, target: str, target_ratio: float) -> str:
    c = hex_to_rgb(color)
    t = hex_to_rgb(target)
    ratio = max(0.0, min(1.0, target_ratio))
    result = tuple(
        round(source * (1.0 - ratio) + destination * ratio)
        for source, destination in zip(c, t)
    )
    return rgb_to_hex(result)


def palette_for_color_group(color_group: str, base_colors: dict[str, str]) -> dict[str, Any]:
    group = str(color_group).strip().upper()
    if group not in base_colors:
        supported = ", ".join(sorted(base_colors))
        raise GeneratorError(
            f"Unsupported colorGroup {color_group!r}. Supported values: {supported}"
        )

    base = base_colors[group]
    property_dark = blend(base, "#000000", 0.20)
    property_light = blend(base, "#FFFFFF", 0.10)
    lightening_pairs = [
        (0.90, 0.84),
        (0.80, 0.72),
        (0.68, 0.58),
        (0.54, 0.42),
        (0.38, 0.26),
    ]
    rent_rows = [
        (blend(base, "#FFFFFF", a), blend(base, "#FFFFFF", b))
        for a, b in lightening_pairs
    ]
    return {
        "property_dark": property_dark,
        "property_light": property_light,
        "rent_rows": rent_rows,
    }


def replace_css_variable(template: str, variable: str, value: str) -> str:
    pattern = rf"(--{re.escape(variable)}\s*:\s*)[^;]+;"
    replacement = rf"\g<1>{value};"
    result, count = re.subn(pattern, replacement, template, count=1)
    if count != 1:
        raise GeneratorError(f"Could not find CSS variable --{variable} in template.")
    return result


def apply_palette(template: str, color_group: str, base_colors: dict[str, str]) -> str:
    palette = palette_for_color_group(color_group, base_colors)
    result = replace_css_variable(template, "property-dark", palette["property_dark"])
    result = replace_css_variable(result, "property-light", palette["property_light"])
    for row_number, (start, end) in enumerate(palette["rent_rows"], start=1):
        result = replace_css_variable(
            result,
            f"rent-row-{row_number}",
            f"{start}, {end}",
        )
    return result


def make_card_data(
    prop: dict[str, Any],
    currency: str,
    single_line_threshold: int,
) -> dict[str, Any]:
    name_lines = split_name(prop["name"], single_line_threshold)
    return {
        "currency": currency,
        "labels": {
            "purchasePrice": "Purchase Price",
            "rentLevel": "RENT LEVEL",
            "rent": "RENT",
        },
        "property": {
            "number": int(prop["sequence"]),
            "name": name_lines,
            "purchasePrice": prop["purchasePrice"],
        },
        "rentLevels": [
            {
                "level": int(item["level"]),
                "rent": item["amount"],
            }
            for item in prop["rentLevels"]
        ],
    }


def replace_card_data(template: str, card_data: dict[str, Any]) -> str:
    pretty_json = json.dumps(card_data, ensure_ascii=False, indent=4)
    lines = pretty_json.splitlines()
    indented_json = "\n".join(("  " + line) if line else line for line in lines)
    replacement = "const CARD_DATA = " + indented_json.lstrip() + ";"
    pattern = re.compile(r"const\s+CARD_DATA\s*=\s*\{.*?\n\s*\};", flags=re.DOTALL)
    result, count = pattern.subn(replacement, template, count=1)
    if count != 1:
        raise GeneratorError("Could not locate the template's 'const CARD_DATA = {...};' block.")
    return result


def replace_document_title(template: str, property_name: str) -> str:
    safe_name = html.escape(normalize_name(property_name))
    title = f"<title>{safe_name} · Monopoly Property Card</title>"
    result, count = re.subn(
        r"<title>.*?</title>",
        title,
        template,
        count=1,
        flags=re.DOTALL | re.IGNORECASE,
    )
    if count != 1:
        raise GeneratorError("Could not locate <title> in HTML template.")
    return result


def output_filename(prop: dict[str, Any]) -> str:
    sequence = int(prop["sequence"])
    name = safe_filename_component(prop["name"])
    return f"{sequence:02d}_{name}.html"


def generate_card(
    template: str,
    prop: dict[str, Any],
    output_dir: Path,
    currency: str,
    base_colors: dict[str, str],
    single_line_threshold: int,
) -> Path:
    card_data = make_card_data(prop, currency, single_line_threshold)
    html_text = apply_palette(template, prop["colorGroup"], base_colors)
    html_text = replace_card_data(html_text, card_data)
    html_text = replace_document_title(html_text, prop["name"])
    output_path = output_dir / output_filename(prop)
    output_path.write_text(html_text, encoding="utf-8")
    return output_path


def generate_property_cards(
    edition_id: str,
    currency: str,
    output_html_dir: Path | None = None,
    template_path: Path | None = None,
    properties: list[dict[str, Any]] | None = None,
    single_line_threshold: int = 12,
) -> list[Path]:
    if properties is None:
        data = load_edition_json(edition_id, "properties.json")
        properties = data["properties"]

    template_path = template_path or (TEMPLATES_DIR / "property_card.html")
    template = read_text(template_path)
    try:
        edition_config = load_edition_config(edition_id)
        theme = load_theme(theme_id_for_edition(edition_config))
    except GeneratorError:
        theme = load_theme(DEFAULT_THEME_ID)
    base_colors = property_card_base_colors(theme)

    if output_html_dir is None:
        output_html_dir = edition_output_dir(edition_id) / "property_cards" / "html"
    output_html_dir.mkdir(parents=True, exist_ok=True)

    generated: list[Path] = []
    for prop in properties:
        output_path = generate_card(
            template=template,
            prop=prop,
            output_dir=output_html_dir,
            currency=currency,
            base_colors=base_colors,
            single_line_threshold=single_line_threshold,
        )
        generated.append(output_path)
        lines = split_name(prop["name"], single_line_threshold)
        print(
            f"[OK] {int(prop['sequence']):02d}  "
            f"{prop['name']}  ->  {output_path.name}  "
            f"| color={prop['colorGroup']} "
            f"| title={' / '.join(lines)}"
        )

    print(f"\nGenerated {len(generated)} HTML file(s) in: {output_html_dir.resolve()}")
    return generated
