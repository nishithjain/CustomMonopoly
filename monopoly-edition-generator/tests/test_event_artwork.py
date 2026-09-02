"""Tests for event-card artwork mapping and Base64 embedding."""

from __future__ import annotations

import asyncio
import re
from pathlib import Path

import pytest

from monopoly_edition_generator.event_artwork import (
    MIN_SIZE,
    RECOMMENDED_SIZE,
    build_artwork_block,
    map_edition_artwork,
)
from monopoly_edition_generator.generators.event_cards import generate_event_cards
from monopoly_edition_generator.paths import GeneratorError, load_edition_json

pytest.importorskip("playwright")

DATA_URI_PATTERN = re.compile(r'src="data:image/(?:png|jpeg|webp);base64,')
SMOKE_EVENT_IDS = ("EVT_01", "EVT_10", "EVT_19", "EVT_24", "EVT_25")


def _india_events() -> list[dict]:
    return load_edition_json("india", "events.json")["events"]


def test_india_artwork_mapping_covers_all_configured_events() -> None:
    records = map_edition_artwork("india", _india_events())
    configured = [event for event in _india_events() if str(event.get("artworkAsset") or "").strip()]
    assert len(records) == 25
    assert len(configured) == 25
    assert {record.event_id for record in records} == {event["eventId"] for event in configured}


def test_india_artwork_mapping_table_is_square_and_supported() -> None:
    records = map_edition_artwork("india", _india_events())
    for record in records:
        assert record.width == record.height
        assert record.width >= MIN_SIZE
        assert record.format_name in {"PNG", "JPEG", "WEBP"}
        assert record.filename.startswith(f"{record.event_id}_")
        assert record.asset_ref.startswith("assets/cards/editions/india/event-artwork/")
        assert "\\" not in record.asset_ref


def test_build_artwork_block_embeds_base64_data_uri() -> None:
    event = next(event for event in _india_events() if event["eventId"] == "EVT_01")
    block = build_artwork_block(event, "india")
    assert 'class="event-artwork"' in block
    assert DATA_URI_PATTERN.search(block)
    assert 'alt="Advance to GO event artwork"' in block
    assert "C:\\Personal\\" not in block
    assert "file:///" not in block


def test_missing_artwork_fails_before_generation(tmp_path, monkeypatch) -> None:
    from monopoly_edition_generator import paths as paths_module

    monkeypatch.setattr(paths_module, "OUTPUT_ROOT", tmp_path)
    monkeypatch.setattr(
        paths_module,
        "edition_output_dir",
        lambda edition_id: tmp_path / edition_id,
    )

    events = _india_events()
    broken = dict(events[0])
    broken["artworkAsset"] = "assets/cards/editions/india/event-artwork/EVT_99_MISSING.png"

    with pytest.raises(GeneratorError, match="EVT_01"):
        generate_event_cards("india", events=[broken])


def test_generated_india_html_embeds_artwork_without_external_paths(tmp_path, monkeypatch) -> None:
    from monopoly_edition_generator import paths as paths_module

    monkeypatch.setattr(paths_module, "OUTPUT_ROOT", tmp_path)
    monkeypatch.setattr(
        paths_module,
        "edition_output_dir",
        lambda edition_id: tmp_path / edition_id,
    )

    generated = generate_event_cards("india")
    assert len(generated) == 25

    for path in generated:
        html = path.read_text(encoding="utf-8")
        assert html.count('class="event-artwork"') == 1, path.name
        assert DATA_URI_PATTERN.search(html), path.name
        assert "C:\\Personal\\" not in html, path.name
        assert "file:///" not in html, path.name
        assert "../../assets" not in html, path.name
        assert 'aria-hidden="true"' not in html, path.name


async def _natural_artwork_sizes(html_paths: list[Path]) -> list[dict]:
    from playwright.async_api import async_playwright

    results: list[dict] = []
    async with async_playwright() as playwright:
        browser = await playwright.chromium.launch(headless=True)
        context = await browser.new_context(viewport={"width": 1200, "height": 1600})
        page = await context.new_page()
        for html_path in html_paths:
            await page.goto(html_path.resolve().as_uri(), wait_until="networkidle")
            await page.evaluate(
                """
                async () => {
                    if (document.fonts && document.fonts.ready) {
                        await document.fonts.ready;
                    }
                    const images = [...document.images];
                    await Promise.all(
                        images.map(image =>
                            image.complete
                                ? image.decode()
                                : new Promise((resolve, reject) => {
                                    image.addEventListener("load", resolve, { once: true });
                                    image.addEventListener("error", reject, { once: true });
                                })
                        )
                    );
                }
                """
            )
            metrics = await page.evaluate(
                """
                () => {
                    const image = document.querySelector('.event-artwork');
                    const viewport = document.querySelector('.event-image');
                    return {
                        naturalWidth: image ? image.naturalWidth : 0,
                        naturalHeight: image ? image.naturalHeight : 0,
                        viewportWidth: viewport ? viewport.clientWidth : 0,
                        viewportHeight: viewport ? viewport.clientHeight : 0,
                        srcPrefix: image ? image.currentSrc.slice(0, 24) : '',
                    };
                }
                """
            )
            results.append({"path": html_path, **metrics})
        await browser.close()
    return results


def test_visual_smoke_test_for_selected_india_event_artwork(tmp_path, monkeypatch) -> None:
    from monopoly_edition_generator import paths as paths_module

    monkeypatch.setattr(paths_module, "OUTPUT_ROOT", tmp_path)
    monkeypatch.setattr(
        paths_module,
        "edition_output_dir",
        lambda edition_id: tmp_path / edition_id,
    )

    generated = generate_event_cards("india")
    selected = [
        next(path for path in generated if path.name.startswith(f"E{int(event_id.split('_')[1]):02d}_"))
        for event_id in SMOKE_EVENT_IDS
    ]
    metrics = asyncio.run(_natural_artwork_sizes(selected))
    assert len(metrics) == len(SMOKE_EVENT_IDS)

    for item in metrics:
        assert item["naturalWidth"] == item["naturalHeight"] > 0, item["path"].name
        assert item["viewportWidth"] == item["viewportHeight"] > 0, item["path"].name
        assert item["srcPrefix"].startswith("data:image/"), item["path"].name


def test_india_artwork_size_warnings_are_reported_not_fatal() -> None:
    records = map_edition_artwork("india", _india_events())
    warned = [record for record in records if record.warnings]
    assert len(warned) == 25
    assert all(str(RECOMMENDED_SIZE) in warning for record in warned for warning in record.warnings)
