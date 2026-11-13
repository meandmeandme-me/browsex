#!/usr/bin/env python3
"""Guard Wren's user-visible identity and connected Android launcher assets."""

from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
CHECKS = {
    "app/src/main/res/values/strings.xml": ("Wren", "English app name"),
    "app/src/main/res/values-ar/strings.xml": ("Wren", "Arabic app name"),
    "app/src/main/assets/home.html": ("<title>Wren</title>", "home page title"),
    "app/src/main/java/com/browsex/app/BrowseXBrowserChrome.kt": ("Wren", "Compose browser chrome"),
}
errors = []

for relative, (required, description) in CHECKS.items():
    path = ROOT / relative
    if not path.is_file():
        errors.append(f"Missing {description}: {relative}")
        continue
    value = path.read_text(encoding="utf-8")
    if required not in value:
        errors.append(f"Missing Wren identity marker in {description}: {relative}")

manifest_path = ROOT / "app/src/main/AndroidManifest.xml"
launcher_path = ROOT / "app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml"
if not manifest_path.is_file() or not launcher_path.is_file():
    errors.append("Android manifest or adaptive launcher resource is missing.")
else:
    manifest = manifest_path.read_text(encoding="utf-8")
    launcher = launcher_path.read_text(encoding="utf-8")
    if '@drawable/browsex_tv_banner' not in manifest:
        errors.append("Android TV banner is not connected.")
    for resource in (
        "browsex_launcher_background",
        "browsex_launcher_foreground",
        "browsex_launcher_monochrome",
    ):
        if resource not in launcher:
            errors.append(f"Adaptive launcher icon is missing {resource}.")
        if not (ROOT / f"app/src/main/res/drawable/{resource}.xml").is_file():
            errors.append(f"Missing launcher drawable: {resource}")

# Old branding is forbidden only in user-facing surfaces. Historical crypto
# aliases, database names and migration identifiers are deliberately excluded.
for relative in (
    "app/src/main/res/values/strings.xml",
    "app/src/main/res/values-ar/strings.xml",
    "app/src/main/assets/home.html",
    "app/src/main/AndroidManifest.xml",
):
    path = ROOT / relative
    if path.is_file():
        value = path.read_text(encoding="utf-8").lower()
        if "browsex" in value:
            errors.append(f"Legacy product name remains in user-facing resource: {relative}")

if errors:
    print("\n".join(f"ERROR: {error}" for error in errors))
    sys.exit(1)

print("Wren brand identity and connected launcher resources are consistent.")
