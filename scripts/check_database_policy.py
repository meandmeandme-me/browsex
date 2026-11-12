#!/usr/bin/env python3
"""Enforce Wren's pre-release Room database safety contract."""

from pathlib import Path
import json
import re
import sys

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "app/src/main/java/com/browsex/app/storage/BrowseXDatabase.kt"
SCHEMA_DIR = ROOT / "app/schemas/com.browsex.app.storage.BrowseXDatabase"
GRADLE = ROOT / "app/build.gradle.kts"
RUNTIME = ROOT / "app/src/main/java/com/browsex/app/storage/BrowserStorageRuntime.kt"
GRAPH = ROOT / "app/src/main/java/com/browsex/app/storage/BrowserStorageGraph.kt"

errors = []
source = SOURCE.read_text(encoding="utf-8")
runtime = RUNTIME.read_text(encoding="utf-8")
graph = GRAPH.read_text(encoding="utf-8")
gradle = GRADLE.read_text(encoding="utf-8")

match = re.search(r"@Database\([\s\S]*?version\s*=\s*(\d+)", source)
if not match:
    errors.append("Cannot determine the Room @Database version.")
    version = None
else:
    version = int(match.group(1))

if '"wren-browser.db"' not in source:
    errors.append("The persistent database filename must remain wren-browser.db.")

# Search all production Kotlin sources. All destructive fallback variants are forbidden.
for path in (ROOT / "app/src/main/java").rglob("*.kt"):
    code = path.read_text(encoding="utf-8")
    if re.search(r"fallbackToDestructiveMigration(?:OnDowngrade|From)?\s*\(", code):
        errors.append(f"Destructive Room migration fallback is forbidden: {path.relative_to(ROOT)}")

if "room.schemaLocation" not in gradle or "exportSchema = true" not in source:
    errors.append("Room schema export must remain enabled.")
if "No SharedPreferences browsing data is imported" not in graph:
    errors.append("Document the clean pre-release baseline; do not silently import legacy stores.")
if "explicit, fixture-tested Room migrations" not in runtime:
    errors.append("Runtime must retain the explicit, fixture-tested migration policy.")

if version is not None:
    schema_file = SCHEMA_DIR / f"{version}.json"
    if not schema_file.is_file():
        errors.append(f"Missing exported Room schema for current version {version}: {schema_file}")
    else:
        try:
            schema = json.loads(schema_file.read_text(encoding="utf-8"))
            schema_version = schema["database"]["version"]
            if schema_version != version:
                errors.append(f"Schema JSON version {schema_version} does not match @Database version {version}.")
            if not schema["database"].get("entities"):
                errors.append("Exported Room schema has no entities.")
        except (ValueError, KeyError, TypeError) as exc:
            errors.append(f"Invalid exported Room schema JSON: {exc}")

    versions = sorted(
        int(path.stem) for path in SCHEMA_DIR.glob("*.json") if path.stem.isdigit()
    )
    if not versions:
        errors.append("No committed Room schema snapshots were found.")
    elif versions[-1] != version:
        errors.append(f"Latest committed schema is v{versions[-1]}, but @Database is v{version}.")
    if versions and versions[0] != 2:
        errors.append(f"Expected the clean pre-release baseline to start at Room v2, found v{versions[0]}.")

    if version > 2:
        storage_sources = list((ROOT / "app/src/main/java/com/browsex/app/storage").rglob("*.kt"))
        migration_source = "\n".join(path.read_text(encoding="utf-8") for path in storage_sources)
        edges = {
            (int(start), int(end))
            for start, end in re.findall(r"Migration\(\s*(\d+)\s*,\s*(\d+)\s*\)", migration_source)
        }

        # Require a continuous explicit path from every committed schema to current.
        for start_version in versions:
            reachable = {start_version}
            changed = True
            while changed:
                changed = False
                for edge_start, edge_end in edges:
                    if edge_start in reachable and edge_end > edge_start and edge_end not in reachable:
                        reachable.add(edge_end)
                        changed = True
            if version not in reachable:
                errors.append(f"No continuous explicit migration path from schema v{start_version} to v{version}.")

        migration_tests = list((ROOT / "app/src/androidTest").rglob("*Migration*Test.kt")) if (ROOT / "app/src/androidTest").exists() else []
        if not migration_tests:
            errors.append("A database version change requires instrumented migration fixture tests.")
        elif not any(
            "MigrationTestHelper" in path.read_text(encoding="utf-8")
            and "runMigrationsAndValidate" in path.read_text(encoding="utf-8")
            for path in migration_tests
        ):
            errors.append("Migration instrumentation tests must use Room MigrationTestHelper and runMigrationsAndValidate.")

if errors:
    for error in errors:
        print(f"ERROR: {error}")
    sys.exit(1)

print(f"Wren database policy OK: Room v{version}, exported schema present, stable filename, no destructive fallback.")
