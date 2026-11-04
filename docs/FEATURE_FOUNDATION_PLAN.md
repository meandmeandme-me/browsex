# BrowseX — Feature Foundation Plan

> Scope: engineering foundations for the six requested capabilities. This plan is based on the current `rewrite-v2` source tree and separates implemented behavior from missing work. It does not claim device verification.

## Product direction

- Keep the existing Figma interface as a reference only; do not redesign or replace it during this foundation phase.
- Build and verify browser capabilities behind stable interfaces first.
- Choose a new visual reference and create a new UI direction only after the foundations are stable.
- Keep ad blocking explicitly deferred.

## Current implementation audit

| Area | Already present in `rewrite-v2` | Remaining work |
|---|---|---|
| 1. Site privacy and security | HTTP(S)-only navigation validation; Android runtime permission flow for location/camera/microphone; per-site content permission controls; camera/microphone decisions stored by origin; website notification bridge; private notification suppression; Android app notification setting; privacy center for selective cookies, DOM storage, cache, site permissions and auth-session clearing after closing sessions | Add Android-backed coverage for clear-data completion/failure and prompt cancellation; harden origin canonicalization and permission expiry/reset. Do not promise revocation for Gecko grants GeckoView cannot revoke. |
| 2. Sessions and tab recovery | One process-wide GeckoRuntime; per-tab GeckoSession ownership; validated persisted regular-tab URL snapshot; active tab restoration; 20-tab cap; lazy opening of background tabs; one automatic content-process recovery attempt; Gecko private-mode tabs excluded from history and persistence; BrowseX Recall across regular tabs and library | Persist richer safe tab metadata (title and timestamps); explicit restore-last-session and close-session behavior; robust crash/restart tests; process-recreation tests; add a safe memory-pressure policy for inactive tabs only. |
| 3. Bookmarks, folders, import/export | Bounded, validated URL bookmarks; search and remove; browsing history storage and clearing | Add stable bookmark IDs, folders and ordering; migration from legacy URL-only list; import/export with strict size/URL validation and duplicate handling; user-visible backup/restore flow. |
| 4. Home customization and themes | System/Light/Dark theme; Arabic/English/System language; shared palette; local home page receives theme/language settings; RTL layout follows RTL locale scripts independently from Arabic translation availability | Persist home layout preferences (quick links/sections); allow safe customization and reset; consolidate theme tokens and ensure every screen uses them; test live theme changes and RTL. |
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
- Define serializable tab metadata and migration version.
- Add explicit restore/close behavior and process-recreation tests.
- Add a safe memory-pressure policy for inactive tabs only.

### C. Library
- Introduce bookmark IDs and folders with legacy migration.
- Add validated import/export and round-trip tests.
- Keep history and bookmarks separate.

### D. Home and appearance
- Add persisted home layout preferences and reset defaults.
- Consolidate theme tokens; preserve System/Light/Dark and Arabic/English/System.
- Verify RTL and contrast in every feature screen.

### E. Modularization and performance
- Extract feature controllers/repositories in small commits.
- Keep MainActivity as a thin app shell over time.
- Add baseline measurements and low-memory regression checks.

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
