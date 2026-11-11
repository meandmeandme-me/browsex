#!/usr/bin/env python3
"""Guard user-visible BrowseX branding and launcher resources."""

from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
CHECKS = {
    "app/src/main/res/values/strings.xml": ("BrowseX", "app_name"),
    "app/src/main/res/values-ar/strings.xml": ("BrowseX", "app_name"),
    "app/src/main/assets/home.html": ("<title>BrowseX</title>", "BrowseX home title"),
    "app/src/main/java/com/browsex/app/BrowseXBrowserChrome.kt": ("BrowseX", "Compose browser chrome"),
    "app/src/main/res/drawable/ic_browsex.xml": ("#71D6FF", "BrowseX mark"),
}
LEGACY_RESOURCES = (
    "app/src/main/res/drawable/ic_wren_icon_background.xml",
    "app/src/main/res/drawable/ic_wren_icon_foreground.xml",
    "app/src/main/res/drawable/ic_wren_icon_monochrome.xml",
    "app/src/main/res/drawable/wren_tv_banner.xml",
    "app/src/main/res/drawable-xhdpi/wren_tv_banner.xml",
    "app/src/main/res/drawable-ar-xhdpi/wren_tv_banner.xml",
)

errors = []
for relative, (required, description) in CHECKS.items():
    path = ROOT / relative
    if not path.is_file():
        errors.append(f"Missing {description}: {relative}")
        continue
    content = path.read_text(encoding="utf-8")
    if required not in content:
        errors.append(f"Missing BrowseX identity marker in {description}: {relative}")
    if "Wren" in content or "wren" in content:
        errors.append(f"Legacy brand leaked into user-visible {description}: {relative}")

manifest = (ROOT / "app/src/main/AndroidManifest.xml").read_text(encoding="utf-8")
if '@drawable/browsex_tv_banner' not in manifest:
    errors.append("Android TV banner is not connected to the BrowseX resource")

launcher = (ROOT / "app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml").read_text(encoding="utf-8")
for resource in ("browsex_launcher_background", "browsex_launcher_foreground", "browsex_launcher_monochrome"):
    if resource not in launcher:
        errors.append(f"Adaptive launcher icon is missing {resource}")

for relative in LEGACY_RESOURCES:
    if (ROOT / relative).exists():
        errors.append(f"Obsolete Wren branding resource remains: {relative}")

if errors:
    print("\n".join(f"ERROR: {error}" for error in errors))
    sys.exit(1)

print("BrowseX brand identity and launcher resources are consistent.")
