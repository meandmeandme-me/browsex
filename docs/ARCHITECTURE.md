# BrowseX Architecture

This document describes the current Android application boundaries and the rules that new features must preserve. It is a working architecture guide, not a claim that every roadmap item is already implemented.

## Application layers

### 1. Application and platform composition

- `BrowseXApplication` owns the process-scoped `BrowserStorageRuntime`.
- `MainActivity` is the Android host and composition root for browser UI, GeckoView, Android permissions, intents, and lifecycle callbacks.
- `BrowserRuntimeOwner` creates the single process-wide `GeckoRuntime`.
- Platform-only services (notifications, autofill, file sharing, Android permission launchers) stay behind dedicated coordinators or bridge classes.

Keep Android framework and GeckoView objects out of storage repositories and pure policy classes.

### 2. Browser domain and lifecycle

- `BrowserTab` is the in-memory tab model; its `privateMode` flag is a security boundary.
- `BrowserTabRegistry` owns tab membership and active selection.
- `BrowserTabController` creates, activates, restores, and closes tab sessions.
- `BrowserSessionLifecycleCoordinator` owns GeckoSession teardown and recovery.
- `BrowserHostLifecycleCoordinator` handles Activity visibility and memory pressure.
- `BrowserTabEventCoordinator` converts Gecko callbacks into browser-domain events.
- `TabSessionState` validates and bounds URLs and tab snapshots before persistence or restoration.

Do not let UI pages create or close Gecko sessions directly. Inject operations through the browser controller.

### 3. Persistence and settings

The storage composition root is:

```text
BrowseXApplication
└── BrowserStorageRuntime
    ├── BrowserSettingsDataStore
    └── BrowserStorageGraph
        ├── BrowseXDatabase (Room)
        ├── RoomBookmarkRepository
        ├── RoomHistoryRepository
        ├── RoomSessionRepository
        ├── RoomTabStateRepository
        ├── RoomDownloadRepository
        └── RoomSitePermissionRepository
```

Repositories own validation, retention limits, and domain-specific transactions. UI code should call repositories rather than issuing SQL or manipulating entities directly.

Current persistence limits include:
- Bookmarks: 200; folders: 50.
- History: 300.
- Saved sessions: 10; each session is normalized to the shared tab limit.
- Recently closed tabs: 10, with a 30-day retention window.
- Site permission decisions: 100.
- Download history and tracked platform IDs: 300.
- Restored regular tabs: maximum 20 after validation.

Keep read queries bounded in SQL as well as enforcing write-time retention. A write cap alone is not enough to protect memory from malformed or legacy database contents.

### 4. UI surfaces

Feature pages are separate classes and should remain presentation-focused:
- `SettingsPage`, `PrivacyPage`, and `SitePermissionsPage`.
- `SiteInfoPage` for the active site's identity and permission controls.
- `TabsPage` and `SavedSessionsPage` for tab/session workflows.
- `RoomSavedPagesPage` and `RecallPage` for bookmarks, history, and quick return.
- `DownloadsPage` for Gecko stream downloads and Android DownloadManager entries.
- `ReaderPage` for the accessible reading surface.
- `AddressSuggestionsController` for address-bar suggestions.

The home document lives at `app/src/main/assets/home.html`. Native strings live in Android resource directories; the Arabic resource set is `values-ar`.

### 5. Security and privacy invariants

- Private tabs must not be written to history, recently closed, named sessions, or regular-tab snapshots.
- Private browsing must not reuse saved website permission decisions.
- Validate URLs and canonical origins at repository/policy boundaries; never trust UI-provided strings.
- Keep credentials, cookies, and browsing history out of bookmark backup exports.
- Do not weaken Gecko safe browsing or tracking protection as a side effect of adding an extension. When uBlock Origin is unavailable, disabled, or not permitted in private browsing, Gecko tracking protection remains enabled.
- Website notifications require both a persisted per-origin `allow` decision and Android notification permission; private-browsing notifications are never forwarded to the Android tray.
- Notification callbacks are asynchronous. Closing a Gecko notification while permission UI or storage IO is pending must prevent a late callback from posting it again.
- Tagged website notifications replace only the matching origin/tag pair; untagged notifications receive unique Android identities. Closing an older replaced notification must not cancel its replacement.
- The notification bridge releases pending Gecko notification references and cancels its own Android tray entries when the Activity is destroyed.
- Clearing browsing data is a multi-store operation: do not report success until Gecko storage plus selected Room history/site-permission cleanup have completed. Attempt independent cleanup steps even if another selected store fails.
- Treat permission prompts as asynchronous, session-scoped operations. Re-check that the requesting tab/session is still active before granting.

When adding a new persistent feature, explicitly define its private-browsing behavior, retention limit, export behavior, and clear-data behavior before wiring its UI.

## Extension seams for future capabilities

These are architectural directions; they are not assertions that the integrations are implemented.

### Account and sync providers

Add an account domain separate from browser tabs and settings:
- Provider-neutral account state and session lifecycle.
- Dedicated Google/Microsoft OAuth/OIDC adapters.
- Encrypted token storage backed by Android Keystore.
- OAuth PKCE transactions are one-use objects and must not be cloneable; callback state, redirect target, age, provider errors, and duplicate parameters are validated before accepting an authorization code.
- Explicit consent, disconnect, token revocation, and account deletion flows.
- Sync conflict resolution and per-data-type opt-in.

Never store provider access tokens in Room bookmark/history tables or plain preferences.

### Privacy and content protection

Keep protection policy separate from UI:
- One policy surface for tracker/ad blocking, exceptions, and per-site controls.
- Separate provider adapters for filter lists and update scheduling.
- Versioned filter-list metadata, integrity checks, and bounded caches.
- Clear distinction between Gecko tracking protection and any optional content-blocking engine.

### Data and schema evolution

The current Room database is version 2. The repository currently contains the version-2 schema export but no version-1 schema fixture, and the database builder now deliberately has no destructive migration fallback. This is a pre-release fail-closed policy: an unsupported schema mismatch must not silently erase browsing data.

Before shipping persistent user data:
1. Commit the generated Room schema JSON for every released database version.
2. Add and test explicit migrations from each supported prior version using fixtures from the actual exported schemas.
3. Verify migration rollback/failure behavior, row preservation, indexes, foreign keys, and backup/restore across upgrades.
4. Block release if any previously shipped version lacks a tested migration path.

See `docs/DATABASE_MIGRATION_RELEASE_GATE.md` for the required migration and release checklist. The CI workflow expects the generated schema at `app/schemas/com.browsex.app.storage.BrowseXDatabase/2.json`; keep that artifact committed and in sync with the entity definitions.

## Build and verification

- Android min SDK: 26.
- Compile/target SDK: 36.
- Java/Kotlin target: 17.
- GeckoView is the browser engine.
- ABI outputs include `armeabi-v7a`, `arm64-v8a`, and a universal APK.
- `.github/workflows/main.yml` runs locale validation, unit tests, lint, debug/release APK builds, APK signing/ABI checks, and Room schema checks.

A code change is not considered verified until the relevant automated checks pass. Device-specific installation, rendering, memory, and permission behavior still require real-device validation, including low-memory and older 32-bit devices.
