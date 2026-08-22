"""Shared path, JSON, and formatting helpers for the card generators."""

from __future__ import annotations

import json
import re
from pathlib import Path
from typing import Any

GENERATOR_ROOT = Path(__file__).resolve().parent.parent
WORKSPACE_ROOT = GENERATOR_ROOT.parent
TEMPLATES_DIR = GENERATOR_ROOT / "templates"
THEMES_DIR = GENERATOR_ROOT / "themes"
ASSETS_COMMON_DIR = GENERATOR_ROOT / "assets" / "common"
OUTPUT_ROOT = GENERATOR_ROOT / "output"

DEFAULT_THEME_ID = "monopoly_default"


class GeneratorError(Exception):
    """User-facing configuration or generation error."""


def configure_stdio() -> None:
    """Allow currency symbols such as ₹ on Windows consoles."""
    import os
    import sys

    for name in ("stdout", "stderr"):
        stream = getattr(sys, name, None)
        if stream is None:
            # pythonw.exe has no console; keep generator prints harmless.
            setattr(sys, name, open(os.devnull, "w", encoding="utf-8"))
            continue
        try:
            stream.reconfigure(encoding="utf-8", errors="replace")
        except (AttributeError, ValueError):
            pass


def resources_root() -> Path:
    """Workspace Resources/ directory (edition artwork)."""
    return WORKSPACE_ROOT / "Resources"


def inner_box_path(edition_id: str) -> Path:
    """Expected optional center artwork for an edition."""
    return resources_root() / "Editions" / edition_id / "Board" / "InnerBox.png"


def inner_box_display_path(edition_id: str) -> str:
    return f"Resources/Editions/{edition_id}/Board/InnerBox.png"


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
    """Discover editions from data/editions/*/edition.json."""
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
    """Locate the authoritative game data directory."""
    candidates = [
        WORKSPACE_ROOT / "monopoly-ultimate-banking-qr" / "data",
        WORKSPACE_ROOT / "data",
        GENERATOR_ROOT / "data",
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
    """Short label used in output filenames, e.g. India or UK."""
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


def rewrite_board_asset_paths(html: str, html_output_path: Path) -> str:
    """Point board artwork at assets/common from the generated HTML location."""
    relative_assets = posix_relative(html_output_path.parent, ASSETS_COMMON_DIR)
    return html.replace("../assets/common", relative_assets)


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
