"""Shared workspace and asset path helpers for publication tools."""

from __future__ import annotations

import hashlib
import json
from pathlib import Path
from typing import Any

VALID_ARTWORK_STATUSES = frozenset({"READY", "INCOMPLETE", "FRONTS_READY"})
IMAGE_EXTENSIONS = {".png", ".jpg", ".jpeg", ".webp"}


def find_workspace_root(start: Path | None = None) -> Path:
    start = (start or Path(__file__)).resolve()
    for candidate in [start.parent.parent, *start.parents]:
        resources = candidate / "Resources"
        generators = candidate / "monopoly-edition-generator"
        if resources.is_dir() and generators.is_dir():
            return candidate
    raise FileNotFoundError(
        "Could not locate workspace root containing Resources/ and monopoly-edition-generator/",
    )


def find_project_root(workspace_root: Path) -> Path:
    direct = workspace_root / "monopoly-ultimate-banking-qr"
    if (direct / "data" / "editions").is_dir():
        return direct
    if (workspace_root / "data" / "editions").is_dir():
        return workspace_root
    raise FileNotFoundError("Could not locate monopoly-ultimate-banking-qr data/editions/")


def find_generator_root(workspace_root: Path) -> Path:
    path = workspace_root / "monopoly-edition-generator"
    if not path.is_dir():
        raise FileNotFoundError("Could not locate monopoly-edition-generator/")
    return path


def load_json(path: Path) -> Any:
    with path.open(encoding="utf-8") as handle:
        return json.load(handle)


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def normalize_asset_ref(asset_ref: str) -> str:
    normalized = str(asset_ref or "").strip().replace("\\", "/")
    if not normalized:
        raise ValueError("Asset path cannot be empty.")
    if ":" in normalized or normalized.startswith("//"):
        raise ValueError(f"Asset path must be repository-relative, not {asset_ref!r}.")
    if "\\" in asset_ref:
        raise ValueError(f"Asset path must use forward slashes, not {asset_ref!r}.")
    return normalized


def resolve_workspace_asset(workspace_root: Path, asset_ref: str) -> Path:
    normalized = normalize_asset_ref(asset_ref)
    if not normalized.startswith("Resources/"):
        raise ValueError(f"Workspace asset must start with Resources/, got {asset_ref!r}.")
    return (workspace_root / normalized).resolve()


def resolve_generator_asset(generator_root: Path, asset_ref: str) -> Path:
    normalized = normalize_asset_ref(asset_ref)
    if not normalized.startswith("assets/"):
        raise ValueError(f"Generator asset must start with assets/, got {asset_ref!r}.")
    resolved = (generator_root / normalized).resolve()
    permitted = (generator_root / "assets").resolve()
    try:
        resolved.relative_to(permitted)
    except ValueError as exc:
        raise ValueError(f"Generator asset resolves outside assets/: {asset_ref!r}") from exc
    return resolved


def edition_data_dir(project_root: Path, edition_id: str) -> Path:
    return project_root / "data" / "editions" / edition_id


def generator_output_dir(workspace_root: Path, edition_id: str) -> Path:
    return workspace_root / "monopoly-edition-generator" / "output" / edition_id
