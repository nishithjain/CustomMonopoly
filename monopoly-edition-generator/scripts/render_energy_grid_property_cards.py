#!/usr/bin/env python3
"""Generate and render India Energy Grid property cards (108 mm × 172 mm, 300 DPI)."""

from __future__ import annotations

import argparse
import importlib.util
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT / "src"
if str(SRC) not in sys.path:
    sys.path.insert(0, str(SRC))

from monopoly_edition_generator.paths import (  # noqa: E402
    ENERGY_GRID_CARD_ASSETS_DIR,
    ENERGY_GRID_CARD_DIR,
    ENERGY_GRID_CARD_GENERATED_HTML_DIR,
    ENERGY_GRID_CARD_HTML_SOURCES,
    ENERGY_GRID_CARD_PNG_FILES,
    INDIA_ENERGY_GRIDS_DATA_JSON,
    configure_stdio,
)
from monopoly_edition_generator.renderer import (  # noqa: E402
    CARD_HEIGHT_MM,
    CARD_WIDTH_MM,
    render_card_pngs,
)


def _generate_html() -> None:
    """Run EnergyGrid_Card/generate_energy_grid_cards.py to refresh HTML sources."""
    script_path = ENERGY_GRID_CARD_DIR / "generate_energy_grid_cards.py"
    if not script_path.is_file():
        raise FileNotFoundError(f"Missing card generator: {script_path}")

    spec = importlib.util.spec_from_file_location("generate_energy_grid_cards", script_path)
    if spec is None or spec.loader is None:
        raise RuntimeError(f"Could not load generator module from {script_path}")
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    module.main()


def _resolve_html_path(grid_id: str, html_name: str) -> Path:
    generated = ENERGY_GRID_CARD_GENERATED_HTML_DIR / html_name
    if generated.is_file():
        return generated

    direct = ENERGY_GRID_CARD_DIR / html_name
    if direct.is_file():
        return direct

    raise FileNotFoundError(
        f"Missing Energy Grid property card HTML for {grid_id}. "
        f"Expected {generated} or {direct}. Run without --skip-html-gen first."
    )


def _validate_html_artwork(html_path: Path) -> None:
    """Ensure artwork referenced by generated HTML exists relative to the HTML file."""
    import re

    text = html_path.read_text(encoding="utf-8")
    text = re.sub(r"<!--.*?-->", "", text, flags=re.DOTALL)

    for match in re.finditer(r'<img[^>]+src="([^"]+)"', text):
        src = match.group(1)
        if src.startswith(("http://", "https://", "data:")):
            continue
        artwork_path = (html_path.parent / src).resolve()
        if not artwork_path.is_file():
            raise FileNotFoundError(
                f"{html_path.name} references missing artwork {src!r} "
                f"(resolved to {artwork_path}). Regenerate HTML with "
                f"EnergyGrid_Card/generate_energy_grid_cards.py."
            )


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Generate HTML and render Energy Grid property card PNGs.",
    )
    parser.add_argument(
        "--output-dir",
        type=Path,
        default=ENERGY_GRID_CARD_ASSETS_DIR,
        help="Directory for rendered PNG files (default: assets/cards/editions/india/energy-grid)",
    )
    parser.add_argument(
        "--skip-html-gen",
        action="store_true",
        help="Skip running EnergyGrid_Card/generate_energy_grid_cards.py",
    )
    parser.add_argument(
        "--dpi",
        type=float,
        default=300,
        help="Print DPI (default: 300)",
    )
    return parser.parse_args()


def main() -> int:
    configure_stdio()
    args = parse_args()

    if not args.skip_html_gen:
        print("Generating Energy Grid property card HTML...")
        _generate_html()

    args.output_dir.mkdir(parents=True, exist_ok=True)

    jobs: list[tuple[Path, Path]] = []
    for grid_id, html_name in ENERGY_GRID_CARD_HTML_SOURCES.items():
        html_path = _resolve_html_path(grid_id, html_name)
        _validate_html_artwork(html_path)
        output_path = args.output_dir / ENERGY_GRID_CARD_PNG_FILES[grid_id]
        jobs.append((html_path, output_path))

    created = render_card_pngs(
        jobs,
        ".energy-card",
        width_mm=CARD_WIDTH_MM,
        height_mm=CARD_HEIGHT_MM,
        dpi=args.dpi,
    )

    for path in created:
        print(f"Created: {path}")

    print()
    print(
        f"Rendered {len(created)} Energy Grid property card PNG(s) at "
        f"{CARD_WIDTH_MM}mm × {CARD_HEIGHT_MM}mm, {args.dpi:.0f} DPI."
    )
    print(f"QR metadata source: {INDIA_ENERGY_GRIDS_DATA_JSON}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
