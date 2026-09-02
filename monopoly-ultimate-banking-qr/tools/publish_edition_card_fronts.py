#!/usr/bin/env python3
"""Publish generated card fronts from monopoly-edition-generator output to Resources."""

from __future__ import annotations

import argparse
import sys
from collections import Counter
from pathlib import Path

from asset_publication import build_publication_manifest, publish_edition_card_fronts, write_manifest
from workspace_paths import find_workspace_root, generator_output_dir


def parse_args(argv: list[str] | None = None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--edition", required=True, help="Edition id, for example india")
    parser.add_argument(
        "--card-type",
        choices=("event", "property", "all"),
        default="all",
        help="Which card fronts to publish.",
    )
    parser.add_argument("--dry-run", action="store_true", help="Report actions without writing files.")
    parser.add_argument(
        "--verify",
        action="store_true",
        help="Verify published targets match generated sources.",
    )
    parser.add_argument(
        "--overwrite",
        action="store_true",
        help="Replace an existing target when content differs.",
    )
    parser.add_argument(
        "--write-manifest-only",
        action="store_true",
        help="Only write output/<edition>/publication-manifest.json from current output.",
    )
    return parser.parse_args(argv)


def print_report(results) -> None:
    counts = Counter(entry.status for entry in results)
    print("Publication report:")
    for status in sorted(counts):
        print(f"  {status}: {counts[status]}")
    print()
    for entry in results:
        print(
            f"[{entry.status}] {entry.card_type} {entry.card_id} "
            f"seq={entry.sequence:02d} {entry.name}",
        )
        if entry.generated_source_path:
            print(f"  source: {entry.generated_source_path}")
        print(f"  target: {entry.published_target_path}")
        if entry.message:
            print(f"  note: {entry.message}")


def main(argv: list[str] | None = None) -> int:
    args = parse_args(argv)
    workspace_root = find_workspace_root(Path(__file__).resolve().parent)

    if args.write_manifest_only:
        entries = build_publication_manifest(workspace_root, args.edition)
        manifest_path = generator_output_dir(workspace_root, args.edition) / "publication-manifest.json"
        write_manifest(entries, manifest_path)
        print(f"Wrote manifest: {manifest_path}")
        print_report(entries)
        return 0

    results, exit_code = publish_edition_card_fronts(
        args.edition,
        card_type=args.card_type,
        dry_run=args.dry_run,
        verify=args.verify,
        overwrite=args.overwrite,
        workspace_root=workspace_root,
    )
    print_report(results)
    manifest_path = generator_output_dir(workspace_root, args.edition) / "publication-manifest.json"
    print(f"\nManifest: {manifest_path}")
    return exit_code


if __name__ == "__main__":
    sys.exit(main())
