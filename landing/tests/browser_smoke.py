"""Real-browser checks for the local static export; this is not participant usability evidence.

Install Playwright in a temporary environment, provide a local Chromium executable if needed,
and run with webapp-testing's with_server.py or an existing local static server.
"""
import argparse
import json
from pathlib import Path

from playwright.sync_api import sync_playwright, expect


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--base-url", default="http://127.0.0.1:8088")
    parser.add_argument("--browser-executable")
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    args.output.mkdir(parents=True, exist_ok=True)
    report = []
    with sync_playwright() as playwright:
        launch = {"headless": True}
        if args.browser_executable:
            launch["executable_path"] = args.browser_executable
        browser = playwright.chromium.launch(**launch)
        try:
            for label, viewport in [("desktop", {"width": 1280, "height": 900}),
                                    ("mobile", {"width": 375, "height": 812})]:
                context = browser.new_context(viewport=viewport)
                page = context.new_page()
                errors = []
                page.on("pageerror", lambda error: errors.append(str(error)))
                response = page.goto(args.base_url)
                assert response.status == 200
                page.wait_for_load_state("networkidle")
                expect(page.get_by_role("heading", name="Pahami sinyal. Ketahui batasnya.")).to_be_visible()
                expect(page.get_by_text("Carda belum tersedia untuk unduhan publik.", exact=False)).to_be_visible()
                assert page.locator("html").get_attribute("lang") == "id"
                assert page.evaluate("document.documentElement.scrollWidth <= window.innerWidth")
                page.screenshot(path=str(args.output / f"home-{label}.png"), full_page=True)
                page.keyboard.press("Tab")
                focus = page.evaluate("({tag: document.activeElement.tagName, outline: getComputedStyle(document.activeElement).outlineStyle})")
                assert focus["tag"] == "A" and focus["outline"] != "none", focus
                page.get_by_role("navigation", name="Navigasi utama").get_by_role("link", name="Privasi", exact=True).click()
                page.wait_for_load_state("networkidle")
                expect(page.get_by_role("heading", name="Informasi privasi Carda", exact=True)).to_be_visible()
                expect(page.get_by_role("heading", name="Data kesehatan dan kamera", exact=True)).to_be_visible()
                assert page.evaluate("document.documentElement.scrollWidth <= window.innerWidth")
                page.screenshot(path=str(args.output / f"privacy-{label}.png"), full_page=True)
                page.get_by_role("link", name="Kembali ke beranda", exact=True).click()
                page.wait_for_load_state("networkidle")
                expect(page.get_by_role("heading", name="Pahami sinyal. Ketahui batasnya.")).to_be_visible()
                assert not errors, errors
                report.append({"viewport": label, "navigation": "passed", "horizontal_overflow": False,
                               "keyboard_focus_visible": True, "page_errors": len(errors)})
                context.close()
        finally:
            browser.close()
    (args.output / "browser-report.json").write_text(json.dumps(report, indent=2) + "\n")
    print(json.dumps(report))


if __name__ == "__main__":
    main()
