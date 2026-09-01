#!/usr/bin/env python3
"""Thin launcher for monopoly_edition_generator.cli."""

from __future__ import annotations

import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent
SRC = ROOT / "src"
if str(SRC) not in sys.path:
    sys.path.insert(0, str(SRC))

from monopoly_edition_generator.cli import main

if __name__ == "__main__":
    raise SystemExit(main())
