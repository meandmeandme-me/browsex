#!/usr/bin/env python3
"""Validate Android locale resource parity and formatting placeholders."""

from collections import Counter
from pathlib import Path
import re
import sys
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / "app" / "src" / "main" / "res"
BASE = RES / "values" / "strings.xml"
LOCALE_CONFIG = RES / "xml" / "locales_config.xml"
PLACEHOLDER = re.compile(r"%(?!%)(?:[0-9]+\\$)?[-#+ 0,(<]*[0-9]*(?:\\.[0-9]+)?[a-zA-Z]")


def resources(path: Path):
    root = ET.parse(path).getroot()
    found = {}
    duplicates = []
    for element in root:
        name = element.attrib.get("name")
        if not name:
            continue
        key = (element.tag, name)
        if key in found:
            duplicates.append(f"{element.tag}/{name}")
        if element.tag == "plurals":
            values = {
                child.attrib.get("quantity", ""): "".join(child.itertext())
                for child in element
            }
            found[key] = values
        else:
            found[key] = {"": "".join(element.itertext())}
    return found, duplicates


def locale_name(directory: str) -> str:
    qualifier = directory.removeprefix("values-")
    if qualifier.startswith("b+"):
        return qualifier[2:].replace("+", "-")
    return re.sub(r"-r([A-Z]{2})$", r"-\\1", qualifier)


def main() -> int:
    errors = []
    base, duplicates = resources(BASE)
    errors.extend(f"{BASE}: duplicate resource {name}" for name in duplicates)

    config_root = ET.parse(LOCALE_CONFIG).getroot()
    android_name = "{http://schemas.android.com/apk/res/android}name"
    configured = {
        element.attrib[android_name]
        for element in config_root
        if android_name in element.attrib
    }
    configured_normalized = {value.lower() for value in configured}

    locale_files = sorted(RES.glob("values-*/strings.xml"))
    for path in locale_files:
        locale = locale_name(path.parent.name)
        if locale.lower() not in configured_normalized:
            errors.append(f"{path}: locale '{locale}' is missing from locales_config.xml")
        translated, duplicates = resources(path)
        errors.extend(f"{path}: duplicate resource {name}" for name in duplicates)

        missing = sorted(set(base) - set(translated))
        extra = sorted(set(translated) - set(base))
        if missing:
            errors.append(f"{path}: missing resources: " + ", ".join(f"{kind}/{name}" for kind, name in missing))
        if extra:
            errors.append(f"{path}: unknown resources: " + ", ".join(f"{kind}/{name}" for kind, name in extra))

        for key in sorted(set(base) & set(translated)):
            base_values = base[key]
            translated_values = translated[key]
            if set(base_values) != set(translated_values):
                errors.append(f"{path}: plural quantities differ for {key[0]}/{key[1]}")
                continue
            for quantity, source_text in base_values.items():
                target_text = translated_values[quantity]
                source_tokens = Counter(PLACEHOLDER.findall(source_text))
                target_tokens = Counter(PLACEHOLDER.findall(target_text))
                if source_tokens != target_tokens:
                    suffix = f"/{quantity}" if quantity else ""
                    errors.append(
                        f"{path}: format placeholders differ for {key[0]}/{key[1]}{suffix}: "
                        f"expected {dict(source_tokens)}, found {dict(target_tokens)}"
                    )

    if errors:
        print("Locale resource validation failed:")
        for error in errors:
            print(f" - {error}")
        return 1

    print(f"Locale resources valid: {len(base)} base resources, {len(locale_files)} translated locale file(s).")
    return 0


if __name__ == "__main__":
    sys.exit(main())
