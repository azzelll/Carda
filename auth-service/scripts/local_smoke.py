import json
import os
import re
import secrets
import time
from pathlib import Path
from urllib.error import HTTPError
from urllib.request import Request, urlopen

base = os.environ.get("CARDA_SMOKE_BASE_URL", "http://127.0.0.1:18081").rstrip("/") + "/api/v1/auth"
email = f"smoke-{secrets.token_hex(6)}@carda.invalid"
password = "local-smoke-password-123"
new_password = "local-smoke-password-456"
mail = Path(os.environ.get("CARDA_SMOKE_MAIL_FILE", "/private/tmp/carda-auth-mail.txt"))

def call(method, path, body=None, token=None):
    payload = json.dumps(body or {}).encode()
    headers = {"Content-Type": "application/json"}
    if token:
        headers["Authorization"] = "Bearer " + token
    request = Request(base + path, data=payload, headers=headers, method=method)
    try:
        with urlopen(request, timeout=10) as response:
            content = response.read()
            return response.status, json.loads(content) if content else {}
    except HTTPError as error:
        content = error.read()
        return error.code, json.loads(content) if content else {}

def mail_tokens(phrase):
    text = mail.read_text(errors="replace") if mail.exists() else ""
    return re.findall(re.escape(phrase) + r"\s*(\S+)", text)

def latest_mail_token(phrase):
    tokens = mail_tokens(phrase)
    return tokens[-1] if tokens else None

def mailed_token(phrase, previous_token):
    deadline = time.monotonic() + 15
    while time.monotonic() < deadline:
        latest = latest_mail_token(phrase)
        if latest is not None and latest != previous_token:
            return latest
        time.sleep(0.1)
    raise AssertionError("test SMTP message did not arrive after committed token")

checks = []
verify_phrase = "memverifikasi email Carda Anda:"
reset_phrase = "mengatur ulang kata sandi Carda Anda:"
mail_before = latest_mail_token(verify_phrase)
checks.append(("register", call("POST", "/register", {"email": email, "password": password})[0]))
checks.append(("login before verification", call("POST", "/login", {"email": email, "password": password})[0]))
verify = mailed_token(verify_phrase, mail_before)
checks.append(("verify", call("POST", "/verify-email", {"token": verify, "newPassword": password})[0]))
status, session = call("POST", "/login", {"email": email, "password": password})
checks.append(("login", status))
assert session.get("accountId") and session.get("accessToken") and session.get("refreshToken")
status, rotated = call("POST", "/refresh", {"refreshToken": session["refreshToken"]})
checks.append(("refresh", status))
assert rotated.get("accessToken") != session["accessToken"]
checks.append(("old refresh rejected", call("POST", "/refresh", {"refreshToken": session["refreshToken"]})[0]))
mail_before = latest_mail_token(reset_phrase)
checks.append(("reset request", call("POST", "/password-reset/request", {"email": email})[0]))
reset = mailed_token(reset_phrase, mail_before)
checks.append(("reset confirm", call("POST", "/password-reset/confirm", {"token": reset, "newPassword": new_password})[0]))
checks.append(("old password rejected", call("POST", "/login", {"email": email, "password": password})[0]))
status, current = call("POST", "/login", {"email": email, "password": new_password})
checks.append(("new password login", status))
checks.append(("delete account", call("DELETE", "/account", {"password": new_password}, current["accessToken"])[0]))
checks.append(("deleted account login rejected", call("POST", "/login", {"email": email, "password": new_password})[0]))

# Pre-registration regression: a third party can request a code, but cannot choose the
# final password merely by registering first. Only redemption of the latest emailed code
# with a recipient-chosen password verifies the account.
victim_email = f"smoke-pre-register-{secrets.token_hex(6)}@carda.invalid"
attacker_password = "synthetic-attacker-password-123"
recipient_password = "synthetic-recipient-password-456"
mail_before = latest_mail_token(verify_phrase)
checks.append(("pre-register victim email", call("POST", "/register", {
    "email": victim_email, "password": attacker_password,
})[0]))
old_verify = mailed_token(verify_phrase, mail_before)
mail_before = latest_mail_token(verify_phrase)
checks.append(("recipient re-registers", call("POST", "/register", {
    "email": victim_email, "password": recipient_password,
})[0]))
latest_verify = mailed_token(verify_phrase, mail_before)
assert old_verify != latest_verify
checks.append(("superseded code rejected", call("POST", "/verify-email", {
    "token": old_verify, "newPassword": recipient_password,
})[0]))
checks.append(("wrong code rejected", call("POST", "/verify-email", {
    "token": "synthetic-wrong-verification-code", "newPassword": recipient_password,
})[0]))
checks.append(("latest code verifies", call("POST", "/verify-email", {
    "token": latest_verify, "newPassword": recipient_password,
})[0]))
checks.append(("attacker password rejected", call("POST", "/login", {
    "email": victim_email, "password": attacker_password,
})[0]))
status, recipient_session = call("POST", "/login", {
    "email": victim_email, "password": recipient_password,
})
checks.append(("recipient password login", status))
checks.append(("replayed code rejected", call("POST", "/verify-email", {
    "token": latest_verify, "newPassword": attacker_password,
})[0]))
checks.append(("pre-registration fixture deleted", call("DELETE", "/account", {
    "password": recipient_password,
}, recipient_session["accessToken"])[0]))

expected = [202, 401, 204, 200, 200, 401, 202, 204, 401, 200, 204, 401,
            202, 202, 400, 400, 204, 401, 200, 400, 204]
for (name, observed), wanted in zip(checks, expected):
    print(f"{name}: {observed} expected {wanted}")
assert [status for _, status in checks] == expected
