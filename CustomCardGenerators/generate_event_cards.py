from pathlib import Path
import json
import html
import re
import shutil
import textwrap
import zipfile

events_path = Path("/mnt/data/events(2).json")
reference_html_path = Path("/mnt/data/Boom_Town_Event_Card_Polished(1).html")

script_path = Path("/mnt/data/generate_event_cards.py")
output_dir = Path("/mnt/data/generated_event_cards")
zip_path = Path("/mnt/data/generated_event_cards.zip")

# Validate the uploaded source files can be read.
events_data = json.loads(events_path.read_text(encoding="utf-8"))
reference_html = reference_html_path.read_text(encoding="utf-8")

script = r'''#!/usr/bin/env python3
"""
Generate Event Card HTML files from events.json.

Physical card size:
    108 mm wide × 172 mm high
    (same portrait ratio as the supplied reference card;
     commonly described as 172 mm × 108 mm)

Input fields used from each event:
    - name
    - sequence
    - eventSubtitle
    - eventDescription

Usage:
    python generate_event_cards.py events.json

Optional output directory:
    python generate_event_cards.py events.json generated_event_cards
"""

from __future__ import annotations

import html
import json
import re
import sys
from pathlib import Path


CARD_TEMPLATE = """<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>{page_title}</title>

  <link rel="preconnect" href="https://fonts.googleapis.com">
  <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
  <link href="https://fonts.googleapis.com/css2?family=Montserrat:wght@700;800;900&family=Nunito:wght@400;500;600;700&display=swap" rel="stylesheet">

  <style>
    :root {{
      --card-width: 108mm;
      --card-height: 172mm;

      --paper: #fffdf8;
      --ink: #232629;

      --teal: #173f50;
      --teal-deep: #0f303d;
      --teal-soft: #255a6d;

      --gold-1: #fff0a4;
      --gold-2: #f5cf48;
      --gold-3: #d9a922;
      --gold-edge: #b88519;
    }}

    * {{
      margin: 0;
      padding: 0;
      box-sizing: border-box;
    }}

    html,
    body {{
      min-height: 100%;
    }}

    body {{
      min-height: 100vh;
      display: grid;
      place-items: center;
      padding: 14mm;
      font-family: "Nunito", "Trebuchet MS", Arial, sans-serif;
      color: var(--ink);
      background:
        radial-gradient(circle at 20% 15%, rgba(255,255,255,.95), transparent 33%),
        radial-gradient(circle at 82% 80%, rgba(255,255,255,.65), transparent 32%),
        linear-gradient(145deg, #e5e2da 0%, #d9d6ce 100%);
    }}

    .card-wrapper {{
      width: var(--card-width);
      height: var(--card-height);
      flex: 0 0 auto;
    }}

    .event-card {{
      width: var(--card-width);
      height: var(--card-height);
      position: relative;
      overflow: hidden;

      display: grid;
      grid-template-rows: auto auto 1fr auto auto;

      padding: 5mm 5mm 4mm;

      border: 0.25mm solid rgba(34, 45, 50, 0.10);
      border-radius: 7.5mm;

      background:
        radial-gradient(circle at 18% 10%, rgba(255,255,255,.95), transparent 30%),
        radial-gradient(circle at 90% 56%, rgba(234,225,208,.32), transparent 34%),
        linear-gradient(180deg, #fffefb 0%, var(--paper) 58%, #faf6ed 100%);

      box-shadow:
        0 4mm 10mm rgba(32, 36, 40, 0.18),
        inset 0 0.2mm 0 rgba(255,255,255,.95),
        inset 0 0 6mm rgba(180, 167, 143, .10);
    }}

    .event-card::before {{
      content: "";
      position: absolute;
      inset: 1.7mm;
      pointer-events: none;
      border-radius: 6mm;
      border: 0.2mm solid rgba(126, 113, 90, .10);
    }}

    .event-card::after {{
      content: "";
      position: absolute;
      inset: 0;
      pointer-events: none;
      opacity: .10;
      mix-blend-mode: multiply;
      background-image:
        repeating-linear-gradient(
          0deg,
          rgba(70,70,70,.04) 0,
          rgba(70,70,70,.04) 0.18mm,
          transparent 0.18mm,
          transparent 0.7mm
        );
    }}

    /* ---------- Header ---------- */

    .top-frame {{
      position: relative;
      z-index: 1;

      padding: 2.5mm 2.8mm 2.8mm;
      border-radius: 4mm 4mm 2.6mm 2.6mm;

      background:
        linear-gradient(180deg, var(--teal-soft), var(--teal) 58%, var(--teal-deep));

      box-shadow:
        inset 0 0.2mm 0 rgba(255,255,255,.18),
        inset 0 -0.8mm 1.5mm rgba(0,0,0,.13),
        0 1.4mm 3mm rgba(18, 55, 68, .14);
    }}

    .top-frame::before {{
      content: "";
      position: absolute;
      inset: 1.2mm;
      border-radius: 3mm 3mm 1.8mm 1.8mm;
      border: 0.2mm solid rgba(255,255,255,.12);
      pointer-events: none;
    }}

    .title-banner {{
      position: relative;
      overflow: hidden;
      min-height: 18.5mm;

      display: grid;
      place-items: center;
      text-align: center;

      padding: 2.5mm 3.5mm;

      border: 0.2mm solid rgba(123, 83, 6, .23);
      border-radius: 2.2mm;

      background:
        linear-gradient(
          102deg,
          rgba(255,255,255,.30) 0%,
          rgba(255,255,255,.06) 22%,
          rgba(255,255,255,.36) 50%,
          rgba(255,255,255,.08) 70%,
          rgba(255,255,255,.22) 100%
        ),
        linear-gradient(180deg, var(--gold-1) 0%, var(--gold-2) 52%, var(--gold-3) 100%);

      box-shadow:
        0 1.1mm 0 var(--gold-edge),
        0 1.8mm 3mm rgba(0,0,0,.22),
        inset 0 0.3mm 0 rgba(255,255,255,.75),
        inset 0 -0.3mm 0.8mm rgba(124, 84, 9, .18);
    }}

    .title-banner::before,
    .title-banner::after {{
      content: "";
      position: absolute;
      pointer-events: none;
    }}

    .title-banner::before {{
      width: 42%;
      height: 190%;
      top: -45%;
      left: 18%;
      transform: rotate(9deg);
      background: linear-gradient(90deg, transparent, rgba(255,255,255,.30), transparent);
      filter: blur(0.4mm);
    }}

    .title-banner::after {{
      inset: 1.2mm;
      border-radius: 1.3mm;
      border: 0.2mm solid rgba(255,255,255,.30);
    }}

    .event-title {{
      position: relative;
      z-index: 2;

      font-family: "Montserrat", "Arial Black", sans-serif;
      font-size: {title_font_size};
      font-weight: 900;
      line-height: 0.98;
      letter-spacing: 0.02em;
      text-transform: uppercase;
      color: #17191b;

      text-shadow:
        0 0.2mm 0 rgba(255,255,255,.55),
        0 0.3mm 0 rgba(117, 80, 6, .10);

      overflow-wrap: anywhere;
    }}

    /* ---------- Subtitle ---------- */

    .event-subtitle {{
      position: relative;
      z-index: 1;

      width: 100%;
      max-width: 92mm;
      margin: 4.7mm auto 3.5mm;
      padding: 0 3mm;

      text-align: center;
      font-size: {subtitle_font_size};
      font-weight: 500;
      line-height: 1.16;
      letter-spacing: -0.015em;
      color: #25292d;
    }}

    .event-subtitle::after {{
      content: "";
      display: block;
      width: 9mm;
      height: 0.65mm;
      margin: 2.7mm auto 0;
      border-radius: 999px;
      background: linear-gradient(90deg, transparent, rgba(23,63,80,.58), transparent);
    }}

    /* ---------- Blank event image area ---------- */

    .event-image {{
      position: relative;
      z-index: 1;

      width: 100%;
      min-height: 0;
      overflow: hidden;

      border-radius: 3mm;

      /*
        Intentionally blank.
        Add the event artwork later, for example:

        <img src="your-event-image.png" alt="">

        or set:
        background-image: url("your-event-image.png");
      */
      background: #f6f4ee;
      background-size: cover;
      background-position: center;
      background-repeat: no-repeat;

      box-shadow:
        0 1.2mm 3.2mm rgba(41, 48, 52, .07),
        inset 0 0 0 0.2mm rgba(67, 75, 78, .06),
        inset 0 0.2mm 0 rgba(255,255,255,.8);
    }}

    .event-image > img {{
      width: 100%;
      height: 100%;
      display: block;
      object-fit: cover;
    }}

    /* ---------- Description ---------- */

    .event-description {{
      position: relative;
      z-index: 1;

      margin: 3.2mm 1.7mm 0;
      padding: 3.2mm 4mm 3mm;

      text-align: center;
      font-size: {description_font_size};
      font-weight: 500;
      line-height: 1.34;
      letter-spacing: -0.01em;
      color: #262a2d;

      border-radius: 3.3mm;
      border: 0.2mm solid rgba(165, 148, 112, .18);

      background:
        linear-gradient(180deg, rgba(255,255,255,.73), rgba(248,243,232,.72));

      box-shadow:
        inset 0 0.2mm 0 rgba(255,255,255,.86),
        0 1mm 2.5mm rgba(80, 70, 50, .05);
    }}

    /* ---------- Bottom identifier ---------- */

    .bottom-placeholder {{
      position: relative;
      z-index: 1;

      width: 20mm;
      height: 2.7mm;
      margin: 2.5mm auto 0;
      border-radius: 999px;

      background: linear-gradient(180deg, #ffffff, #ece7dc);
      border: 0.2mm solid rgba(145, 130, 102, .18);

      box-shadow:
        inset 0 0.2mm 0.3mm rgba(255,255,255,.85),
        0 0.3mm 0.8mm rgba(75, 66, 48, .06);
    }}

    /* ---------- Print at exact physical size ---------- */

    @page {{
      size: 108mm 172mm;
      margin: 0;
    }}

    @media print {{
      html,
      body {{
        width: 108mm;
        height: 172mm;
      }}

      body {{
        display: block;
        padding: 0;
        margin: 0;
        background: #fff;
      }}

      .card-wrapper,
      .event-card {{
        width: 108mm;
        height: 172mm;
      }}

      .event-card {{
        box-shadow: none;
      }}
    }}
  </style>
</head>

<body>
  <div class="card-wrapper">
    <article class="event-card">

      <header class="top-frame">
        <div class="title-banner">
          <div class="event-title">{event_name}</div>
        </div>
      </header>

      <div class="event-subtitle">{event_subtitle}</div>

      <div class="event-image"></div>

      <div class="event-description">{event_description}</div>

      <div class="bottom-placeholder"></div>

    </article>
  </div>
</body>
</html>
"""


def safe_filename(name: str) -> str:
    """Convert an event name into a Windows-friendly filename."""
    name = name.strip()
    name = re.sub(r"[^\w\s-]", "", name, flags=re.UNICODE)
    name = re.sub(r"[\s_-]+", "_", name)
    return name.strip("_") or "Event"


def text_to_html(value: str) -> str:
    """
    Escape JSON text for HTML.
    Existing line breaks are preserved.
    """
    return html.escape(str(value), quote=True).replace("\n", "<br>\n")


def title_font_size(name: str) -> str:
    """Reduce title size slightly for longer event names."""
    length = len(name.strip())
    if length <= 12:
        return "9.2mm"
    if length <= 18:
        return "7.8mm"
    if length <= 24:
        return "6.7mm"
    return "5.8mm"


def subtitle_font_size(text: str) -> str:
    """Keep long subtitles inside the available area."""
    length = len(text.strip())
    if length <= 38:
        return "5.7mm"
    if length <= 58:
        return "5.0mm"
    if length <= 82:
        return "4.4mm"
    return "3.9mm"


def description_font_size(text: str) -> str:
    """Keep long descriptions readable without overflowing."""
    length = len(text.strip())
    if length <= 85:
        return "4.1mm"
    if length <= 125:
        return "3.7mm"
    if length <= 165:
        return "3.35mm"
    return "3.0mm"


def build_card_html(event: dict) -> str:
    """Create one complete HTML document from the four requested fields."""
    name = str(event["name"])
    subtitle = str(event["eventSubtitle"])
    description = str(event["eventDescription"])

    return CARD_TEMPLATE.format(
        page_title=html.escape(name, quote=True),
        event_name=text_to_html(name),
        event_subtitle=text_to_html(subtitle),
        event_description=text_to_html(description),
        title_font_size=title_font_size(name),
        subtitle_font_size=subtitle_font_size(subtitle),
        description_font_size=description_font_size(description),
    )


def generate_cards(json_path: Path, output_dir: Path) -> int:
    """Read events.json and create one HTML file per event."""
    with json_path.open("r", encoding="utf-8") as file:
        data = json.load(file)

    events = data.get("events")
    if not isinstance(events, list):
        raise ValueError('Expected the JSON file to contain an "events" array.')

    output_dir.mkdir(parents=True, exist_ok=True)

    required_fields = (
        "name",
        "sequence",
        "eventSubtitle",
        "eventDescription",
    )

    generated = 0

    for event in events:
        missing = [field for field in required_fields if field not in event]
        if missing:
            raise ValueError(
                f"Event is missing required field(s): {', '.join(missing)}"
            )

        sequence = int(event["sequence"])
        name = str(event["name"])

        filename = f"E{sequence:02d}_{safe_filename(name)}.html"
        output_path = output_dir / filename

        output_path.write_text(
            build_card_html(event),
            encoding="utf-8",
        )

        print(f"Created: {output_path}")
        generated += 1

    return generated


def main() -> None:
    if len(sys.argv) < 2:
        json_path = Path("events.json")
    else:
        json_path = Path(sys.argv[1])

    if len(sys.argv) >= 3:
        output_dir = Path(sys.argv[2])
    else:
        output_dir = Path("generated_event_cards")

    if not json_path.exists():
        raise FileNotFoundError(
            f"JSON file not found: {json_path.resolve()}"
        )

    count = generate_cards(json_path, output_dir)

    print()
    print(f"Done. Generated {count} HTML file(s).")
    print(f"Output folder: {output_dir.resolve()}")


if __name__ == "__main__":
    main()
'''

script_path.write_text(script, encoding="utf-8")

# Run the generated script logic here to validate it and create the user's 23 HTML outputs.
namespace = {}
exec(compile(script, str(script_path), "exec"), namespace)

# Avoid main() from exec because __name__ in namespace may differ.
count = namespace["generate_cards"](events_path, output_dir)

# Zip generated HTML files.
if zip_path.exists():
    zip_path.unlink()

with zipfile.ZipFile(zip_path, "w", zipfile.ZIP_DEFLATED) as zf:
    for file_path in sorted(output_dir.glob("*.html")):
        zf.write(file_path, arcname=file_path.name)

print(f"\nValidated generator against uploaded JSON.")
print(f"Generated HTML files: {count}")
print(f"Script: {script_path}")
print(f"ZIP: {zip_path}")
