# BrowseX — Feature Foundation Plan

> Scope: engineering foundations for the six requested capabilities. This plan is based on the current `rewrite-v2` source tree and separates implemented behavior from missing work. It does not claim device verification.

## Product direction

- Keep the current Figma file as historical/reference material; the next UI specification and screen map are intended for Uizard.
- Build and verify browser capabilities behind stable interfaces first.
- Use `WORLD_BROWSER_PRODUCT_REQUIREMENTS.md` to define the new visual and interaction direction; do not treat a competitor's feature list as a requirement by itself.
- Keep ad blocking explicitly deferred.

## Current implementation audit

| Area | Already present in `rewrite-v2` | Remaining work |
|---|---|---|
| 1. Site privacy and security | HTTP(S)-only navigation validation; Android runtime permission flow for location/camera/microphone; per-site content permission controls; camera/microphone decisions stored by origin; website notification bridge; private notification suppression; Android app notification setting; privacy center for selective cookies, DOM storage, cache, site permissions and auth-session clearing after closing sessions; host-scoped site-data clearing from Site Info | Add Android-backed coverage for clear-data completion/failure and prompt cancellation; harden origin canonicalization and permission expiry/reset. Do not promise revocation for Gecko grants GeckoView cannot revoke. |
| 2. Sessions and tab recovery | `BrowserTabPersistenceCoordinator` isolates regular-tab snapshot restore/save policy from Activity lifecycle callbacks; one process-wide GeckoRuntime; per-tab GeckoSession ownership; validated persisted regular-tab URL snapshot; active tab restoration; 20-tab cap; lazy opening of background tabs; one automatic content-process recovery attempt; Gecko private-mode tabs excluded from history and persistence; BrowseX Recall; named regular-tab sessions with save/list/open/delete; up to ten recently closed regular tabs with restore; basic lifecycle-aware memory-pressure release of inactive sessions | Persist richer safe tab metadata (title and timestamps); verify process recreation, named-session/recently-closed persistence and crash recovery; profile memory policy on low-RAM devices. |
| 3. Bookmarks, folders, import/export | Bounded URL validation; stable generated bookmark IDs; default-folder migration from legacy URL-only data; create/rename/delete folders; move bookmarks; manual earlier/later ordering; versioned JSON export/import with size/URL limits and folder-ID/name remapping; title-aware search | Add Android-backed persistence and round-trip tests; add explicit duplicate/replace preview and conflict UI; consider drag reordering after accessible controls are in place. |
| 4. Home customization and themes | System/Light/Dark theme; Arabic/English/System language; shared palette; locale-script-aware RTL; persisted quick-link/discovery visibility and grid/list shortcut layout; add/edit/remove/reorder shortcuts; reset to defaults | Allow users to add/remove/reorder individual shortcuts and sections; resource-based multilingual UI; consolidate tokens and verify live changes, contrast and RTL on devices. |
| 5. Extensible architecture | Separate BrowserStore, permission/notification bridges, palette and several feature pages; documented lifecycle invariants | MainActivity remains a large coordinator; introduce feature contracts and small controllers/repositories; isolate tab/session coordination, privacy, library, settings and home preferences; add migrations and unit tests before UI rewrites. |
| 6. Low-memory performance | Single GeckoRuntime; background sessions open lazily; bounded tabs/bookmarks/history/download metadata; sessions deactivated when not visible; bounded recovery | Measure cold start, tab switching and memory on low-RAM devices; add lifecycle-aware background tab discard/reload policy with safeguards; avoid duplicate reads/rebuilds; test memory pressure and repeated process death. Never discard the active tab or unsaved user state. |

## Architecture target

Keep GeckoView ownership in the browser layer and keep UI screens independent of GeckoSession internals.

- **Browser core:** `BrowserRuntimeOwner`, `TabRepository`, `TabSessionCoordinator`.
- **Privacy:** `SitePermissionRepository`, `PrivacyController`, explicit capability and origin validation.
- **Library:** `BookmarkRepository`, `BookmarkFolderRepository`, `BrowserHistoryRepository`, import/export codec.
- **Preferences:** `ThemePreferences`, `HomePreferences`, language and appearance state.
- **Platform adapters:** Android permission launcher, notification bridge, DownloadManager adapter.
- **UI:** feature pages consume immutable state and callbacks; they do not create GeckoRuntime or own tab sessions.

These are target boundaries, not a claim that these classes already exist. Introduce them incrementally; do not move the whole application in one risky rewrite.

## Data and migration rules

1. Keep the existing SharedPreferences keys readable during migration.
2. Migrate URL-only bookmarks to generated stable IDs and a default folder exactly once; retain a rollback-safe legacy snapshot until migration succeeds.
3. Validate imported data before writing anything. Reject non-HTTP(S) URLs, credentials in URLs, oversized payloads and unsupported formats.
4. Write imports atomically: parse and validate first, then replace/merge in one committed operation.
5. Keep session persistence limited to safe serializable metadata. Never serialize GeckoSession, cookies, passwords, page form data or private browsing state.
6. Put hard limits on all persisted collections and imported file sizes.
7. Treat site permissions as origin-scoped; never infer site permission from Android app permission.

## Delivery order

### A. Security and privacy
- Audit permission origin normalization and every grant/reject/cancel path.
- Add site permission reset and supported site-data clearing.
- Add tests for malformed origins, navigation between prompt and response, denied Android permission and private notifications.

### B. Session reliability
- Named sessions and bounded lazy tab restoration are implemented; private tabs are excluded.
- Persist richer safe tab metadata and add recently-closed tab restore.
- Verify restore, close and process recreation; profile the existing inactive-session memory policy.

### C. Library
- Bookmark IDs, folder CRUD, ordering, migration and validated JSON backup are implemented in the current branch.
- Add Android-backed migration and import/export round-trip tests before release.
- Duplicate conflict preview and destructive-replace confirmation are implemented; verify malformed files, collisions and no partial writes.

### D. Home and appearance
- Add persisted home layout preferences and reset defaults.
- Consolidate theme tokens; preserve System/Light/Dark and Arabic/English/System.
- Verify RTL and contrast in every feature screen.

### E. Modularization and performance
- Extract tab/session, privacy, download, library and preference controllers/repositories in small commits.
- Keep MainActivity as a thin app shell over time.
- Add baseline measurements and a lifecycle-safe low-memory policy with safeguards for active and unsaved state.

### F. Accounts, sync and international reach
- Keep the browser fully usable without an account; sign-in must be optional.
- Add provider-neutral identity interfaces first; add Google, Microsoft and Apple adapters only after provider-specific security and consent review.
- Sync only user-selected data types; keep private tabs, cookies and site permissions out of cloud sync by default.
- Define encryption, key recovery, conflict resolution, offline behavior and account deletion/export before implementing a backend.
- Shared Android resource lookup now covers browser chrome, Settings, Downloads, Saved Pages, Site Info, Reader and Recall with English/Arabic resources; BCP 47 tags are validated, home-page direction follows RTL locale scripts, and Unicode NFKC search normalization is used in library/Recall.
- Add reviewed locale packs for more languages; finish custom home-page and secondary prompt localization, plurals, date/number formats, Unicode normalization and mixed-direction verification.

## Definition of done

- Each feature has a repository/controller boundary, bounded persistence and unit tests.
- Existing user data migrates without loss.
- Invalid imported data cannot partially overwrite saved data.
- Session restoration never exceeds the configured tab cap and never restores private state.
- Permission decisions remain origin-scoped and respect Android runtime permission.
- Theme and home preferences survive process recreation and reset cleanly.
- Background tab memory policy never silently loses active or unsaved state.
- CI passes for unit tests, lint, ABI APK builds and package verification.
- Physical-device verification is recorded separately from CI; no device test is implied by a green build.
