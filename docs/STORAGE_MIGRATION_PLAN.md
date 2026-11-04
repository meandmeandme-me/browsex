# BrowseX storage migration plan

Status: schema and rollout proposal only. Current production storage remains SharedPreferences. No DataStore/Room dependency or migration has been introduced by this document.

## Existing storage inventory

All focused stores currently share the legacy SharedPreferences file named browsex.browser.

| Owner | Current keys | Proposed destination |
|---|---|---|
| BrowserPreferencesStore | theme_mode, language_mode, site_notifications_enabled, home_show_quick_links, home_show_discovery, home_layout, search_engine, ad_block_enabled | Preferences DataStore |
| BrowserPreferencesStore | denied_platform_permissions | Preferences DataStore |
| BrowserPreferencesStore | site_media_permissions | Room site_permission_decisions |
| BrowserSessionStore | open_tabs, active_tab_index | Room tab_snapshots or a single DataStore snapshot; decide after query/transaction requirements are finalized |
| BrowserSessionStore | named_sessions_v1, recently_closed_tabs_v1 | Room saved_sessions, saved_session_tabs, recently_closed_tabs |
| BrowserLibraryStore | bookmark_folders_v1, bookmark_items_v1, legacy bookmarks | Room bookmark_folders, bookmarks |
| BrowserLibraryStore | history_entries_v1, legacy history | Room history_entries |
| BrowserDownloadStore | browsex_download_names, browsex_download_ids, stream_downloads_v1 | Room download_records, download_name_reservations, platform_download_ids |

Key names are a migration contract. Do not rename or delete them before a verified migration is complete.


## Verified legacy key contract (source-level inventory)

The following details were checked against the four focused store implementations at the time of this plan. They describe the current format, not the future Room/DataStore API.

| Key | Stored type / default | Current behavior and migration notes |
|---|---|---|
| `theme_mode` | String / `system` | Normalized through `BrowserPreferencePolicy.normalizeTheme`. |
| `language_mode` | String / `system` | Normalized BCP-47 tag; preserve `system` as a special value. |
| `site_notifications_enabled` | Boolean / `true` | App-level website-notification switch; separate from Android POST_NOTIFICATIONS grant state. |
| `home_show_quick_links` | Boolean / `true` | Missing means default enabled. |
| `home_show_discovery` | Boolean / `true` | Missing means default enabled. |
| `home_layout` | String / `grid` | Normalized to supported layouts. |
| `search_engine` | String / `Google` | Normalized by `BrowserStorePolicy`. |
| `ad_block_enabled` | Boolean / `false` | Preference flag only; do not interpret as proof that a complete network/content blocker is implemented. |
| `denied_platform_permissions` | StringSet / empty | Supplemental denial history; Android runtime permission state is authoritative. Copy the set defensively. |
| `site_media_permissions` | JSON object string / `{}` | Keys are `origin|feature`; only camera/microphone and allow/deny are retained. `ask` removes the entry. Maximum 100 decisions. |
| `open_tabs` | JSON string array / `[]` | Regular-tab URL snapshot only; raw reader accepts at most 500 entries before normalization. |
| `active_tab_index` | Int / `0` | Index into the normalized regular-tab snapshot, not the all-tabs list. |
| `named_sessions_v1` | JSON array string / `[]` | Up to 10 sessions; each has id, name, urls, activeIndex, savedAt. Names max 40 chars; URLs bounded by tab normalization. |
| `recently_closed_tabs_v1` | JSON array string / `[]` | Up to 10 entries with id, url, title, closedAt. Only validated HTTP(S) URLs; HOME_URL excluded on write. |
| `bookmark_folders_v1` | JSON array string / absent | Absent means the built-in Unfiled folder. At most 50 stored non-default folders are read; writes include Unfiled. |
| `bookmark_items_v1` | JSON array string / absent | Absent triggers one-time import from legacy `bookmarks`; records carry id, url, title, folderId, createdAt. |
| `bookmarks` | Legacy JSON string array / `[]` | Compatibility mirror of bookmark URLs, rewritten alongside `bookmark_items_v1`; preserve until old-format fallback is no longer needed. |
| `history_entries_v1` | JSON array string / absent | Absent triggers import from legacy `history`; records carry url, title, visitedAt. |
| `history` | Legacy JSON string array / `[]` | Compatibility mirror of history URLs, rewritten alongside `history_entries_v1`; fallback used if detailed history JSON is corrupt. |
| `browsex_download_names` | JSON string array / `[]` | Case-insensitive filename collision reservations; bounded to the latest 300 names. |
| `browsex_download_ids` | JSON numeric array / `[]` | IDs of DownloadManager items enqueued by BrowseX only; bounded to 300. The actual downloaded files/status remain owned by Android DownloadManager. |
| `stream_downloads_v1` | JSON array string / `[]` | Up to 300 records with id, displayName, uri, mimeType, bytesWritten, createdAt, failed, error, status, totalBytes. Active queued/running records are converted to failed on recovery after interruption. |

### Compatibility and side-effect constraints

- All keys above live in the single `browsex.browser` SharedPreferences file. A migration must snapshot this file consistently and must not clear it as part of import.
- Bookmark and history URL-only keys are compatibility mirrors, not independent collections. Import detailed records first when present; use the legacy URL-only key only when the detailed key is absent or damaged, matching current fallback behavior.
- Current reads of absent detailed bookmark/history keys may write migrated detailed data and mirrors back to SharedPreferences. The migration implementation must avoid accidentally invoking these mutating read paths before its immutable legacy snapshot is captured.
- Clearing history currently also removes `recently_closed_tabs_v1`. Preserve this observable behavior only if product requirements confirm it is intentional; otherwise make the behavior change separately from storage migration and cover it with a regression test.
- DownloadManager IDs are references to Android-owned records, not complete download records. Do not attempt to recreate or claim ownership of those downloads from Room alone.
- The current download-name reservation and stream-download JSON lists are bounded and ordered. Preserve ordering/retention semantics or document an explicit product-level change.
- The migration must never include private-tab URLs, GeckoSession state, cookies, credentials, notification payloads, or raw form data.

## Proposed Room schema

Use stable primary keys already present in domain records where available. Foreign keys should use explicit ON DELETE behavior.

- bookmark_folders(id PRIMARY KEY, name, sort_order, created_at, updated_at)
- bookmarks(id PRIMARY KEY, url UNIQUE, title, folder_id, sort_order, created_at, updated_at); folder reference points to the default/unfiled folder when missing.
- history_entries(id PRIMARY KEY, url, title, visited_at, visit_count) with indexes on URL and visited_at.
- saved_sessions(id PRIMARY KEY, name, active_index, saved_at) plus saved_session_tabs(session_id, position, url) with a composite primary key.
- recently_closed_tabs(id PRIMARY KEY, url, title, closed_at) with bounded retention.
- site_permission_decisions(origin, feature, decision, updated_at) with composite primary key (origin, feature).
- download_records(id PRIMARY KEY, source_kind, display_name, mime_type, uri, bytes_written, total_bytes, status, error, created_at, updated_at).
- platform_download_ids(download_id PRIMARY KEY, created_at).
- download_name_reservations(normalized_name PRIMARY KEY, reserved_at) with bounded retention.

Do not persist GeckoSession instances, cookies, raw auth tokens, form contents, or private-tab URLs. The open-tab snapshot must continue to filter private tabs before writing.

## Proposed DataStore keys

Theme mode, BCP-47 language tag, notification switch, home-page visibility/layout, search engine and ad-block preference. Platform permission-denial hints only if product behavior still needs them; Android's own permission state remains authoritative. Migration marker contains schema version and completion metadata, not a simple boolean without versioning.

## Migration protocol

1. Ship additive schema version 1 and create tables without changing existing preference writes.
2. Read the legacy SharedPreferences snapshot on an IO dispatcher; never clear it during the read phase.
3. Parse each JSON payload defensively with existing validators and explicit item limits.
4. Normalize legacy bookmarks/history/sessions, generate stable IDs only when absent, and repair invalid folder references to Unfiled.
5. Insert all imported records in a single Room transaction. Preserve timestamps and ordering; do not invent visit dates.
6. Write DataStore preferences and migration-version marker only after the Room transaction commits.
7. On interruption, rerun idempotently. Use deterministic migration IDs or a transaction marker to prevent duplicate records.
8. Verify counts, IDs, relationships, statuses and a checksum/summary of source payloads.
9. Keep the original SharedPreferences values untouched for at least one stable release and until upgrade verification is proven.
10. Only a later release may remove legacy reads/writes, after rollback and downgrade behavior are documented.

## Recovery and failure behavior

- Corrupt JSON: import valid records, record a recoverable migration warning and retain the original source string for support/retry.
- Invalid URLs: exclude from active browser collections and count them in migration diagnostics; never silently convert them into navigable URLs.
- Missing folder: move bookmark to Unfiled.
- Duplicate URL: merge deterministically while preserving stable bookmark identity and the most recent valid metadata.
- Interrupted transaction: Room rolls back; migration marker is not advanced.
- DataStore failure after Room commit: retry preference import; operations must be idempotent.
- App downgrade: old version can still read the untouched legacy keys.

## Required tests before rollout

- Fresh install creates empty schema and default preferences.
- Upgrade from each legacy JSON format.
- Repeated launch after migration is idempotent.
- Process death before/during/after Room transaction.
- Corrupt/truncated JSON and unexpected types.
- Invalid URLs, duplicate bookmarks, missing folders and extreme payload sizes.
- Download statuses and IDs remain consistent.
- Private tabs and URLs are never migrated.
- API 26 low-memory migration and API 29+ behavior.
- Backup/restore, downgrade and rollback behavior.
