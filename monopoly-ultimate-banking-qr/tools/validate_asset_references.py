#!/usr/bin/env python3
"""Validate edition image asset references across generator, Resources, and Android."""

from __future__ import annotations

import argparse
import json
import sys
from dataclasses import dataclass
from pathlib import Path

from workspace_paths import (
    VALID_ARTWORK_STATUSES,
    edition_data_dir,
    find_generator_root,
    find_project_root,
    find_workspace_root,
    load_json,
    resolve_generator_asset,
    resolve_workspace_asset,
    sha256_file,
)


@dataclass
class ReferenceIssue:
    level: str
    category: str
    card_id: str
    field: str
    path: str
    message: str


IMAGE_FIELDS = ("frontAsset", "qrAsset", "artworkAsset")


def _walk_assets(obj, visitor) -> None:
    if isinstance(obj, dict):
        for key, value in obj.items():
            if key in IMAGE_FIELDS and isinstance(value, str) and value.strip():
                visitor(key, value)
            else:
                _walk_assets(value, visitor)
    elif isinstance(obj, list):
        for item in obj:
            _walk_assets(item, visitor)


def validate_edition_assets(edition_id: str, workspace_root: Path | None = None) -> tuple[list[ReferenceIssue], dict]:
    workspace_root = workspace_root or find_workspace_root()
    project_root = find_project_root(workspace_root)
    generator_root = find_generator_root(workspace_root)
    issues: list[ReferenceIssue] = []
    stats = {
        "artworkAsset_total": 0,
        "artworkAsset_ok": 0,
        "frontAsset_total": 0,
        "frontAsset_ok": 0,
        "qrAsset_total": 0,
        "qrAsset_ok": 0,
        "qrAsset_pending": 0,
        "json_output_refs": 0,
        "absolute_paths": 0,
        "backslash_paths": 0,
    }

    edition_dir = edition_data_dir(project_root, edition_id)
    for filename in ("events.json", "properties.json"):
        json_path = edition_dir / filename
        payload = load_json(json_path)

        def visitor(field: str, value: str, source=filename) -> None:
            if "output/" in value.replace("\\", "/"):
                stats["json_output_refs"] += 1
                issues.append(
                    ReferenceIssue(
                        "error",
                        "json_path",
                        source,
                        field,
                        value,
                        "JSON must not reference generator output/",
                    ),
                )
            if ":" in value and value[1:2] == ":":
                stats["absolute_paths"] += 1
                issues.append(
                    ReferenceIssue(
                        "error",
                        "json_path",
                        source,
                        field,
                        value,
                        "JSON must not contain absolute Windows paths.",
                    ),
                )
            if "\\" in value:
                stats["backslash_paths"] += 1
                issues.append(
                    ReferenceIssue(
                        "error",
                        "json_path",
                        source,
                        field,
                        value,
                        "JSON asset paths must use forward slashes.",
                    ),
                )

            if field == "artworkAsset":
                stats["artworkAsset_total"] += 1
                try:
                    resolved = resolve_generator_asset(generator_root, value)
                except ValueError as exc:
                    issues.append(
                        ReferenceIssue("error", "source_artwork", source, field, value, str(exc)),
                    )
                    return
                if resolved.is_file():
                    stats["artworkAsset_ok"] += 1
                else:
                    issues.append(
                        ReferenceIssue(
                            "error",
                            "source_artwork",
                            source,
                            field,
                            value,
                            "Source artwork file does not exist.",
                        ),
                    )
                return

            if field in {"frontAsset", "qrAsset"}:
                key = f"{field}_total"
                stats[key] += 1
                try:
                    resolved = resolve_workspace_asset(workspace_root, value)
                except ValueError as exc:
                    issues.append(
                        ReferenceIssue("error", field, source, field, value, str(exc)),
                    )
                    return
                expected_prefix = f"Resources/Editions/{edition_id}/"
                if not value.replace("\\", "/").startswith(expected_prefix):
                    issues.append(
                        ReferenceIssue(
                            "error",
                            field,
                            source,
                            field,
                            value,
                            f"{field} must remain under {expected_prefix}",
                        ),
                    )
                if resolved.is_file():
                    stats[f"{field}_ok"] += 1
                elif field == "qrAsset":
                    stats["qrAsset_pending"] += 1
                    issues.append(
                        ReferenceIssue(
                            "pending",
                            "qr_back",
                            source,
                            field,
                            value,
                            "QR Card Back pending generation by separate QRCode workspace.",
                        ),
                    )
                else:
                    issues.append(
                        ReferenceIssue(
                            "error",
                            "card_front",
                            source,
                            field,
                            value,
                            "Published Card Front is missing.",
                        ),
                    )

        _walk_assets(payload, visitor)

    android_event_dir = (
        project_root
        / "android-app"
        / "app"
        / "src"
        / "main"
        / "assets"
        / "cards"
        / "editions"
        / edition_id
        / "event"
    )
    android_property_dir = android_event_dir.parent / "property"
    if edition_id == "india":
        expected_events = 25
        expected_properties = 22
    elif edition_id == "uk":
        expected_events = 23
        expected_properties = 22
    else:
        expected_events = None
        expected_properties = None

    if android_event_dir.is_dir() and expected_events is not None:
        event_files = sorted(path for path in android_event_dir.glob("evt_*.png") if path.is_file())
        if len(event_files) != expected_events:
            issues.append(
                ReferenceIssue(
                    "error",
                    "android_sync",
                    edition_id,
                    "event",
                    str(android_event_dir),
                    f"Expected {expected_events} Android event fronts, found {len(event_files)}.",
                ),
            )
    if android_property_dir.is_dir() and expected_properties is not None:
        property_files = sorted(path for path in android_property_dir.glob("prp_*.png") if path.is_file())
        if len(property_files) != expected_properties:
            issues.append(
                ReferenceIssue(
                    "error",
                    "android_sync",
                    edition_id,
                    "property",
                    str(android_property_dir),
                    f"Expected {expected_properties} Android property fronts, found {len(property_files)}.",
                ),
            )

    edition = load_json(edition_dir / "edition.json")
    status = str(edition.get("artworkStatus") or "READY")
    if status not in VALID_ARTWORK_STATUSES:
        issues.append(
            ReferenceIssue(
                "error",
                "edition_metadata",
                edition_id,
                "artworkStatus",
                status,
                f"Unknown artworkStatus. Allowed: {', '.join(sorted(VALID_ARTWORK_STATUSES))}",
            ),
        )

    return issues, stats


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--edition", default="india")
    args = parser.parse_args(argv)

    issues, stats = validate_edition_assets(args.edition)
    errors = [issue for issue in issues if issue.level == "error"]
    pending = [issue for issue in issues if issue.level == "pending"]

    print(f"Asset reference validation for edition: {args.edition}")
    print(json.dumps(stats, indent=2))
    print()
    if errors:
        print("Errors:")
        for issue in errors:
            print(f"  - [{issue.category}] {issue.card_id} {issue.field}: {issue.message} ({issue.path})")
    if pending:
        print("Pending:")
        for issue in pending:
            print(f"  - {issue.path}")
    print()
    fronts_ok = (
        stats["frontAsset_ok"] == stats["frontAsset_total"] and stats["frontAsset_total"] > 0
    )
    artwork_ok = stats["artworkAsset_ok"] == stats["artworkAsset_total"]
    print(f"Front publication validation: {'PASS' if fronts_ok else 'FAIL'}")
    print(f"Source artwork validation: {'PASS' if artwork_ok else 'FAIL'}")
    print(
        "QR Back publication: "
        + (
            f"PENDING — {stats['qrAsset_pending']} files"
            if stats["qrAsset_pending"]
            else "PASS"
        ),
    )
    return 1 if errors else 0


if __name__ == "__main__":
    sys.exit(main())
