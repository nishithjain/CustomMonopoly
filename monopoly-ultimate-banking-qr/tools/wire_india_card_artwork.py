#!/usr/bin/env python3
"""Backward-compatible wrapper for publishing India card fronts."""

from __future__ import annotations

import sys
from pathlib import Path

from publish_edition_card_fronts import main as publish_main


def main() -> int:
    tools_dir = Path(__file__).resolve().parent
    return publish_main(
        [
            "--edition",
            "india",
            "--card-type",
            "all",
            *sys.argv[1:],
        ],
    )


if __name__ == "__main__":
    sys.exit(main())
