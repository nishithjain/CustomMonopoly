# Custom Card Generator

Generate property cards, event cards, and the Monopoly board from edition JSON.

Game values live in the main project data files. This folder only contains templates, themes, and generation code.

Authoritative edition data:

```text
monopoly-ultimate-banking-qr/data/editions/<edition>/
    edition.json
    properties.json
    events.json
    banking_values.json
    board_relationships.json
```

Run commands from `CustomCardGenerators/`.

## Launch GUI

```bash
python gui.py
```

or:

```bash
python generate.py --gui
```

The GUI uses the same generation functions as the command line. Select an edition, choose what to generate, then **Validate** or **Generate Selected**. Long work runs in a background thread so the window stays responsive.

**Generate PNGs** is a separate checkbox. With it unchecked, a run writes HTML only, so no `.png` file appears in the output folder. The status log lists every file written at the end of each run.

Requires:

```bash
pip install customtkinter pillow playwright
playwright install chromium
```

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

| Path | Purpose |
| --- | --- |
| `gui.py` | Desktop GUI |
| `generate.py` | Command-line entry point |
| `generator/` | Generation, validation, and PNG rendering code |
| `templates/` | Read-only HTML templates |
| `themes/monopoly_default.json` | Shared property/board colors |
| `assets/common/` | Shared outer-board artwork (GO, Jail, parking) |
| `output/<edition>/` | Generated HTML, PNG, and `generation_report.json` |

Generated files are always written under `output/<edition>/`. Templates are never overwritten. Regenerating an edition replaces that output folder's artifacts.

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

You can delete `output/india/` at any time and recreate it with `python generate.py india`.

## What users normally edit

Edit edition data, not Python:

- Property names, prices, rent, color groups → `properties.json`
- Event titles, subtitles, descriptions → `events.json`
- Currency, GO salary, location fee → `banking_values.json`
- Edition metadata → `edition.json`
- Color-set / neighbour relationships → `board_relationships.json`

Do **not** normally edit:

- `generator/*.py`
- `templates/*.html` (visual layout)
- files under `output/`

Theme colors in `themes/monopoly_default.json` are the current property-card and board-tile palettes. Change them only when you intend to change appearance for every edition using that theme.

## How to add another edition

1. Copy `monopoly-ultimate-banking-qr/data/editions/india/` (or `uk/`) to a new folder, for example `data/editions/france/`.
2. Set `editionId` in `edition.json` to match the folder name.
3. Update properties, events, and banking values for that location.
4. Keep 22 properties with sequences `1`–`22` (the physical board layout is fixed).
5. Run `python generate.py france`.

You should not need to change Python files for a new location that uses the same board shape and card sizes.

Edition-specific card photographs stay under `Resources/Editions/<edition>/`. The generator reads prices and text from JSON; it does not copy those photographs into `output/`.

## Compatibility scripts

These still work and call the new modules:

```bash
python generate_property_cards.py <properties.json> templates/property_card.html <output_dir> --banking <banking_values.json>
python generate_event_cards.py <events.json> <output_dir>
python update_board_template.py --properties ... --banking ... --template templates/board.html --output ...
python html_to_png.py --input output/india/board/Board_India.html --output output/india/board/Board_India_300DPI.png
```

`update_board_template.py` no longer writes over the template and no longer creates `.bak` files. `--output` is required.

## PNG rendering

Board and card PNGs use Playwright Chromium:

```bash
pip install playwright pillow
playwright install chromium
```

Board PNGs are 50 cm × 50 cm. Pixel size is calculated from physical size and DPI (`50 / 2.54 × DPI`). At 300 DPI that is 5906 × 5906. Property and event cards are 108 mm × 172 mm.

## Edition inner artwork

The outer board is fixed:

```text
Outer board:   50 cm × 50 cm
Inner artwork: 37 cm × 37 cm
```

These sizes are not editable in the GUI. They are defined in `generator/board_spec.py`.

Each edition may provide its own center image at:

```text
Resources/Editions/<edition>/Board/InnerBox.png
```

Examples:

```text
Resources/Editions/india/Board/InnerBox.png
Resources/Editions/uk/Board/InnerBox.png
```

**InnerBox.png is optional.** If it is missing, board generation still succeeds and the 37 cm × 37 cm center stays empty. The generator never copies another edition's image as a fallback.

You can also add or replace that file from the GUI with **Select / Replace Inner Artwork**.
