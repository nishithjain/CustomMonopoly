"""Shared path, JSON, theme, and formatting helpers."""

from __future__ import annotations

import json
import os
import re
import shutil
from pathlib import Path
from typing import Any

PACKAGE_ROOT = Path(__file__).resolve().parent
PROJECT_ROOT = PACKAGE_ROOT.parent.parent

EVENT_CARD_ARTWORK_ROOT = PROJECT_ROOT / "assets" / "cards" / "editions"

WORKSPACE_ENV_VAR = "MONOPOLY_WORKSPACE_ROOT"


class GeneratorError(Exception):
    """User-facing configuration or generation error."""


def discover_workspace_root(start: Path | None = None) -> Path:
    """Locate the Monopoly workspace root by env override or parent search."""
    env = os.environ.get(WORKSPACE_ENV_VAR, "").strip()
    if env:
        root = Path(env).resolve()
        if not root.is_dir():
            raise GeneratorError(f"{WORKSPACE_ENV_VAR} is not a directory: {root}")
        return root

    current = (start or PROJECT_ROOT).resolve()
    for candidate in [current, *current.parents]:
        data = candidate / "monopoly-ultimate-banking-qr" / "data" / "editions"
        resources = candidate / "Resources" / "Editions"
        if data.is_dir() and resources.is_dir():
            return candidate
    raise GeneratorError(
        "Could not discover workspace root. Set "
        f"{WORKSPACE_ENV_VAR} or run from the Monopoly repository."
    )


WORKSPACE_ROOT = discover_workspace_root()

TEMPLATES_DIR = PROJECT_ROOT / "templates"
THEMES_DIR = PROJECT_ROOT / "themes"
BOARD_SPACES_DIR = PROJECT_ROOT / "assets" / "board-spaces"
ENERGY_GRID_ASSETS_DIR = BOARD_SPACES_DIR / "energy-grids"
OUTPUT_ROOT = PROJECT_ROOT / "output"
LEGACY_COMMON_INNERBOX = PROJECT_ROOT / "assets" / "common" / "InnerBox.png"

ENERGY_GRIDS_JSON = discover_workspace_root() / "EnergyGrid_Board" / "energy_grids.json"
ENERGY_GRID_BOARD_DIR = discover_workspace_root() / "EnergyGrid_Board"
ENERGY_GRID_CARD_DIR = discover_workspace_root() / "EnergyGrid_Card"
ENERGY_GRID_CARD_JSON = ENERGY_GRID_CARD_DIR / "energy_grids.json"
ENERGY_GRID_CARD_GENERATED_HTML_DIR = ENERGY_GRID_CARD_DIR / "generated_energy_grid_cards"
ENERGY_GRID_CARD_ASSETS_DIR = PROJECT_ROOT / "assets" / "cards" / "editions" / "india" / "energy-grid"
ENERGY_GRID_CARD_OUTPUT_DIR = OUTPUT_ROOT / "india" / "energy_grid_cards" / "png"
INDIA_ENERGY_GRIDS_DATA_JSON = (
    discover_workspace_root()
    / "monopoly-ultimate-banking-qr"
    / "data"
    / "editions"
    / "india"
    / "energy_grids.json"
)

ENERGY_GRID_WIDTH_CM = 4.625
ENERGY_GRID_HEIGHT_CM = 6.5

ENERGY_GRID_ASSET_FILES = {
    "ENG_01": "eng_01_solar.png",
    "ENG_02": "eng_02_wind.png",
    "ENG_03": "eng_03_hydroelectric.png",
    "ENG_04": "eng_04_biomass.png",
}

ENERGY_GRID_HTML_SOURCES = {
    "ENG_01": "ENG_01_Solar_Energy.html",
    "ENG_02": "ENG_02_Wind_Energy.html",
    "ENG_03": "ENG_03_Hydroelectric_Energy.html",
    "ENG_04": "ENG_04_Biomass_Energy.html",
}

ENERGY_GRID_CARD_HTML_SOURCES = {
    "ENG_01": "Solar_Energy.html",
    "ENG_02": "Wind_Energy.html",
    "ENG_03": "Hydroelectric_Energy.html",
    "ENG_04": "Biomass_Energy.html",
}

ENERGY_GRID_CARD_PNG_FILES = {
    "ENG_01": "eng_01_solar.png",
    "ENG_02": "eng_02_wind.png",
    "ENG_03": "eng_03_hydroelectric.png",
    "ENG_04": "eng_04_biomass.png",
}

BOARD_TEMPLATE = TEMPLATES_DIR / "board" / "board.html"
EVENT_CARD_TEMPLATE = TEMPLATES_DIR / "cards" / "event-card.html"
PROPERTY_CARD_TEMPLATE = TEMPLATES_DIR / "cards" / "property-card.html"

BOARD_ASSET_PREFIX = "../../assets/board-spaces"
ENERGY_GRID_ASSET_PREFIX = "../../assets/board-spaces/energy-grids"

# Fixed Monopoly board geometry. Do not expose these as editable settings.
OUTER_BOARD_SIZE_CM = 50.0
INNER_BOARD_SIZE_CM = 37.0
CM_PER_INCH = 2.54

DEFAULT_THEME_ID = "monopoly_default"

REQUIRED_BOARD_ASSETS = (
    "go.png",
    "jail.png",
    "go-to-jail.png",
    "free-parking.png",
    "event-space.png",
    "location-space.png",
)


def configure_stdio() -> None:
    """Allow currency symbols such as ₹ on Windows consoles."""
    import os
    import sys

    for name in ("stdout", "stderr"):
        stream = getattr(sys, name, None)
        if stream is None:
            setattr(sys, name, open(os.devnull, "w", encoding="utf-8"))
            continue
        try:
            stream.reconfigure(encoding="utf-8", errors="replace")
        except (AttributeError, ValueError):
            pass


def board_pixels_at_dpi(dpi: float, size_cm: float = OUTER_BOARD_SIZE_CM) -> int:
    return round((size_cm / CM_PER_INCH) * dpi)


def resources_root() -> Path:
    return discover_workspace_root() / "Resources"


INNER_BOX_BASENAME = "InnerBox"
INNER_BOX_EXTENSIONS = (".png", ".jpg", ".jpeg")


def inner_box_dir(edition_id: str) -> Path:
    return resources_root() / "Editions" / edition_id / "Board"


def inner_box_path(edition_id: str) -> Path:
    """Return the edition InnerBox file, preferring the first supported extension that exists."""
    board_dir = inner_box_dir(edition_id)
    for extension in INNER_BOX_EXTENSIONS:
        candidate = board_dir / f"{INNER_BOX_BASENAME}{extension}"
        if candidate.is_file():
            return candidate
    return board_dir / f"{INNER_BOX_BASENAME}.png"


def inner_box_display_path(edition_id: str) -> str:
    path = inner_box_path(edition_id)
    if path.is_file():
        return f"Resources/Editions/{edition_id}/Board/{path.name}"
    supported = ", ".join(f"InnerBox{extension}" for extension in INNER_BOX_EXTENSIONS)
    return f"Resources/Editions/{edition_id}/Board/({supported})"


def inner_box_status(edition_id: str) -> dict[str, Any]:
    path = inner_box_path(edition_id)
    found = path.is_file()
    display = inner_box_display_path(edition_id)
    return {
        "found": found,
        "path": display if found else None,
        "absolutePath": str(path) if found else None,
        "expectedPath": display,
        "action": "used" if found else "empty_center_used",
    }


def edition_display_name(edition_id: str, edition_config: dict[str, Any] | None = None) -> str:
    if edition_config:
        name = str(edition_config.get("name") or "").strip()
        if name:
            if name.lower().endswith(" edition"):
                return name[:-8].strip() or name
            return name
    return edition_display_token(edition_id, edition_config)


def list_editions() -> list[dict[str, Any]]:
    root = editions_root()
    editions: list[dict[str, Any]] = []
    for folder in sorted(root.iterdir(), key=lambda item: item.name.lower()):
        if not folder.is_dir():
            continue
        config_path = folder / "edition.json"
        config: dict[str, Any] = {}
        if config_path.is_file():
            try:
                loaded = load_json(config_path)
                if isinstance(loaded, dict):
                    config = loaded
            except GeneratorError:
                config = {}
        edition_id = str(config.get("editionId") or folder.name)
        editions.append(
            {
                "editionId": edition_id,
                "folderName": folder.name,
                "name": edition_display_name(edition_id, config),
                "config": config,
            }
        )
    return editions


def find_data_root() -> Path:
    root = discover_workspace_root()
    candidates = [
        root / "monopoly-ultimate-banking-qr" / "data",
        root / "data",
        PROJECT_ROOT / "data",
    ]
    for candidate in candidates:
        if (candidate / "editions").is_dir():
            return candidate
    raise GeneratorError(
        "Could not find game data. Expected editions under "
        "monopoly-ultimate-banking-qr/data/editions/."
    )


def editions_root() -> Path:
    return find_data_root() / "editions"


def edition_dir(edition_id: str) -> Path:
    path = editions_root() / edition_id
    if not path.is_dir():
        available = ", ".join(sorted(p.name for p in editions_root().iterdir() if p.is_dir())) or "(none)"
        raise GeneratorError(
            f"Unknown edition {edition_id!r}. Available editions: {available}"
        )
    return path


def edition_output_dir(edition_id: str) -> Path:
    return OUTPUT_ROOT / edition_id


def load_json(path: Path) -> Any:
    if not path.is_file():
        raise GeneratorError(f"File not found: {path}")
    try:
        with path.open("r", encoding="utf-8-sig") as handle:
            return json.load(handle)
    except json.JSONDecodeError as exc:
        raise GeneratorError(f"Invalid JSON in {path}: {exc}") from exc


def write_json(path: Path, payload: Any) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(payload, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")


def load_edition_json(edition_id: str, filename: str) -> Any:
    return load_json(edition_dir(edition_id) / filename)


def load_edition_config(edition_id: str) -> dict[str, Any]:
    data = load_edition_json(edition_id, "edition.json")
    if not isinstance(data, dict):
        raise GeneratorError("edition.json must be a JSON object.")
    return data


def theme_id_for_edition(edition_config: dict[str, Any]) -> str:
    return str(edition_config.get("theme") or DEFAULT_THEME_ID)


def load_theme(theme_id: str = DEFAULT_THEME_ID) -> dict[str, Any]:
    path = THEMES_DIR / f"{theme_id}.json"
    data = load_json(path)
    if not isinstance(data, dict):
        raise GeneratorError(f"Theme {theme_id!r} must be a JSON object.")
    return data


def property_card_base_colors(theme: dict[str, Any]) -> dict[str, str]:
    colors = theme.get("colors", {}).get("propertyCardBase")
    if not isinstance(colors, dict) or not colors:
        raise GeneratorError("Theme is missing colors.propertyCardBase.")
    return {str(key).upper(): str(value) for key, value in colors.items()}


def board_tile_colors(theme: dict[str, Any]) -> dict[str, dict[str, str]]:
    colors = theme.get("colors", {}).get("boardTiles")
    if not isinstance(colors, dict) or not colors:
        raise GeneratorError("Theme is missing colors.boardTiles.")
    result: dict[str, dict[str, str]] = {}
    for key, value in colors.items():
        if not isinstance(value, dict) or "dark" not in value or "light" not in value:
            raise GeneratorError(f"Theme boardTiles.{key} must contain dark and light.")
        result[str(key).upper()] = {"dark": str(value["dark"]), "light": str(value["light"])}
    return result


def currency_symbol(banking: dict[str, Any]) -> str:
    try:
        symbol = str(banking["currency"]["symbol"])
    except (KeyError, TypeError) as exc:
        raise GeneratorError("banking_values.json must contain currency.symbol") from exc
    if not symbol:
        raise GeneratorError("currency.symbol cannot be empty.")
    return symbol


def numeric_banking_value(banking: dict[str, Any], key: str) -> int | float:
    if key not in banking:
        raise GeneratorError(f"banking_values.json must contain {key}")
    value = banking[key]
    if not isinstance(value, (int, float)) or isinstance(value, bool):
        raise GeneratorError(f"{key} must be numeric.")
    return value


def edition_display_token(edition_id: str, edition_config: dict[str, Any] | None = None) -> str:
    if edition_config:
        name = str(edition_config.get("name") or "").strip()
        if name:
            token = name.replace(" Edition", "").replace(" edition", "").strip()
            if token:
                return token.replace(" ", "_")
    if len(edition_id) <= 3:
        return edition_id.upper()
    return edition_id[:1].upper() + edition_id[1:].lower()


def ensure_dir(path: Path) -> Path:
    path.mkdir(parents=True, exist_ok=True)
    return path


def posix_relative(from_dir: Path, to_path: Path) -> str:
    import os

    return Path(os.path.relpath(to_path.resolve(), from_dir.resolve())).as_posix()


def project_asset_path(asset_ref: str) -> Path:
    normalized = asset_ref.replace("\\", "/").lstrip("/")
    if normalized.startswith("assets/"):
        return (PROJECT_ROOT / normalized).resolve()
    return (PROJECT_ROOT / normalized).resolve()


def event_artwork_dir(edition_id: str) -> Path:
    return EVENT_CARD_ARTWORK_ROOT / edition_id / "event-artwork"


def rewrite_board_asset_paths(html: str, html_output_path: Path) -> str:
    """Point board artwork at assets/board-spaces from the generated HTML location."""
    relative_energy = posix_relative(html_output_path.parent, ENERGY_GRID_ASSETS_DIR)
    html = html.replace(ENERGY_GRID_ASSET_PREFIX, relative_energy)
    relative_assets = posix_relative(html_output_path.parent, BOARD_SPACES_DIR)
    return re.sub(
        re.escape(BOARD_ASSET_PREFIX) + r"(?!/energy-grids)",
        relative_assets,
        html,
    )


def normalize_name(name: str) -> str:
    return " ".join(str(name).strip().split()).upper()


def safe_filename_component(value: str) -> str:
    value = normalize_name(value)
    value = re.sub(r"[^A-Z0-9]+", "_", value)
    return value.strip("_") or "ITEM"


def read_text(path: Path) -> str:
    if not path.is_file():
        raise GeneratorError(f"File not found: {path}")
    return path.read_text(encoding="utf-8")


def remove_generated_tree(path: Path) -> None:
    """Remove a generated output directory without touching sibling edition output."""
    if path.is_dir():
        shutil.rmtree(path)


def clean_stale_outputs(edition_id: str, *, properties: bool, events: bool, board: bool) -> None:
    """Remove stale HTML/PNG for artifact types about to be regenerated."""
    edition_out = edition_output_dir(edition_id)
    if properties:
        remove_generated_tree(edition_out / "property_cards" / "html")
        remove_generated_tree(edition_out / "property_cards" / "png")
    if events:
        remove_generated_tree(edition_out / "event_cards" / "html")
        remove_generated_tree(edition_out / "event_cards" / "png")
    if board:
        board_dir = edition_out / "board"
        if board_dir.is_dir():
            for path in board_dir.glob("Board_*.html"):
                path.unlink(missing_ok=True)
            for path in board_dir.glob("Board_*DPI.png"):
                path.unlink(missing_ok=True)
