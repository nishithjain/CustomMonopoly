"""Tests for edition card-front publication tools."""

from __future__ import annotations

import hashlib
import json
import sys
from pathlib import Path

import pytest

TOOLS_DIR = Path(__file__).resolve().parents[2] / "monopoly-ultimate-banking-qr" / "tools"
sys.path.insert(0, str(TOOLS_DIR))

from asset_publication import atomic_copy, find_generated_png, publish_edition_card_fronts  # noqa: E402


def _write_png(path: Path, color: tuple[int, int, int]) -> None:
    from PIL import Image

    path.parent.mkdir(parents=True, exist_ok=True)
    Image.new("RGB", (64, 96), color).save(path, format="PNG")


def _build_fixture_tree(tmp_path: Path) -> Path:
    workspace = tmp_path / "Monopoly"
    generator = workspace / "monopoly-edition-generator"
    project = workspace / "monopoly-ultimate-banking-qr"
    edition = "demo"

    event_png = generator / "output" / edition / "event_cards" / "png" / "E01_Test_Event.png"
    property_png = generator / "output" / edition / "property_cards" / "png" / "01_TEST_PROPERTY.png"
    _write_png(event_png, (255, 0, 0))
    _write_png(property_png, (0, 255, 0))

    events = {
        "events": [
            {
                "eventId": "EVT_01",
                "sequence": 1,
                "name": "Test Event",
                "frontAsset": f"Resources/Editions/{edition}/EventCards/Test_Event_Front.png",
            },
        ],
    }
    properties = {
        "properties": [
            {
                "propertyId": "PRP_01",
                "sequence": 1,
                "name": "Test Property",
                "frontAsset": f"Resources/Editions/{edition}/PropertyCards/01_Property_Front.png",
            },
        ],
    }
    edition_json = {
        "editionId": edition,
        "data": {"events": "events.json", "properties": "properties.json"},
        "artworkStatus": "FRONTS_READY",
    }
    data_dir = project / "data" / "editions" / edition
    data_dir.mkdir(parents=True)
    (data_dir / "events.json").write_text(json.dumps(events), encoding="utf-8")
    (data_dir / "properties.json").write_text(json.dumps(properties), encoding="utf-8")
    (data_dir / "edition.json").write_text(json.dumps(edition_json), encoding="utf-8")
    return workspace


def test_find_generated_png_rejects_ambiguous_matches(tmp_path: Path) -> None:
    folder = tmp_path / "event_cards" / "png"
    folder.mkdir(parents=True)
    (folder / "E01_A.png").write_bytes(b"a")
    (folder / "E01_B.png").write_bytes(b"b")
    with pytest.raises(RuntimeError, match="Ambiguous"):
        find_generated_png(tmp_path, "event", 1)


def test_publish_dry_run_makes_no_target_files(tmp_path: Path) -> None:
    workspace = _build_fixture_tree(tmp_path)
    results, exit_code = publish_edition_card_fronts(
        "demo",
        dry_run=True,
        workspace_root=workspace,
    )
    assert exit_code == 0
    assert len(results) == 2
    assert all(entry.status == "dry_run" for entry in results)
    target = workspace / "Resources/Editions/demo/EventCards/Test_Event_Front.png"
    assert not target.exists()


def test_publish_refuses_conflict_without_overwrite(tmp_path: Path) -> None:
    workspace = _build_fixture_tree(tmp_path)
    target = workspace / "Resources/Editions/demo/EventCards/Test_Event_Front.png"
    target.parent.mkdir(parents=True)
    target.write_bytes(b"existing")

    results, exit_code = publish_edition_card_fronts(
        "demo",
        workspace_root=workspace,
    )
    assert exit_code == 1
    assert any(entry.status == "conflict" for entry in results)


def test_atomic_copy_writes_byte_identical_file(tmp_path: Path) -> None:
    source = tmp_path / "source.png"
    destination = tmp_path / "nested" / "target.png"
    source.write_bytes(b"png-bytes")
    atomic_copy(source, destination)
    assert destination.read_bytes() == source.read_bytes()
    assert hashlib.sha256(destination.read_bytes()).hexdigest() == hashlib.sha256(source.read_bytes()).hexdigest()
