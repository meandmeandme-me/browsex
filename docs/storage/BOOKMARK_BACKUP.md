# BrowseX bookmark backup contract

## Scope

This file describes a portable backup of bookmarks and bookmark folders only. It is not a full browser profile backup.

The backup must never contain cookies, passwords, authentication tokens, browsing history, open tabs, saved sessions, download contents, site permissions, or private-session data.

## Version 1 JSON shape

```json
{
  "format": "browsex-bookmarks",
  "version": 1,
  "folders": [
    { "id": "folder-id", "name": "Research" }
  ],
  "bookmarks": [
    {
      "id": "bookmark-id",
      "url": "https://example.com/",
      "title": "Example",
      "folderId": "folder-id",
      "createdAt": 1730000000000
    }
  ]
}
```

The `unfiled` folder is represented by the reserved ID `unfiled`. Exporters should include it so imported bookmarks retain a valid destination.

## Import rules

- Accept only `format=browsex-bookmarks` and `version=1`.
- Reject payloads larger than 1 MB, more than 50 incoming folders, or more than 1,000 incoming bookmark rows.
- Validate every URL as an ordinary HTTP(S) web URL and cap URLs at 4,096 characters.
- Normalize folder names to 40 characters and bookmark titles to 160 characters.
- Treat folder names case-insensitively; map an incoming folder to an existing local folder with the same name.
- Unknown or unimportable folder references map to `unfiled`.
- Merge only: an existing URL is never overwritten by an imported row.
- Keep at most 50 folders including `unfiled`, and at most 200 bookmarks.
- Import is performed transactionally in Room. The result reports imported folders, imported bookmarks, and skipped bookmark rows.

## Compatibility and rollout

The JSON property names and version marker match the portable bookmark backup format in `BrowserLibraryStore`. The Room repository implementation is groundwork only until the app's bookmark UI and all bookmark mutations are moved together to a single repository source of truth. Do not enable a partial dual-write path, and do not remove legacy SharedPreferences keys during rollout.

A future format change must introduce a new version and an explicit migration path. Never silently reinterpret an unknown version.
