"""Fixed Monopoly board geometry. Do not expose these as editable settings."""

from __future__ import annotations

# Outer board including property, event, location, and corner spaces.
OUTER_BOARD_SIZE_CM = 50.0

# Center artwork region only. Never covers outer spaces or corners.
INNER_BOARD_SIZE_CM = 37.0

CM_PER_INCH = 2.54


def board_pixels_at_dpi(dpi: float, size_cm: float = OUTER_BOARD_SIZE_CM) -> int:
    """Print pixels for a square of the given physical size at DPI."""
    return round((size_cm / CM_PER_INCH) * dpi)
