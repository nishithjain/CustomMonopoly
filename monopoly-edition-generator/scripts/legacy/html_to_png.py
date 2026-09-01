#!/usr/bin/env python3
"""Compatibility wrapper. Prefer: python generate.py <edition> --only board"""

from __future__ import annotations

import argparse
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SRC = ROOT / "src"
if str(SRC) not in sys.path:
    sys.path.insert(0, str(SRC))

from monopoly_edition_generator.paths import BOARD_TEMPLATE, configure_stdio
from monopoly_edition_generator.renderer import render_board_png


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Capture #monopoly-board from a local HTML file as a print-ready PNG."
    )
    parser.add_argument(
        "--input",
        type=Path,
        default=ROOT / "templates" / "board" / "board.html",
        help="Input HTML file",
    )
    parser.add_argument(
        "--output",
        type=Path,
        default=ROOT / "output" / "Monopoly_Board_300DPI.png",
        help="Output PNG path",
    )
    parser.add_argument(
        "--dpi",
        type=float,
        default=300,
        help="Print DPI (default: 300)",
    )
    return parser.parse_args()


def main() -> None:
    configure_stdio()
    args = parse_args()
    if args.dpi <= 0:
        print("ERROR: --dpi must be greater than 0", file=sys.stderr)
        raise SystemExit(1)
    if not args.input.is_file():
        print(f"ERROR: Input HTML file not found: {args.input.resolve()}", file=sys.stderr)
        raise SystemExit(1)
    render_board_png(args.input, args.output, dpi=args.dpi)


if __name__ == "__main__":
    main()
