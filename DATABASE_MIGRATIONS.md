# Wren database and migration policy

## Pre-release baseline

- The Room database is named `wren-browser.db`. Do not rename it as part of a UI or brand change.
- The current Room schema is version **2**. Its exported schema is committed at `app/schemas/com.browsex.app.storage.BrowseXDatabase/2.json`.
- The project is still pre-release and has no public user-data compatibility promise yet. The current storage graph intentionally starts from the clean Room baseline; it does not silently import the former SharedPreferences browsing stores.
- Browser settings are stored separately in the DataStore file `browsex.settings`.
- Destructive migration fallback is forbidden. Never add `fallbackToDestructiveMigration()`, `fallbackToDestructiveMigrationOnDowngrade()`, or a destructive fallback for convenience.

## When changing the schema

For every future Room schema change:

1. Increment `@Database(version = ...)` by exactly one.
2. Generate and commit the new exported schema JSON. Never edit generated schema JSON by hand.
3. Add an explicit Room `Migration(oldVersion, newVersion)` to the database builder.
4. Write an Android instrumentation migration test using the committed old-version schema fixture. Verify existing rows in every affected table survive and new columns/tables have correct defaults, indexes, and foreign keys.
5. Test both upgrade and any intentionally supported downgrade. If downgrade is not supported, fail safely with a clear recovery path; do not erase data.
6. Run repository tests, lint, and the full debug/release build.
7. Review privacy implications before adding any field that can contain browsing history, credentials, identifiers, or site permissions.

Do not create a speculative v2-to-v3 migration until an actual schema change is required. The committed v2 schema is the baseline fixture for the first real migration.

## Data safety invariants

- Bookmark URLs remain unique; folder references must resolve to the reserved `unfiled` folder or a real folder.
- Saved session tabs are children of their session and are removed atomically with it.
- Permission decisions are keyed by origin and feature.
- Download records, platform download IDs, and filename reservations must remain mutually reconcilable.
- Open-tab snapshots and active index must be committed as one transaction and restored with a clamped valid index.
- History pruning must not delete newer rows and must preserve visit counts.
- Private browsing data must not be written to regular history/session persistence.
- DataStore updates must remain atomic read-modify-write operations.
- Never log raw URLs, page titles, credentials, or other browsing content.

## Before first public release

- Decide and document the retention limits for history, recently closed tabs, sessions, and downloads.
- Verify app backup/data extraction rules match the privacy policy and encryption design.
- Verify storage corruption handling and backup/restore behavior on real devices.
- Verify upgrades from the final pre-release build to the first public release.
- Keep the signing key stable across all releases; signing identity is separate from the Room schema version.
