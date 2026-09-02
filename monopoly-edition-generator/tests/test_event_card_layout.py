"""Layout validation for generated event-card HTML."""

from __future__ import annotations

import asyncio
import math
from pathlib import Path

import pytest

from monopoly_edition_generator.generators.event_cards import generate_event_cards
from monopoly_edition_generator.paths import edition_output_dir

pytest.importorskip("playwright")

MM_TOLERANCE = 0.75
ARTWORK_MM = 90.0
CARD_WIDTH_MM = 108.0
CARD_HEIGHT_MM = 172.0
DESCRIPTION_MM = 22.0


def _mm_to_css_px(mm: float) -> float:
    return (mm / 25.4) * 96.0


async def _measure_cards(html_paths: list[Path]) -> list[dict]:
    from playwright.async_api import async_playwright

    results: list[dict] = []
    async with async_playwright() as playwright:
        browser = await playwright.chromium.launch(headless=True)
        context = await browser.new_context(viewport={"width": 1200, "height": 1600})
        page = await context.new_page()
        for html_path in html_paths:
            await page.goto(html_path.resolve().as_uri(), wait_until="networkidle")
            metrics = await page.evaluate(
                """
                () => {
                    const card = document.querySelector('.event-card');
                    const image = document.querySelector('.event-image');
                    const description = document.querySelector('.event-description');
                    const artwork = document.querySelector('.event-artwork');
                    const rect = (el) => {
                        if (!el) return null;
                        const box = el.getBoundingClientRect();
                        return {
                            width: box.width,
                            height: box.height,
                            scrollHeight: el.scrollHeight,
                            clientHeight: el.clientHeight,
                        };
                    };
                    return {
                        card: rect(card),
                        image: rect(image),
                        description: rect(description),
                        text: description ? description.textContent : '',
                        artworkSrc: artwork ? artwork.currentSrc : '',
                        artworkNaturalWidth: artwork ? artwork.naturalWidth : 0,
                        artworkNaturalHeight: artwork ? artwork.naturalHeight : 0,
                    };
                }
                """
            )
            results.append({"path": html_path, **metrics})
        await browser.close()
    return results


def test_india_event_card_layout_is_consistent(tmp_path, monkeypatch) -> None:
    from monopoly_edition_generator import paths as paths_module

    monkeypatch.setattr(paths_module, "OUTPUT_ROOT", tmp_path)
    monkeypatch.setattr(paths_module, "edition_output_dir", lambda edition_id: tmp_path / edition_id)

    generated = generate_event_cards("india")
    assert len(generated) == 25

    metrics = asyncio.run(_measure_cards(generated))
    assert len(metrics) == 25

    artwork_sizes: list[tuple[float, float]] = []
    card_w = _mm_to_css_px(CARD_WIDTH_MM)
    card_h = _mm_to_css_px(CARD_HEIGHT_MM)
    artwork_px = _mm_to_css_px(ARTWORK_MM)
    description_px = _mm_to_css_px(DESCRIPTION_MM)

    for item in metrics:
        name = item["path"].name
        text = str(item.get("text") or "")
        assert "Recommended rules" not in text, name
        assert "Recommended resolution" not in text, name
        assert "SKIP_NEXT_TURN" not in text, name
        assert str(item.get("artworkSrc") or "").startswith("data:image/"), name
        assert item.get("artworkNaturalWidth") == item.get("artworkNaturalHeight") > 0, name

        image = item["image"]
        description = item["description"]
        card = item["card"]
        assert image is not None, name
        assert description is not None, name
        assert card is not None, name

        assert image["scrollHeight"] == image["clientHeight"], name
        assert description["scrollHeight"] <= description["clientHeight"] + 1, name

        assert math.isclose(image["width"], artwork_px, abs_tol=2.0), name
        assert math.isclose(image["height"], artwork_px, abs_tol=2.0), name
        assert math.isclose(description["height"], description_px, abs_tol=2.0), name
        assert math.isclose(card["width"], card_w, abs_tol=2.0), name
        assert math.isclose(card["height"], card_h, abs_tol=2.0), name

        artwork_sizes.append((image["width"], image["height"]))

    assert len({(round(w, 1), round(h, 1)) for w, h in artwork_sizes}) == 1


def test_second_wind_and_green_energy_rebate_share_artwork_size(tmp_path, monkeypatch) -> None:
    from monopoly_edition_generator import paths as paths_module

    monkeypatch.setattr(paths_module, "OUTPUT_ROOT", tmp_path)
    monkeypatch.setattr(paths_module, "edition_output_dir", lambda edition_id: tmp_path / edition_id)

    generated = generate_event_cards("india")
    paths = {
        "second_wind": next(path for path in generated if path.name.startswith("E24_")),
        "green_energy": next(path for path in generated if path.name.startswith("E25_")),
    }
    metrics = asyncio.run(_measure_cards(list(paths.values())))
    second = metrics[0]["image"]
    green = metrics[1]["image"]
    assert second["width"] == green["width"]
    assert second["height"] == green["height"]
