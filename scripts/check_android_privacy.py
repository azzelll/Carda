#!/usr/bin/env python3
"""Fail a release build when its merged manifest weakens Carda's privacy boundary."""

import os
from pathlib import Path
import shutil
import subprocess
import sys
import xml.etree.ElementTree as ET


ROOT = Path(__file__).resolve().parents[1]
ANDROID = "{http://schemas.android.com/apk/res/android}"
ALLOWED_PERMISSIONS = {
    "android.permission.CAMERA",
    "android.permission.INTERNET",  # Identity only; inspect runtime traffic separately.
    "android.permission.POST_NOTIFICATIONS",
    "android.permission.RECEIVE_BOOT_COMPLETED",
    "android.permission.WAKE_LOCK",  # WorkManager
    "android.permission.ACCESS_NETWORK_STATE",  # WorkManager
    "android.permission.FOREGROUND_SERVICE",  # WorkManager manifest merger
    "id.carda.app.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION",  # AndroidX
}


def require(condition: bool, message: str) -> None:
    if not condition:
        raise ValueError(message)


def find_apkanalyzer() -> str:
    from_path = shutil.which("apkanalyzer")
    if from_path:
        return from_path
    sdk = os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT")
    if sdk:
        path = Path(sdk) / "cmdline-tools/latest/bin/apkanalyzer"
        if path.is_file():
            return str(path)
    raise ValueError("Android SDK apkanalyzer is required for merged-APK inspection")


def inspect_manifest(apk: Path) -> None:
    output = subprocess.run([find_apkanalyzer(), "manifest", "print", str(apk)],
                            check=True, capture_output=True, text=True).stdout
    manifest = ET.fromstring(output)
    require(manifest.get("package") == "id.carda.app", "Unexpected application ID")
    application = manifest.find("application")
    require(application is not None, "Missing application element")
    require(application.get(ANDROID + "allowBackup") == "false", "Release backup must be disabled")
    require(application.get(ANDROID + "debuggable") != "true", "Release must not be debuggable")
    require(application.get(ANDROID + "usesCleartextTraffic") != "true", "Release cleartext traffic is forbidden")
    require(application.get(ANDROID + "fullBackupContent") is not None, "Legacy backup rules missing")
    require(application.get(ANDROID + "dataExtractionRules") is not None, "Transfer rules missing")
    permissions = {element.get(ANDROID + "name") for element in manifest.findall("uses-permission")}
    require(permissions <= ALLOWED_PERMISSIONS,
            "Unexpected merged permissions: " + ", ".join(sorted(permissions - ALLOWED_PERMISSIONS)))
    for element in application.iter():
        if element.tag not in {"activity", "service", "receiver", "provider"}:
            continue
        name = element.get(ANDROID + "name", "")
        if name.startswith("id.carda.app.") and element.get(ANDROID + "exported") == "true":
            require(name in {"id.carda.app.MainActivity", "id.carda.app.ReminderClockChangeReceiver"},
                    f"Unexpected exported Carda component: {name}")


def inspect_backup_rules() -> None:
    xml = ROOT / "app/src/main/res/xml"
    required = {"database", "sharedpref", "file"}
    legacy = ET.parse(xml / "backup_rules.xml").getroot()
    require(not legacy.findall("include"), "Legacy backup must have no included paths")
    require({element.get("domain") for element in legacy.findall("exclude")} >= required,
            "Legacy backup exclusions incomplete")
    extraction = ET.parse(xml / "data_extraction_rules.xml").getroot()
    for section in ("cloud-backup", "device-transfer"):
        node = extraction.find(section)
        require(node is not None and not node.findall("include"), f"{section} must have no includes")
        require({element.get("domain") for element in node.findall("exclude")} >= required,
                f"{section} exclusions incomplete")


def main() -> int:
    try:
        apk = Path(sys.argv[1]).resolve() if len(sys.argv) == 2 else ROOT / "app/build/outputs/apk/release/app-release-unsigned.apk"
        require(apk.is_file(), f"Release APK missing: {apk}")
        inspect_manifest(apk)
        inspect_backup_rules()
        print("PASS: release merged manifest, permissions and backup rules meet static Carda privacy checks")
        print("Scope: static APK/source check only; logcat, network, provider and device-transfer behavior require runtime audit")
        return 0
    except (ValueError, ET.ParseError, subprocess.CalledProcessError) as error:
        print(f"FAIL: {error}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
