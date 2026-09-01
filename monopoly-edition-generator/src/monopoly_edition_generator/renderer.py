"""HTML to PNG rendering using Playwright."""

from __future__ import annotations

import asyncio
import math
import tempfile
from pathlib import Path

from monopoly_edition_generator.paths import CM_PER_INCH, OUTER_BOARD_SIZE_CM

CARD_WIDTH_MM = 108.0
CARD_HEIGHT_MM = 172.0
CSS_PX_PER_INCH = 96.0
POST_RENDER_WAIT_MS = 800
MAX_DEVICE_SCALE = 8.0

INSTALL_HINT = """ERROR: Playwright is not installed.

Install with:
    pip install playwright pillow
    playwright install chromium
"""

CHROMIUM_HINT = """ERROR: Playwright Chromium is not installed.

Install with:
    playwright install chromium
"""

PILLOW_HINT = """ERROR: Pillow is not installed.

Install with:
    pip install pillow
"""


def mm_to_cm(mm: float) -> float:
    return mm / 10.0


def target_pixels_for_cm(cm: float, dpi: float) -> int:
    return round((cm / CM_PER_INCH) * dpi)


def css_pixels_for_cm(cm: float) -> float:
    return (cm / CM_PER_INCH) * CSS_PX_PER_INCH


def require_pillow():
    try:
        from PIL import Image
    except ImportError:
        print(PILLOW_HINT)
        raise SystemExit(1) from None
    return Image


def require_playwright():
    try:
        from playwright.async_api import Error as PlaywrightError
        from playwright.async_api import async_playwright
    except ImportError:
        print(INSTALL_HINT)
        raise SystemExit(1) from None
    return async_playwright, PlaywrightError


def device_scale_for_target(target: int, css_px: float) -> float:
    if css_px <= 0:
        raise ValueError("Invalid CSS size.")
    scale = (target / css_px) * 1.08
    scale = max(scale, target / css_px)
    return min(MAX_DEVICE_SCALE, scale)


async def wait_for_board(page) -> None:
    await page.wait_for_function(
        """
        () => {
            const board = document.querySelector("#monopoly-board");
            if (!board) {
                return false;
            }
            return board.querySelectorAll(".board-space").length >= 32
                && board.querySelectorAll(".board-corner").length >= 4;
        }
        """
    )

    result = await page.evaluate(
        """
        async () => {
            const board = document.querySelector("#monopoly-board");
            if (!board) {
                return { boardExists: false, failed: [] };
            }

            if (document.fonts && document.fonts.ready) {
                await document.fonts.ready;
            }

            await Promise.all(
                Array.from(document.images)
                    .filter(img => !img.complete)
                    .map(img => new Promise(resolve => {
                        img.onload = img.onerror = resolve;
                    }))
            );

            const failed = [];
            for (const img of document.images) {
                if (!img.complete || img.naturalWidth <= 0) {
                    failed.push(img.currentSrc || img.getAttribute("src") || "(unknown img)");
                }
            }

            const backgroundUrls = new Set();
            const nodes = [board, ...board.querySelectorAll("*")];
            for (const node of nodes) {
                const bg = getComputedStyle(node).backgroundImage;
                if (!bg || bg === "none") {
                    continue;
                }
                for (const match of bg.matchAll(/url\\((['"]?)(.*?)\\1\\)/g)) {
                    const url = match[2];
                    if (url && !url.startsWith("data:")) {
                        backgroundUrls.add(url);
                    }
                }
            }

            await Promise.all(
                Array.from(backgroundUrls).map(url => new Promise(resolve => {
                    const image = new Image();
                    image.onload = () => {
                        if (image.naturalWidth <= 0) {
                            failed.push(url);
                        }
                        resolve();
                    };
                    image.onerror = () => {
                        failed.push(url);
                        resolve();
                    };
                    image.src = url;
                }))
            );

            return { boardExists: true, failed };
        }
        """
    )

    if not result or not result.get("boardExists"):
        raise RuntimeError("Could not find #monopoly-board")

    failed = result.get("failed") or []
    if failed:
        details = "\n".join(f"  {src}" for src in failed)
        raise RuntimeError("One or more local images failed to load:\n" + details)


async def wait_for_card(page, selector: str) -> None:
    await page.wait_for_selector(selector)
    await page.evaluate(
        """
        async () => {
            if (document.fonts && document.fonts.ready) {
                await document.fonts.ready;
            }
        }
        """
    )


async def _launch_browser(async_playwright, PlaywrightError):
    playwright = await async_playwright().start()
    try:
        browser = await playwright.chromium.launch(
            headless=True,
            args=[
                "--allow-file-access-from-files",
                "--hide-scrollbars",
                "--disable-lcd-text",
            ],
        )
    except PlaywrightError as exc:
        await playwright.stop()
        message = str(exc)
        if "Executable doesn't exist" in message or "chromium" in message.lower():
            print(CHROMIUM_HINT)
            raise SystemExit(1) from None
        raise
    return playwright, browser


async def _screenshot_element(
    page,
    selector: str,
    output_path: Path,
    target_w: int,
    target_h: int,
    dpi: float,
    Image,
    square_crop: bool,
) -> None:
    element = page.locator(selector).first
    await element.scroll_into_view_if_needed()
    await page.wait_for_timeout(POST_RENDER_WAIT_MS)

    with tempfile.NamedTemporaryFile(suffix=".png", delete=False) as tmp:
        tmp_path = Path(tmp.name)

    try:
        await element.screenshot(
            path=str(tmp_path),
            type="png",
            omit_background=False,
            animations="disabled",
            caret="hide",
        )
        with Image.open(tmp_path) as captured:
            captured = captured.convert("RGBA")
            width, height = captured.size
            if square_crop:
                side = min(width, height)
                if width != height:
                    left = (width - side) // 2
                    top = (height - side) // 2
                    captured = captured.crop((left, top, left + side, top + side))
            if captured.size != (target_w, target_h):
                captured = captured.resize(
                    (target_w, target_h),
                    resample=Image.Resampling.LANCZOS,
                )
            output_path.parent.mkdir(parents=True, exist_ok=True)
            captured.save(output_path, format="PNG", dpi=(dpi, dpi))
    finally:
        tmp_path.unlink(missing_ok=True)


async def capture_board_png(html_path: Path, output_path: Path, dpi: float = 300) -> Path:
    Image = require_pillow()
    async_playwright, PlaywrightError = require_playwright()
    target = target_pixels_for_cm(OUTER_BOARD_SIZE_CM, dpi)
    css_px = css_pixels_for_cm(OUTER_BOARD_SIZE_CM)
    scale = device_scale_for_target(target, css_px)
    viewport = int(math.ceil(css_px) + 520)
    html_uri = html_path.resolve().as_uri()
    output_path = output_path.resolve()

    print(f"Loading:\n{html_path.resolve()}")
    print()
    print(f"Board size:\n{OUTER_BOARD_SIZE_CM:g} cm × {OUTER_BOARD_SIZE_CM:g} cm")
    print()
    print(f"Target DPI:\n{dpi:g}")
    print()
    print(f"Target resolution:\n{target} × {target}")
    print()

    playwright, browser = await _launch_browser(async_playwright, PlaywrightError)
    try:
        context = await browser.new_context(
            viewport={"width": viewport, "height": viewport},
            device_scale_factor=scale,
            color_scheme="light",
            reduced_motion="reduce",
        )
        page = await context.new_page()
        await page.emulate_media(media="screen")
        page.set_default_timeout(120_000)

        print("Waiting for board rendering...")
        await page.goto(html_uri, wait_until="networkidle")
        await page.wait_for_load_state("networkidle")

        exists = await page.evaluate("() => !!document.querySelector('#monopoly-board')")
        if not exists:
            raise RuntimeError("Could not find #monopoly-board")

        await wait_for_board(page)
        await page.add_style_tag(
            content="""
            html, body { overflow: hidden !important; }
            .board-preview { overflow: visible !important; padding: 0 !important; }
            .print-note, .no-print { display: none !important; }
            #monopoly-board { box-shadow: none !important; margin: 0 !important; }
            """
        )
        print()
        print("Capturing #monopoly-board...")
        await _screenshot_element(
            page,
            "#monopoly-board",
            output_path,
            target,
            target,
            dpi,
            Image,
            square_crop=True,
        )
    finally:
        await browser.close()
        await playwright.stop()

    with Image.open(output_path) as verified:
        actual = verified.size
    if actual != (target, target):
        raise RuntimeError(
            f"Final PNG dimensions are {actual[0]} × {actual[1]}, expected {target} × {target}."
        )

    print()
    print("Created:")
    print(str(output_path))
    print()
    print(f"Verified PNG size: {actual[0]} × {actual[1]} at {dpi:g} DPI")
    return output_path


async def capture_cards_png(
    jobs: list[tuple[Path, Path]],
    selector: str,
    width_mm: float = CARD_WIDTH_MM,
    height_mm: float = CARD_HEIGHT_MM,
    dpi: float = 300,
    on_progress=None,
) -> list[Path]:
    if not jobs:
        return []

    Image = require_pillow()
    async_playwright, PlaywrightError = require_playwright()
    width_cm = mm_to_cm(width_mm)
    height_cm = mm_to_cm(height_mm)
    target_w = target_pixels_for_cm(width_cm, dpi)
    target_h = target_pixels_for_cm(height_cm, dpi)
    css_w = css_pixels_for_cm(width_cm)
    css_h = css_pixels_for_cm(height_cm)
    scale = device_scale_for_target(max(target_w, target_h), max(css_w, css_h))
    viewport_w = int(math.ceil(css_w) + 240)
    viewport_h = int(math.ceil(css_h) + 240)

    playwright, browser = await _launch_browser(async_playwright, PlaywrightError)
    created: list[Path] = []
    try:
        context = await browser.new_context(
            viewport={"width": viewport_w, "height": viewport_h},
            device_scale_factor=scale,
            color_scheme="light",
            reduced_motion="reduce",
        )
        page = await context.new_page()
        await page.emulate_media(media="screen")
        page.set_default_timeout(120_000)

        for html_path, output_path in jobs:
            await page.goto(html_path.resolve().as_uri(), wait_until="networkidle")
            await wait_for_card(page, selector)
            await page.add_style_tag(
                content="""
                html, body {
                    overflow: hidden !important;
                    background: white !important;
                    padding: 0 !important;
                    margin: 0 !important;
                    display: flex !important;
                    justify-content: center !important;
                    align-items: center !important;
                    min-height: 0 !important;
                }
                .card-wrapper { padding: 0 !important; }
                """
            )
            await _screenshot_element(
                page,
                selector,
                output_path.resolve(),
                target_w,
                target_h,
                dpi,
                Image,
                square_crop=False,
            )
            created.append(output_path)
            print(f"Created: {output_path}")
            if on_progress:
                on_progress(len(created), len(jobs), output_path)
    finally:
        await browser.close()
        await playwright.stop()
    return created


def render_board_png(html_path: Path, output_path: Path, dpi: float = 300) -> Path:
    return asyncio.run(capture_board_png(html_path, output_path, dpi))


def render_card_pngs(
    jobs: list[tuple[Path, Path]],
    selector: str,
    *,
    width_mm: float = CARD_WIDTH_MM,
    height_mm: float = CARD_HEIGHT_MM,
    dpi: float = 300,
    on_progress=None,
) -> list[Path]:
    return asyncio.run(
        capture_cards_png(
            jobs,
            selector,
            width_mm=width_mm,
            height_mm=height_mm,
            dpi=dpi,
            on_progress=on_progress,
        )
    )
