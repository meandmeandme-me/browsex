# Wren database migration and release gate

## Current development baseline

- The Room database is `wren-browser.db`, currently schema version **2**.
- The committed Room export currently contains `app/schemas/com.wren.app.storage.WrenDatabase/2.json`.
- There is no committed version-1 schema fixture in this repository. Do not invent a version-1 migration from guessed columns or indexes.
- Wren is still pre-release and has no production user database population. This is the correct time to establish a fail-closed policy before persistent user data exists.
- Treat schema version **2** as the first supported database baseline for the first public release. Do not publish an upgrade path from an older development schema unless its exact exported schema is recovered and a fixture-based migration is added.
- Before the first public release, developers using older local test databases must reset only those development installs; this is not a user-data recovery procedure.
- The database builder intentionally has **no destructive migration fallback**. A schema mismatch must fail visibly rather than silently erase bookmarks, history, sessions, permissions, or download records.

## Rules for every future schema change

1. Increment the Room `@Database(version = ...)` exactly once for the schema change.
2. Commit the generated schema JSON for the new version. Never overwrite or delete an older released schema export.
3. Add an explicit Room `Migration(oldVersion, newVersion)` for every supported upgrade path. If two-step upgrades are supported, keep each intermediate migration.
4. Make migrations transactional and idempotent where the Room migration API permits. Use SQL defaults or backfills for newly required fields; do not discard existing rows to simplify a migration.
5. Preserve primary keys, foreign keys, unique indexes, ordering fields, timestamps, permission decisions, and download state unless a reviewed product/data-retention decision explicitly changes them.
6. For table rebuilds, create the replacement table, copy and validate all compatible rows, swap tables, and recreate indexes/foreign keys. Do not use `DROP TABLE` before the copy has succeeded.
7. Register migrations with `Room.databaseBuilder(...).addMigrations(...)`. Do not reintroduce `fallbackToDestructiveMigration()` or destructive fallback-from-version shortcuts in a production path.
8. Add migration tests using a database created from the actual previous exported schema. Verify row counts and representative values before and after migration, foreign-key integrity, indexes, defaults, and a subsequent normal open.
9. Test every supported starting version, including direct upgrades from the oldest supported version. A test that only creates the latest schema is not a migration test.
10. Keep backup/import/export formats versioned independently from Room schema versions. Validate backup size and contents before opening a write transaction; never silently import credentials, cookies, or private browsing data.

## Release gate

A production release that stores user data is blocked until all are true:

- Every database version that has shipped to users has its schema JSON committed.
- Every supported upgrade path has an explicit migration and a fixture-based test.
- Migration failure leaves the original database intact and presents a recoverable error path; it must not trigger automatic data deletion.
- Backup/restore has been tested across the release boundary for bookmarks and saved sessions, and the documented export intentionally excludes secrets and private data.
- Clear-browsing-data actions are tested independently from schema migration and remove only the selected data categories.
- CI runs Room schema validation and migration tests for every pull request that changes entities or DAO schema.
- A release candidate is installed over the previous release on representative 32-bit and 64-bit Android devices, and the user-visible data is checked after first launch.

## Developer recovery before public release

Because this repository has no version-1 schema fixture and the product is pre-release, an old local development database may not be migratable. If a developer install fails to open after a schema change, back up any test data that matters and clear app data/reinstall only on that development device. Never use this as a production recovery strategy.

## Current known limits

The current repositories bound retained data (for example, 200 bookmarks, 50 folders, 300 history rows, 10 saved sessions, 10 recently closed tabs, 100 site permission decisions, and 300 download records/IDs). These are product retention policies, not migration policies. Changing a retention limit must not be implemented as a schema migration that unexpectedly deletes existing user data.
