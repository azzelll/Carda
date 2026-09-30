"""Loopback identity portal smoke; synthetic accounts only, never a cloud/participant test.

Requires Playwright, a running disposable identity/PostgreSQL/fake-SMTP stack,
--base-url http://127.0.0.1:PORT and --mail-file containing the latest test mail.
Only aggregate checks are printed. Credentials, codes and tokens stay in memory.
"""
import argparse
import json
import re
import time
import uuid
from pathlib import Path
from urllib.error import HTTPError
from urllib.parse import urlparse
from urllib.request import Request, urlopen

from playwright.sync_api import expect, sync_playwright


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--base-url", required=True)
    parser.add_argument("--mail-file", required=True, type=Path)
    parser.add_argument("--chromium", required=True)
    parser.add_argument("--screenshots", type=Path)
    args = parser.parse_args()
    parsed = urlparse(args.base_url)
    if parsed.scheme != "http" or parsed.hostname not in ("127.0.0.1", "localhost", "::1"):
        parser.error("This smoke only accepts a disposable loopback HTTP server")
    base = args.base_url.rstrip("/")
    email = f"portal-{uuid.uuid4().hex}@carda.invalid"
    password = "synthetic-portal-password-123"
    checks = []

    def call(method, route, body, token=None):
        headers = {"Content-Type": "application/json"}
        if token:
            headers["Authorization"] = "Bearer " + token
        request = Request(base + "/api/v1/auth" + route, data=json.dumps(body).encode(), headers=headers, method=method)
        try:
            with urlopen(request, timeout=15) as response:
                payload = response.read(65537)
                assert len(payload) <= 65536, "API response exceeds test budget"
                return response.status, json.loads(payload) if payload else {}
        except HTTPError as error:
            return error.code, {}

    def login_form(page, address=email, secret=password):
        page.locator("#email").fill(address)
        page.locator("#password").fill(secret)
        page.get_by_role("button", name="Masuk untuk melanjutkan", exact=True).click()

    def confirm(page, secret=password):
        page.locator("#confirm-password").fill(secret)
        page.locator("#confirm-delete").check()
        page.get_by_role("button", name="Hapus akun daring secara permanen", exact=True).click()

    # Real disposable SQL/SMTP preparation. The source file is not printed or saved in reports.
    pattern = r"memverifikasi email Carda Anda:\s*(\S+)"
    def verification_codes():
        return re.findall(pattern, args.mail_file.read_text()) if args.mail_file.exists() else []

    before_codes = verification_codes()
    before = before_codes[-1] if before_codes else None
    assert call("POST", "/register", {"email": email, "password": password})[0] == 202
    deadline = time.monotonic() + 15
    while time.monotonic() < deadline:
        current = verification_codes()
        if current and current[-1] != before:
            break
        time.sleep(0.1)
    codes = verification_codes()
    assert codes and codes[-1] != before, "Verification code missing from fake SMTP after commit"
    assert call("POST", "/verify-email", {"token": codes[-1], "newPassword": password})[0] == 204
    removed = False
    try:
        with sync_playwright() as p:
            browser = p.chromium.launch(headless=True, executable_path=args.chromium)
            try:
                for viewport in ({"width": 1280, "height": 800}, {"width": 390, "height": 844}):
                    context = browser.new_context(viewport=viewport)
                    page = context.new_page()
                    errors = []
                    page.on("pageerror", lambda _: errors.append("uncaught browser error"))
                    response = page.goto(base + "/account-delete/index.html", wait_until="networkidle")
                    assert response.status == 200
                    assert "connect-src 'self'" in response.headers["content-security-policy"]
                    expect(page.get_by_role("heading", name="Hapus akun daring Carda", exact=True)).to_be_visible()
                    assert page.evaluate("document.documentElement.scrollWidth <= innerWidth")
                    if args.screenshots:
                        args.screenshots.mkdir(parents=True, exist_ok=True)
                        page.screenshot(path=str(args.screenshots / f"portal-{viewport['width']}.png"), full_page=True)
                    page.keyboard.press("Tab")
                    assert page.locator("#email").evaluate("element => element === document.activeElement")
                    login_form(page)
                    expect(page.locator("#delete-form")).to_be_visible()
                    assert page.locator("#password").input_value() == ""
                    assert page.evaluate("localStorage.length === 0 && sessionStorage.length === 0")
                    assert context.cookies() == []
                    # Login alone must never delete an account; explicit cancel revokes its real server session.
                    page.get_by_role("button", name="Batal dan keluar", exact=True).click()
                    expect(page.locator("#status")).to_have_text("Penghapusan dibatalkan. Sesi portal ditutup.")
                    expect(page.locator("#login-form")).to_be_visible()
                    assert errors == []
                    checks.append(f"real login/cancel, CSP, focus, storage, overflow: {viewport['width']}")
                    context.close()

                context = browser.new_context()
                page = context.new_page()
                page.goto(base + "/account-delete/index.html", wait_until="networkidle")
                login_form(page)
                expect(page.locator("#delete-form")).to_be_visible()
                confirm(page, "incorrect-synthetic-password")
                expect(page.locator("#login-form")).to_be_visible()
                expect(page.locator("#status")).to_contain_text("Masuk kembali")
                login_form(page)
                expect(page.locator("#delete-form")).to_be_visible()
                confirm(page)
                expect(page.locator("#status")).to_contain_text("Akun daring dan sesi server dihapus")
                assert page.locator("#login-form").is_hidden() and page.locator("#delete-form").is_hidden()
                assert call("POST", "/login", {"email": email, "password": password})[0] == 401
                removed = True
                checks.append("real wrong-password rejection, explicit deletion, old login rejected")
                context.close()

                # Software edge cases below use intercepted identity responses, not real database outcomes.
                fixture = {"accountId": "11111111-1111-4111-8111-111111111111", "accessToken": "synthetic-access",
                           "refreshToken": "synthetic-refresh", "expiresInSeconds": 900}
                for label, body in (("malformed session", {**fixture, "accountId": "invalid"}),
                                    ("oversized session", {**fixture, "padding": "x" * 65537})):
                    context = browser.new_context()
                    page = context.new_page()
                    page.route("**/api/v1/auth/login", lambda route: route.fulfill(status=200, json=body))
                    page.goto(base + "/account-delete/index.html", wait_until="networkidle")
                    login_form(page)
                    expect(page.locator("#status")).to_contain_text("Login belum berhasil")
                    assert page.locator("#delete-form").is_hidden()
                    checks.append(f"mock {label} rejected")
                    context.close()

                context = browser.new_context()
                page = context.new_page()
                deletes = []
                page.route("**/api/v1/auth/login", lambda route: route.fulfill(status=200, json={**fixture, "expiresInSeconds": 1}))
                page.route("**/api/v1/auth/account", lambda route: (deletes.append(1), route.fulfill(status=204)))
                page.goto(base + "/account-delete/index.html", wait_until="networkidle")
                login_form(page)
                expect(page.locator("#delete-form")).to_be_visible()
                page.evaluate("expiresAt = Date.now() - 1")
                confirm(page)
                expect(page.locator("#status")).to_contain_text("Sesi telah berakhir")
                assert deletes == []
                checks.append("mock expired access blocks deletion")
                context.close()

                context = browser.new_context()
                page = context.new_page()
                pending = []
                page.route("**/api/v1/auth/login", lambda route: pending.append(route))
                page.goto(base + "/account-delete/index.html", wait_until="networkidle")
                login_form(page)
                expect(page.locator("#status")).to_have_text("Memproses login…")
                page.wait_for_function("busy")
                page.evaluate("window.dispatchEvent(new PageTransitionEvent('pagehide'))")
                assert page.locator("#password").input_value() == ""
                for route in pending:
                    try:
                        route.fulfill(status=200, json=fixture)
                    except Exception:
                        pass  # The aborted request need not remain fulfillable.
                expect(page.locator("#status")).to_contain_text("Sesi portal ditutup")
                assert page.locator("#delete-form").is_hidden()
                checks.append("mock interrupted login cannot reopen confirmation")
                context.close()
            finally:
                browser.close()
    finally:
        if not removed:
            status, session = call("POST", "/login", {"email": email, "password": password})
            if status == 200:
                assert call("DELETE", "/account", {"password": password}, session["accessToken"])[0] == 204
    print(json.dumps({"scope": "disposable loopback SQL/fake-SMTP plus clearly separated mocked edge cases",
                      "checks": checks, "passed": len(checks)}, indent=2))


if __name__ == "__main__":
    main()
