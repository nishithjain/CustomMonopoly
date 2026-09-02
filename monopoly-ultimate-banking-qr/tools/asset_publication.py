"""Publish generated card fronts from generator output to Resources."""

from __future__ import annotations

import json
import os
import tempfile
from dataclasses import asdict, dataclass
from pathlib import Path
from typing import Any, Literal

try:
    from PIL import Image
except ImportError:  # pragma: no cover
    Image = None  # type: ignore[assignment,misc]

from workspace_paths import (
    edition_data_dir,
    find_generator_root,
    find_project_root,
    find_workspace_root,
    generator_output_dir,
    load_json,
    resolve_workspace_asset,
    sha256_file,
)

CardType = Literal["event", "property"]
PublicationStatus = Literal[
    "published",
    "skipped_identical",
    "missing_source",
    "missing_target_parent",
    "conflict",
    "dry_run",
    "verify_pass",
    "verify_fail",
]


@dataclass(frozen=True)
class PublicationEntry:
    edition_id: str
    card_type: str
    card_id: str
    sequence: int
    name: str
    generated_source_path: str
    published_target_path: str
    source_sha256: str | None
    target_sha256: str | None
    status: str
    message: str = ""


def _require_pillow() -> Any:
    if Image is None:
        raise RuntimeError("Pillow is required for image validation. Install with: pip install Pillow")
    return Image


def validate_image_file(path: Path) -> tuple[int, int, str]:
    pillow = _require_pillow()
    if not path.is_file():
        raise FileNotFoundError(f"Image not found: {path}")
    suffix = path.suffix.lower()
    if suffix not in {".png", ".jpg", ".jpeg", ".webp"}:
        raise ValueError(f"Unsupported image format for {path.name}")
    with pillow.open(path) as image:
        image.verify()
    with pillow.open(path) as image:
        image.load()
        width, height = image.size
        format_name = (image.format or suffix.lstrip(".")).upper()
    if width <= 0 or height <= 0:
        raise ValueError(f"Invalid image dimensions for {path}")
    return width, height, format_name


def find_generated_png(output_dir: Path, card_type: CardType, sequence: int) -> Path:
    if card_type == "property":
        folder = output_dir / "property_cards" / "png"
        prefix = f"{sequence:02d}_"
    else:
        folder = output_dir / "event_cards" / "png"
        prefix = f"E{sequence:02d}_"
    if not folder.is_dir():
        raise FileNotFoundError(f"Generator output folder not found: {folder}")
    matches = sorted(path for path in folder.iterdir() if path.is_file() and path.name.startswith(prefix))
    if not matches:
        raise FileNotFoundError(f"No generated PNG with prefix {prefix!r} in {folder}")
    if len(matches) > 1:
        names = ", ".join(path.name for path in matches)
        raise RuntimeError(f"Ambiguous generated PNG matches for sequence {sequence}: {names}")
    return matches[0]


def _card_records(
    project_root: Path,
    edition_id: str,
    card_type: CardType,
) -> list[dict[str, Any]]:
    if card_type == "property":
        payload = load_json(edition_data_dir(project_root, edition_id) / "properties.json")
        key = "properties"
        id_field = "propertyId"
    else:
        payload = load_json(edition_data_dir(project_root, edition_id) / "events.json")
        key = "events"
        id_field = "eventId"
    records: list[dict[str, Any]] = []
    for item in payload[key]:
        records.append(
            {
                "card_id": str(item[id_field]),
                "sequence": int(item["sequence"]),
                "name": str(item["name"]),
                "front_asset": str(item["frontAsset"]),
            },
        )
    return records


def _assert_edition_scope(edition_id: str, front_asset: str, card_id: str) -> None:
    normalized = front_asset.replace("\\", "/")
    expected = f"Resources/Editions/{edition_id}/"
    if not normalized.startswith(expected):
        raise ValueError(
            f"{card_id}: frontAsset {front_asset!r} is outside edition scope {expected}",
        )


def atomic_copy(source: Path, destination: Path) -> None:
    destination.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.NamedTemporaryFile(
        dir=destination.parent,
        prefix=f".{destination.name}.",
        suffix=".tmp",
        delete=False,
    ) as handle:
        temp_path = Path(handle.name)
    try:
        temp_path.write_bytes(source.read_bytes())
        os.replace(temp_path, destination)
    finally:
        if temp_path.exists():
            temp_path.unlink(missing_ok=True)


def build_publication_manifest(
    workspace_root: Path,
    edition_id: str,
    *,
    card_types: tuple[CardType, ...] = ("event", "property"),
) -> list[PublicationEntry]:
    project_root = find_project_root(workspace_root)
    output_dir = generator_output_dir(workspace_root, edition_id)
    entries: list[PublicationEntry] = []

    for card_type in card_types:
        for record in _card_records(project_root, edition_id, card_type):
            front_asset = record["front_asset"]
            _assert_edition_scope(edition_id, front_asset, record["card_id"])
            target = resolve_workspace_asset(workspace_root, front_asset)
            try:
                source = find_generated_png(output_dir, card_type, record["sequence"])
                source_hash = sha256_file(source)
                validate_image_file(source)
                status = "pending"
                target_hash = sha256_file(target) if target.is_file() else None
                if target.is_file() and target_hash == source_hash:
                    status = "skipped_identical"
                elif target.is_file():
                    status = "conflict"
                else:
                    status = "ready_to_publish"
            except Exception as exc:
                entries.append(
                    PublicationEntry(
                        edition_id=edition_id,
                        card_type=card_type,
                        card_id=record["card_id"],
                        sequence=record["sequence"],
                        name=record["name"],
                        generated_source_path="",
                        published_target_path=front_asset,
                        source_sha256=None,
                        target_sha256=sha256_file(target) if target.is_file() else None,
                        status="missing_source",
                        message=str(exc),
                    ),
                )
                continue

            entries.append(
                PublicationEntry(
                    edition_id=edition_id,
                    card_type=card_type,
                    card_id=record["card_id"],
                    sequence=record["sequence"],
                    name=record["name"],
                    generated_source_path=str(source.relative_to(workspace_root)).replace("\\", "/"),
                    published_target_path=front_asset,
                    source_sha256=source_hash,
                    target_sha256=target_hash,
                    status=status,
                ),
            )
    return entries


def write_manifest(entries: list[PublicationEntry], manifest_path: Path) -> None:
    manifest_path.parent.mkdir(parents=True, exist_ok=True)
    payload = [asdict(entry) for entry in entries]
    manifest_path.write_text(json.dumps(payload, indent=2) + "\n", encoding="utf-8")


def publish_edition_card_fronts(
    edition_id: str,
    *,
    card_type: Literal["event", "property", "all"] = "all",
    dry_run: bool = False,
    verify: bool = False,
    overwrite: bool = False,
    workspace_root: Path | None = None,
) -> tuple[list[PublicationEntry], int]:
    workspace_root = workspace_root or find_workspace_root()
    project_root = find_project_root(workspace_root)
    output_dir = generator_output_dir(workspace_root, edition_id)

    selected: tuple[CardType, ...]
    if card_type == "all":
        selected = ("event", "property")
    else:
        selected = (card_type,)

    results: list[PublicationEntry] = []
    exit_code = 0

    for current_type in selected:
        for record in _card_records(project_root, edition_id, current_type):
            card_id = record["card_id"]
            front_asset = record["front_asset"]
            try:
                _assert_edition_scope(edition_id, front_asset, card_id)
                source = find_generated_png(output_dir, current_type, record["sequence"])
                target = resolve_workspace_asset(workspace_root, front_asset)
                validate_image_file(source)
                source_hash = sha256_file(source)
            except Exception as exc:
                results.append(
                    PublicationEntry(
                        edition_id=edition_id,
                        card_type=current_type,
                        card_id=card_id,
                        sequence=record["sequence"],
                        name=record["name"],
                        generated_source_path="",
                        published_target_path=front_asset,
                        source_sha256=None,
                        target_sha256=None,
                        status="missing_source",
                        message=str(exc),
                    ),
                )
                exit_code = 1
                continue

            target_hash = sha256_file(target) if target.is_file() else None
            rel_source = str(source.relative_to(workspace_root)).replace("\\", "/")

            if verify:
                if target.is_file() and target_hash == source_hash:
                    status: PublicationStatus = "verify_pass"
                    message = ""
                else:
                    status = "verify_fail"
                    exit_code = 1
                    message = "Published target missing or does not match generated source."
                results.append(
                    PublicationEntry(
                        edition_id=edition_id,
                        card_type=current_type,
                        card_id=card_id,
                        sequence=record["sequence"],
                        name=record["name"],
                        generated_source_path=rel_source,
                        published_target_path=front_asset,
                        source_sha256=source_hash,
                        target_sha256=target_hash,
                        status=status,
                        message=message if status == "verify_fail" else "",
                    ),
                )
                continue

            if target.is_file() and target_hash == source_hash:
                results.append(
                    PublicationEntry(
                        edition_id=edition_id,
                        card_type=current_type,
                        card_id=card_id,
                        sequence=record["sequence"],
                        name=record["name"],
                        generated_source_path=rel_source,
                        published_target_path=front_asset,
                        source_sha256=source_hash,
                        target_sha256=target_hash,
                        status="skipped_identical",
                    ),
                )
                continue

            if target.is_file() and target_hash != source_hash and not overwrite:
                results.append(
                    PublicationEntry(
                        edition_id=edition_id,
                        card_type=current_type,
                        card_id=card_id,
                        sequence=record["sequence"],
                        name=record["name"],
                        generated_source_path=rel_source,
                        published_target_path=front_asset,
                        source_sha256=source_hash,
                        target_sha256=target_hash,
                        status="conflict",
                        message="Target exists with different content. Use --overwrite to replace.",
                    ),
                )
                exit_code = 1
                continue

            if dry_run:
                results.append(
                    PublicationEntry(
                        edition_id=edition_id,
                        card_type=current_type,
                        card_id=card_id,
                        sequence=record["sequence"],
                        name=record["name"],
                        generated_source_path=rel_source,
                        published_target_path=front_asset,
                        source_sha256=source_hash,
                        target_sha256=target_hash,
                        status="dry_run",
                    ),
                )
                continue

            atomic_copy(source, target)
            published_hash = sha256_file(target)
            if published_hash != source_hash:
                results.append(
                    PublicationEntry(
                        edition_id=edition_id,
                        card_type=current_type,
                        card_id=card_id,
                        sequence=record["sequence"],
                        name=record["name"],
                        generated_source_path=rel_source,
                        published_target_path=front_asset,
                        source_sha256=source_hash,
                        target_sha256=published_hash,
                        status="conflict",
                        message="Published file hash does not match source after copy.",
                    ),
                )
                exit_code = 1
                continue

            results.append(
                PublicationEntry(
                    edition_id=edition_id,
                    card_type=current_type,
                    card_id=card_id,
                    sequence=record["sequence"],
                    name=record["name"],
                    generated_source_path=rel_source,
                    published_target_path=front_asset,
                    source_sha256=source_hash,
                    target_sha256=published_hash,
                    status="published",
                ),
            )

    manifest_path = generator_output_dir(workspace_root, edition_id) / "publication-manifest.json"
    write_manifest(results, manifest_path)
    return results, exit_code
