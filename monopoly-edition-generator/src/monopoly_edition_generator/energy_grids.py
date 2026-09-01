"""Load authoritative Energy Grid data from EnergyGrid_Board/energy_grids.json."""

from __future__ import annotations

from typing import Any

from monopoly_edition_generator.paths import (
    ENERGY_GRID_ASSET_FILES,
    ENERGY_GRIDS_JSON,
    GeneratorError,
    load_json,
)

REQUIRED_GRID_IDS = ("ENG_01", "ENG_02", "ENG_03", "ENG_04")


def load_energy_grids() -> dict[str, Any]:
    data = load_json(ENERGY_GRIDS_JSON)
    if not isinstance(data, dict):
        raise GeneratorError(f"{ENERGY_GRIDS_JSON} must be a JSON object.")
    _validate_energy_grids(data)
    return data


def _validate_energy_grids(data: dict[str, Any]) -> None:
    for key in ("currency", "purchasePrice", "rent", "energyGrids"):
        if key not in data:
            raise GeneratorError(f"energy_grids.json is missing required field {key!r}.")

    grids = data.get("energyGrids")
    if not isinstance(grids, list) or len(grids) != 4:
        raise GeneratorError("energy_grids.json must contain exactly 4 energyGrids entries.")

    seen: set[str] = set()
    for grid in grids:
        if not isinstance(grid, dict):
            raise GeneratorError("Each energy grid entry must be an object.")
        grid_id = str(grid.get("energyGridId") or "").strip()
        name = str(grid.get("energyName") or "").strip()
        if not grid_id or not name:
            raise GeneratorError("Each energy grid requires energyGridId and energyName.")
        if grid_id in seen:
            raise GeneratorError(f"Duplicate energyGridId in energy_grids.json: {grid_id}")
        seen.add(grid_id)

    missing = [grid_id for grid_id in REQUIRED_GRID_IDS if grid_id not in seen]
    if missing:
        raise GeneratorError(f"energy_grids.json is missing grid IDs: {', '.join(missing)}")


def energy_grid_lookup(data: dict[str, Any]) -> dict[str, dict[str, Any]]:
    return {
        str(grid["energyGridId"]): grid
        for grid in data["energyGrids"]
        if isinstance(grid, dict) and grid.get("energyGridId")
    }


def asset_filename_for_grid(grid_id: str) -> str:
    try:
        return ENERGY_GRID_ASSET_FILES[grid_id]
    except KeyError as exc:
        raise GeneratorError(f"No board asset mapping for energy grid {grid_id!r}.") from exc
