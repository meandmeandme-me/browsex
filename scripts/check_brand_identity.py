#!/usr/bin/env python3
"""Reject retired product branding and verify Wren's Android identity."""

from pathlib import Path
import re
import sys
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]

# Build the retired marker from fragments so this guard does not match itself.
retired_brand = re.compile(
    "browse" + r"[\s._-]*" + "x|browse" + r"\s+" + "x",
    re.IGNORECASE,
)
retired_secret_prefix = "BROWSE" + "X_"
retired_package = "com." + "browse" + "x"

SKIP_DIRS = {".git", ".gradle", ".idea", "build", "out", "node_modules"}
TEXT_SUFFIXES = {
    ".kt", ".kts", ".java", ".xml", ".html", ".css", ".js", ".json",
    ".md", ".txt", ".py", ".yml", ".yaml", ".properties", ".toml",
    ".gradle", ".sh", ".bat", ".ps1", ".svg", ".pro",
}


def source_files():
    for path in ROOT.rglob("*"):
        if not path.is_file() or any(part in SKIP_DIRS for part in path.parts):
            continue
        if path.suffix.lower() in TEXT_SUFFIXES:
            yield path


errors = []
for path in source_files():
    try:
        content = path.read_text(encoding="utf-8")
    except (UnicodeDecodeError, OSError):
        continue
    relative = path.relative_to(ROOT).as_posix()
    if retired_brand.search(content):
        errors.append(f"Retired product name found in {relative}")
    if retired_secret_prefix.casefold() in content.casefold():
        errors.append(f"Retired signing-secret prefix found in {relative}")
    if retired_package.casefold() in content.casefold():
        errors.append(f"Retired Android package found in {relative}")


def require_file(relative):
    path = ROOT / relative
    if not path.is_file():
        errors.append(f"Required Wren identity file is missing: {relative}")
        return None
    return path


gradle = require_file("app/build.gradle.kts")
if gradle:
    build = gradle.read_text(encoding="utf-8")
    for key in ("namespace", "applicationId"):
        if not re.search(rf"\b{key}\s*=\s*\"com\.wren\.app\"", build):
            errors.append(f"Android {key} must be com.wren.app")

for relative in ("app/src/main/res/values/strings.xml", "app/src/main/res/values-ar/strings.xml"):
    strings_path = require_file(relative)
    if not strings_path:
        continue
    try:
        root = ET.parse(strings_path).getroot()
        app_name = root.find("string[@name='app_name']")
        if app_name is None or (app_name.text or "").strip() != "Wren":
            errors.append(f"{relative} must set app_name to Wren")
    except ET.ParseError as error:
        errors.append(f"Invalid Android strings XML in {relative}: {error}")

manifest_path = require_file("app/src/main/AndroidManifest.xml")
if manifest_path:
    manifest = manifest_path.read_text(encoding="utf-8")
    if 'android:label="@string/app_name"' not in manifest:
        errors.append("Android launcher label must reference @string/app_name")
    launcher = require_file("app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml")
    if launcher:
        icon = launcher.read_text(encoding="utf-8")
        for resource in ("wren_launcher_background", "wren_launcher_foreground", "wren_launcher_monochrome"):
            if resource not in icon:
                errors.append(f"Adaptive launcher icon is missing {resource}")

home_path = require_file("app/src/main/assets/home.html")
if home_path and not re.search(r"<title>\s*Wren\s*</title>", home_path.read_text(encoding="utf-8"), re.I):
    errors.append("Wren start page title must be Wren")

icon_path = require_file("app/src/main/res/drawable/ic_wren.xml")
if icon_path and "<vector" not in icon_path.read_text(encoding="utf-8"):
    errors.append("Wren launcher mark must be an Android vector drawable")

if errors:
    print("\n".join(f"ERROR: {error}" for error in errors))
    sys.exit(1)

print("Wren identity is consistent; no retired product references found.")
