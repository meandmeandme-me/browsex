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

## Continuous integration enforcement

- The database policy checker scans all production Kotlin sources for destructive Room fallback variants.
- Once the schema advances beyond v2, every committed starting schema must have a continuous explicit migration path to the current version.
- Migration instrumentation tests must use Room `MigrationTestHelper` and `runMigrationsAndValidate`.
- CI runs those migration tests on an Android emulator whenever a migration test fixture is present. The current v2 baseline has no schema upgrade to test, so no speculative migration test is created.
- The CI schema verification derives the expected snapshot filename from the current `@Database` version instead of hard-coding v2.

## Storage ownership boundaries

- Room owns durable browsing records: bookmarks/folders, history, saved sessions, recently closed tabs, site permission decisions, open-tab snapshots, and download metadata/reservations.
- Preferences DataStore owns non-secret browser settings. Its current file name is `browsex.settings`; keep it stable unless a tested, explicit file migration is implemented.
- Saved login credentials are deliberately outside Room. `BrowserCredentialVault` stores only AES-GCM ciphertext in the private app preferences file `browsex_credentials_v1`; the AES key is held by Android Keystore and requires recent device authentication. Never move credential plaintext into Room, logs, backups, or ordinary preferences.
- The credential vault's small process marker for private-tab state is coordination metadata, not browsing history. Keep it process-scoped and fail closed for credential autofill while private tabs are open.
- Do not rename storage files merely to match visible Wren branding. Storage filenames are compatibility identifiers, not user-facing product labels.
