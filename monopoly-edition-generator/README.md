# Monopoly Edition Generator

Generate property cards, event cards, and the Monopoly board from edition JSON.

Game values live in the main project data files. This package contains templates, themes, and generation code only.

Authoritative edition data:

```text
monopoly-ultimate-banking-qr/data/editions/<edition>/
    edition.json
    properties.json
    events.json
    banking_values.json
    board_relationships.json
```

Run commands from `monopoly-edition-generator/`.

## Launch GUI

```bash
python generate.py --gui
```

The GUI uses the same generation functions as the command line. Select an edition, choose what to generate, then **Validate** or **Generate Selected**. Long work runs in a background thread so the window stays responsive.

**Generate PNGs** is a separate checkbox. With it unchecked, a run writes HTML only. The status log lists every file written at the end of each run.

## Generate Everything

```bash
python generate.py india
```

This validates the edition, writes HTML, then renders PNGs (Playwright + Chromium).

## Generate Properties Only

```bash
python generate.py india --only properties
```

## Generate Events Only

```bash
python generate.py india --only events
```

Event card text is read from `monopoly-ultimate-banking-qr/data/editions/<edition>/events.json`.

Monetary amounts in event descriptions use named placeholders such as `{amount}` and `{goSalary}`. During generation, the event-card generator resolves those placeholders from:

* `{edition}_event_balance_config.json` (event-specific amounts, matched by `eventId`)
* `banking_values.json` (GO salary, Jail release fee, currency formatting)

Editions without a balance configuration file (for example UK) pass event text through unchanged when no placeholders are present.

### Replace stale India event HTML (23 → 25 cards)

If `output/india/event_cards/html/` still contains old UK-deck files (for example `E01_Boom_Town.html`), regenerate events. **You do not need to delete them manually** — the generator removes the entire `event_cards/html/` and `event_cards/png/` folders for that edition before writing new files.

From `monopoly-edition-generator/` (your usual working folder):

```bash
# 1. Rebuild events.json (note: ../ not monopoly-ultimate-banking-qr/ alone)
python ../monopoly-ultimate-banking-qr/tools/build_india_events.py

# 2. Regenerate event-card HTML only
python generate.py india --only events --no-png
```

Or change directory first:

```bash
cd ../monopoly-ultimate-banking-qr
python tools/build_india_events.py
cd ../monopoly-edition-generator
python generate.py india --only events --no-png
```

From the Monopoly repo root (`C:\Personal\Monopoly`):

```bash
python monopoly-ultimate-banking-qr/tools/build_india_events.py
cd monopoly-edition-generator && python generate.py india --only events --no-png
```

If `events.json` is already up to date, skip step 1 and run only:

```bash
python generate.py india --only events --no-png
```

Expected result:

```text
output/india/event_cards/html/
    E01_Advance_to_GO.html
    ...
    E25_Green_Energy_Rebate.html
```

To render printable PNGs as well, omit `--no-png` (requires Playwright + Chromium):

```bash
python generate.py india --only events
```

Optional manual cleanup (only needed if you want to clear output without regenerating):

```bash
rm -rf output/india/event_cards/html output/india/event_cards/png
```

UK edition uses the same commands with `uk` instead of `india` (23 events).

## Generate Board Only

```bash
python generate.py india --only board
```

## Validate Edition

```bash
python generate.py india --validate
```

HTML-only (skip PNG rendering):

```bash
python generate.py india --no-png
```

UK edition uses the same commands with `uk` instead of `india`.

## Folder layout

```text
monopoly-edition-generator/
├── README.md
├── pyproject.toml
├── requirements.txt
├── generate.py                 # thin launcher → monopoly_edition_generator.cli
├── src/monopoly_edition_generator/
│   ├── cli.py
│   ├── gui.py
│   ├── pipeline.py
│   ├── validator.py
│   ├── renderer.py
│   ├── paths.py
│   └── generators/
│       ├── board.py
│       ├── event_cards.py
│       └── property_cards.py
├── templates/
│   ├── board/board.html
│   └── cards/
│       ├── event-card.html
│       └── property-card.html
├── assets/board-spaces/
│   ├── go.png
│   ├── jail.png
│   ├── go-to-jail.png
│   ├── free-parking.png
│   ├── event-space.png
│   └── location-space.png
├── themes/monopoly_default.json
├── scripts/legacy/             # compatibility wrappers
├── tests/
└── output/<edition>/           # generated; gitignored
```

Generated files are always written under `output/<edition>/`. Templates are never overwritten. Regenerating a selected artifact type clears only that type's stale HTML/PNG for the current edition.

Example India output:

```text
output/india/
    board/Board_India.html
    board/Board_India_300DPI.png
    property_cards/html/
    property_cards/png/
    event_cards/html/
    event_cards/png/
    generation_report.json
```

## What users normally edit

Edit edition data, not Python:

- Property names, prices, rent, color groups → `properties.json`
- Event titles, subtitles, descriptions → `events.json`
- Currency, GO salary, location fee → `banking_values.json`
- Edition metadata → `edition.json`
- Color-set / neighbour relationships → `board_relationships.json`

Theme colors in `themes/monopoly_default.json` are the current property-card and board-tile palettes.

## Edition inner artwork

Each edition may provide its own center image at:

```text
Resources/Editions/<edition>/Board/InnerBox.png
```

**InnerBox.png is optional.** If it is missing, board generation still succeeds and the 37 cm × 37 cm center stays empty.

## Dependencies

```bash
pip install -r requirements.txt
playwright install chromium
```

Pinned versions are listed in `requirements.txt` and `pyproject.toml`. Playwright requires a one-time Chromium install for PNG rendering.

For development and tests:

```bash
pip install -e ".[dev]"
pytest
```

## Legacy scripts

Compatibility wrappers live under `scripts/legacy/`:

```bash
python scripts/legacy/generate_property_cards.py <properties.json> templates/cards/property-card.html <output_dir> --banking <banking_values.json>
python scripts/legacy/generate_event_cards.py <events.json> <output_dir>
python scripts/legacy/update_board_template.py --properties ... --banking ... --template templates/board/board.html --output ...
python scripts/legacy/html_to_png.py --input output/india/board/Board_India.html --output output/india/board/Board_India_300DPI.png
```

Prefer `python generate.py <edition>` for new work.

## Energy Grid board-space tiles

Authoritative Energy Grid data lives at `../EnergyGrid_Board/energy_grids.json`. India board spaces reference grid IDs only; prices and rent are injected at generation time from that file.

Render portrait board-space PNGs (4.625 cm × 6.5 cm, 300 DPI) before generating the India board:

```bash
python scripts/render_energy_grid_board_spaces.py
```

Output:

```text
assets/board-spaces/energy-grids/
    eng_01_solar.png
    eng_02_wind.png
    eng_03_hydroelectric.png
    eng_04_biomass.png
```

## PNG rendering

Board PNGs are 50 cm × 50 cm. At 300 DPI that is 5906 × 5906. Property and event cards are 108 mm × 172 mm.
