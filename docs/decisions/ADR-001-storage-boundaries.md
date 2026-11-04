# ADR-001: Separate storage ownership before changing storage engines

- Status: Accepted
- Date: 2026-11-04

## Context

The original `BrowserStore` combined settings, per-origin permission decisions, tab/session snapshots, bookmarks/history and download metadata in one SharedPreferences-backed class. Replacing the backend in one operation would mix schema migration risk with behavior changes and could destroy user data.

## Decision

Split ownership into focused stores while preserving the existing SharedPreferences file and key names:

- `BrowserPreferencesStore`: app preferences and supplemental site permission decisions.
- `BrowserSessionStore`: regular-tab snapshots, named sessions and recently closed tabs.
- `BrowserLibraryStore`: bookmarks, folders, history and JSON import/export.
- `BrowserDownloadStore`: DownloadManager IDs, stream-download records and filename reservations.
- `BrowserStore`: compatibility facade for existing feature callers.

Pure validation remains in policy classes. No data migration or backend change is implied by this extraction.

## Next migration

Evaluate DataStore for small preferences and Room for relational collections/metadata. Before changing either backend:

1. Define explicit schema versions and migrations.
2. Read legacy SharedPreferences without deleting or rewriting it.
3. Validate migrated counts, IDs, folder references, URLs, statuses and timestamps.
4. Commit the new database transaction before marking migration complete.
5. Retain a recoverable legacy snapshot until upgrade verification passes.
6. Add Android-backed tests for fresh install, upgrade, interrupted migration, corrupted legacy data, rollback and repeated launch.

Never store GeckoSession, cookies, credentials or private-tab URLs in this migration.

## Consequences

The current storage backend remains unchanged, reducing immediate risk. Feature code can migrate incrementally. There is temporary duplication of facade methods and additional store objects, which should be removed only after callers depend on domain repositories directly.
