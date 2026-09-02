"""Validate and embed event-card centre artwork."""

from __future__ import annotations

import base64
import re
from dataclasses import dataclass
from pathlib import Path
from typing import Any

from monopoly_edition_generator.paths import (
    EVENT_CARD_ARTWORK_ROOT,
    GeneratorError,
    PROJECT_ROOT,
    event_artwork_dir,
    safe_filename_component,
)

SUPPORTED_EXTENSIONS = {".png", ".jpg", ".jpeg", ".webp"}
MIME_TYPES = {
    ".png": "image/png",
    ".jpg": "image/jpeg",
    ".jpeg": "image/jpeg",
    ".webp": "image/webp",
}
RECOMMENDED_SIZE = 1063
MIN_SIZE = 1024
EVENT_ID_PATTERN = re.compile(r"^EVT_\d{2}$")


@dataclass(frozen=True)
class ArtworkRecord:
    event_id: str
    event_name: str
    filename: str
    format_name: str
    width: int
    height: int
    mapping_result: str
    asset_ref: str
    warnings: tuple[str, ...] = ()

    @property
    def aspect_ratio(self) -> str:
        return f"{self.width}:{self.height}"


def _require_pillow():
    try:
        from PIL import Image
    except ImportError as exc:
        raise GeneratorError("Pillow is required to validate event artwork.") from exc
    return Image


def normalize_asset_ref(asset_ref: str) -> str:
    normalized = str(asset_ref or "").strip().replace("\\", "/").lstrip("/")
    if not normalized:
        raise GeneratorError("artworkAsset cannot be empty.")
    if ":" in normalized or normalized.startswith("//"):
        raise GeneratorError(f"artworkAsset must be a repository-relative path, not {asset_ref!r}.")
    if not normalized.startswith("assets/cards/editions/"):
        raise GeneratorError(
            f"artworkAsset must start with assets/cards/editions/, got {asset_ref!r}."
        )
    return normalized


def resolve_event_artwork_path(asset_ref: str, *, edition_id: str | None = None) -> Path:
    normalized = normalize_asset_ref(asset_ref)
    resolved = (PROJECT_ROOT / normalized).resolve()
    permitted_root = EVENT_CARD_ARTWORK_ROOT.resolve()
    try:
        resolved.relative_to(permitted_root)
    except ValueError as exc:
        raise GeneratorError(
            f"artworkAsset {asset_ref!r} resolves outside permitted artwork directory "
            f"{permitted_root}."
        ) from exc
    if edition_id:
        expected_dir = event_artwork_dir(edition_id).resolve()
        try:
            resolved.relative_to(expected_dir)
        except ValueError as exc:
            raise GeneratorError(
                f"artworkAsset {asset_ref!r} is not inside edition artwork directory "
                f"{expected_dir}."
            ) from exc
    return resolved


def inspect_artwork_file(path: Path) -> tuple[str, int, int, tuple[str, ...]]:
    Image = _require_pillow()
    if not path.is_file():
        raise GeneratorError(f"Artwork file not found: {path}")

    suffix = path.suffix.lower()
    if suffix not in SUPPORTED_EXTENSIONS:
        raise GeneratorError(
            f"Unsupported artwork format {path.suffix!r}. "
            f"Supported formats: {', '.join(sorted(SUPPORTED_EXTENSIONS))}."
        )

    try:
        with Image.open(path) as image:
            image.verify()
        with Image.open(path) as image:
            image.load()
            width, height = image.size
            format_name = (image.format or suffix.lstrip(".")).upper()
    except GeneratorError:
        raise
    except Exception as exc:
        raise GeneratorError(f"Artwork file cannot be decoded: {path}") from exc

    warnings: list[str] = []
    if width != height:
        raise GeneratorError(
            f"Artwork must be square (1:1). {path.name} is {width}×{height}."
        )
    if width < MIN_SIZE or height < MIN_SIZE:
        raise GeneratorError(
            f"Artwork must be at least {MIN_SIZE}×{MIN_SIZE}px. "
            f"{path.name} is {width}×{height}."
        )
    if width != RECOMMENDED_SIZE or height != RECOMMENDED_SIZE:
        warnings.append(
            f"Recommended size is {RECOMMENDED_SIZE}×{RECOMMENDED_SIZE}px; "
            f"{path.name} is {width}×{height}."
        )
    return format_name, width, height, tuple(warnings)


def encode_artwork_data_uri(path: Path) -> str:
    inspect_artwork_file(path)
    mime = MIME_TYPES[path.suffix.lower()]
    encoded = base64.b64encode(path.read_bytes()).decode("ascii")
    return f"data:{mime};base64,{encoded}"


def _event_id_from_filename(filename: str) -> str | None:
    prefix = filename.split("_", 2)[0]
    if EVENT_ID_PATTERN.fullmatch(prefix):
        return prefix
    return None


def _normalized_event_name(name: str) -> str:
    return safe_filename_component(name)


def discover_artwork_files(edition_id: str) -> dict[str, list[Path]]:
    directory = event_artwork_dir(edition_id)
    if not directory.is_dir():
        return {}

    grouped: dict[str, list[Path]] = {}
    for path in sorted(directory.iterdir()):
        if not path.is_file() or path.suffix.lower() not in SUPPORTED_EXTENSIONS:
            continue
        event_id = _event_id_from_filename(path.name)
        if event_id is None:
            continue
        grouped.setdefault(event_id, []).append(path)
    return grouped


def map_event_artwork(event: dict[str, Any], edition_id: str) -> ArtworkRecord:
    event_id = str(event.get("eventId") or "").strip()
    event_name = str(event.get("name") or "").strip()
    asset_ref = str(event.get("artworkAsset") or "").strip()

    if not event_id:
        raise GeneratorError("Event is missing eventId.")
    if not asset_ref:
        raise GeneratorError(
            f"{event_id} ({event_name or 'unnamed event'}): artworkAsset is not configured."
        )

    discovered = discover_artwork_files(edition_id).get(event_id, [])
    if len(discovered) > 1:
        names = ", ".join(path.name for path in discovered)
        raise GeneratorError(
            f"{event_id} ({event_name}): multiple artwork files match this event: {names}."
        )

    resolved = resolve_event_artwork_path(asset_ref, edition_id=edition_id)
    if not resolved.is_file():
        reason = "configured artwork file does not exist"
        raise GeneratorError(
            f"{event_id} ({event_name}): {reason} at {asset_ref}."
        )

    expected_prefix = f"{event_id}_"
    if not resolved.name.startswith(expected_prefix):
        raise GeneratorError(
            f"{event_id} ({event_name}): artwork filename {resolved.name!r} "
            f"does not use the required {expected_prefix!r} prefix."
        )

    if discovered and discovered[0].resolve() != resolved.resolve():
        raise GeneratorError(
            f"{event_id} ({event_name}): artworkAsset points to {resolved.name}, "
            f"but directory also contains {discovered[0].name}."
        )

    format_name, width, height, warnings = inspect_artwork_file(resolved)
    mapping_result = "matched"
    if discovered and discovered[0].resolve() == resolved.resolve():
        mapping_result = "matched by EVT prefix"

    return ArtworkRecord(
        event_id=event_id,
        event_name=event_name,
        filename=resolved.name,
        format_name=format_name,
        width=width,
        height=height,
        mapping_result=mapping_result,
        asset_ref=normalize_asset_ref(asset_ref),
        warnings=warnings,
    )


def map_edition_artwork(edition_id: str, events: list[dict[str, Any]]) -> list[ArtworkRecord]:
    records: list[ArtworkRecord] = []
    seen_files: set[Path] = set()
    event_ids = {str(event.get("eventId") or "") for event in events}

    for event in events:
        if not str(event.get("artworkAsset") or "").strip():
            continue
        record = map_event_artwork(event, edition_id)
        records.append(record)
        seen_files.add(resolve_event_artwork_path(record.asset_ref, edition_id=edition_id))

    discovered = discover_artwork_files(edition_id)
    for event_id, paths in sorted(discovered.items()):
        if event_id not in event_ids:
            names = ", ".join(path.name for path in paths)
            raise GeneratorError(
                f"Artwork file(s) {names} use unknown event ID {event_id}."
            )
        resolved_paths = {path.resolve() for path in paths}
        if resolved_paths - seen_files:
            orphan_names = ", ".join(path.name for path in paths if path.resolve() not in seen_files)
            raise GeneratorError(
                f"Artwork file(s) {orphan_names} are not mapped by events.json for {event_id}."
            )

    return records


def build_artwork_block(event: dict[str, Any], edition_id: str) -> str:
    import html

    asset_ref = str(event.get("artworkAsset") or "").strip()
    event_id = str(event.get("eventId") or "EVT")
    event_name = str(event.get("name") or "Event")
    alt = html.escape(f"{event_name} event artwork", quote=True)
    fit = str(event.get("artworkFit") or "contain").strip().lower()
    fit_class = " artwork-cover" if fit == "cover" else ""

    if not asset_ref:
        return '<div class="event-image" aria-hidden="true"></div>'

    try:
        record = map_event_artwork(event, edition_id)
        data_uri = encode_artwork_data_uri(
            resolve_event_artwork_path(record.asset_ref, edition_id=edition_id)
        )
    except GeneratorError as exc:
        raise GeneratorError(
            f"{event_id} ({event_name}): configured artwork path {asset_ref!r} failed: {exc}"
        ) from exc

    return (
        f'<div class="event-image">'
        f'<img class="event-artwork{fit_class}" src="{data_uri}" alt="{alt}">'
        f"</div>"
    )
