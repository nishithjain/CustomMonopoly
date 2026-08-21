# Custom card generators

Authoritative India property dataset:

`monopoly-ultimate-banking-qr/data/editions/india/properties.json`

`properties_india.json` in this folder is a generator snapshot only. Do not treat it as a second runtime source of truth.

## Update the board HTML (`update_board_template.py`)

This script fills `Board_Template.html` with property names, prices, color groups, currency, GO salary, and location fees from edition JSON. It replaces the `const boardSpaces = [...]` and `const GO_DATA = {...}` blocks and leaves the rest of the template (layout, CSS, assets) unchanged.

Requires **Python 3.9+**. No extra packages.

### Files it reads

| Input | Fields used |
| --- | --- |
| `properties.json` | `name`, `sequence`, `colorGroup`, `purchasePrice` (exactly 22 properties, sequences 1–22) |
| `banking_values.json` | `currency.symbol`, `goSalary`, `locationFee` |
| `Board_Template.html` | existing `const boardSpaces` array to replace |

By default those filenames are looked up in the **current working directory**. Point at the edition data with flags.

### Run (India edition)

Run it **from this folder**, not from the repo root, otherwise Python reports `can't open file 'update_board_template.py'`:

```bash
cd CustomCardGenerators
python update_board_template.py \
  --properties ../monopoly-ultimate-banking-qr/data/editions/india/properties.json \
  --banking ../monopoly-ultimate-banking-qr/data/editions/india/banking_values.json \
  --template Board_Template.html
```

This **overwrites** `Board_Template.html` and writes a backup next to it: `Board_Template.html.bak`.

UK edition is the same paths with `uk` instead of `india`.

### Write a new file instead of overwriting

```bash
python update_board_template.py \
  --properties ../monopoly-ultimate-banking-qr/data/editions/india/properties.json \
  --banking ../monopoly-ultimate-banking-qr/data/editions/india/banking_values.json \
  --template Board_Template.html \
  --output Board_India.html
```

With `--output`, no `.bak` file is created; the template is left untouched.

### Flags

| Flag | Default | Meaning |
| --- | --- | --- |
| `--properties` | `properties.json` | Property dataset |
| `--banking` | `banking_values.json` | Currency and location fee |
| `--template` | `Board_Template.html` | HTML to read |
| `--output` | (in-place) | HTML to write |

### What gets written onto the board

Clockwise from GO (bottom-right), spaces follow:

`GO → P E P E P P L P → corner → P E P P P P L P → corner → P E P P P P L P → corner → P E P P E P L P → GO`

`P` = property (filled from JSON in sequence order 1–22), `E` = event, `L` = location (fee from banking JSON).

The GO corner amount comes from `goSalary` plus `currency.symbol`, so India renders ₹20000 rather than the UK default M200.

Supported `colorGroup` values: `BROWN`, `LIGHT_BLUE`, `PINK`, `ORANGE`, `RED`, `YELLOW`, `GREEN`, `DARK_BLUE`. Tile hex colors live in `COLOR_GROUP_STYLES` in the script.

Open the resulting HTML in a browser to preview the board.
