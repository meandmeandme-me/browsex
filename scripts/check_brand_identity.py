#!/usr/bin/env python3
"""Guard Wren's user-visible identity and connected Android launcher assets."""
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
CHECKS = {
    "app/src/main/res/values/strings.xml": ("<string name=\"app_name\">Wren</string>", "English app name"),
    "app/src/main/res/values-ar/strings.xml": ("<string name=\"app_name\">Wren</string>", "Arabic app name"),
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
    if 'android:label="@string/app_name"' not in manifest:
        errors.append("Android manifest does not use the localized app name.")
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

# Check only user-visible text nodes. Internal resource identifiers and
# storage keys retain their stable names to avoid breaking persisted data.
for relative in (
    "app/src/main/res/values/strings.xml",
    "app/src/main/res/values-ar/strings.xml",
):
    path = ROOT / relative
    if path.is_file():
        import re
        for value in re.findall(r"<string\\b[^>]*>([\\s\\S]*?)</string>", path.read_text(encoding="utf-8")):
            if re.search(r"browse\\s*x", value, re.IGNORECASE):
                errors.append(f"Legacy product name remains in visible string value: {relative}")
                break

home_path = ROOT / "app/src/main/assets/home.html"
if home_path.is_file():
    home = home_path.read_text(encoding="utf-8")
    if re.search(r"BrowseX", home, re.IGNORECASE):
        errors.append("Legacy product name remains in home page visible text.")

if errors:
    print("\\n".join(f"ERROR: {error}" for error in errors))
    sys.exit(1)
print("Wren brand identity and connected launcher resources are consistent.")
