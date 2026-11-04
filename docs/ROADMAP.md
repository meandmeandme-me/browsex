# BrowseX implementation roadmap

This file records implementation state separately from device verification. A checked implementation item is not a claim that it has passed the final build or physical-device QA.

## Phase 0 — engineering baseline

- [x] Keep all work on `rewrite-v2`; do not modify `main`.
- [x] Record architecture, lifecycle rules, compatibility targets and feature contracts.
- [x] Extract URL parsing/validation into a pure policy with unit tests.
- [x] Add CI gates for unit tests, lint, APK verification and checksums.
- [x] Add pure tests for persisted URL validation, duplicate tabs, storage limits and search-engine fallback.
- [ ] Add Android-backed persistence tests for BrowserStore.
- [x] Add environment-based release signing and an optional CI path that verifies/uploads signed release APKs when secrets are configured.
- [ ] Configure protected release-signing secrets, define update publication/recovery policy and validate signed upgrades on devices.
- [x] Add a locale-resource integrity validator to CI for missing keys, placeholder parity and Android locale-config registration.
- [ ] Expand reviewed locale packs beyond English and Arabic; validate mixed-direction, plural, date/number, TalkBack and font-fallback behavior for each pack.

## Phase 1 — browser foundation

- [x] Validate external HTTP/HTTPS intents and reject unsafe schemes.
- [x] Accept internationalized domain names using IDNA ASCII validation while rejecting credentials, malformed authorities and invalid ports.
- [x] Persist and restore a bounded, URL-validated tab snapshot and active tab.
- [x] Enforce the 20-tab cap for regular and popup tab creation.
- [x] Restore tab metadata without opening every saved tab at startup; open only the active session and load background tabs on demand.
- [x] Add Gecko private-mode tabs; exclude their URLs from persisted sessions and history, inherit private mode for popup tabs, and close their session data with the tab.
- [x] Add BrowseX Recall to search regular open tabs, bookmarks and recent history from one surface; private tabs are intentionally excluded.
- [x] Save, list, reopen and delete up to ten named regular-tab sessions; private tabs are never included in a saved session.
- [x] Keep up to ten recently closed regular tabs and restore them from the tab workspace; private tabs are never recorded.
- [x] Extract process-wide GeckoRuntime ownership and the tab/session state model from MainActivity into a dedicated browser-core file.
- [x] Extract tab membership/selection into BrowserTabRegistry and isolate pure GeckoSession budget/memory-pressure policy.
- [x] Move tab workspace UI and saved/recent session actions into TabsPage with injected browser callbacks.
- [x] Centralize tab close cleanup (pending site/Android permission rejection, session deactivation and close) in BrowserSessionLifecycleCoordinator.
- [x] Use one deterministic GeckoSession teardown path for tab close, session reset, inactive-session release and Activity shutdown; clear per-session delegates/permissions even when cleanup callbacks fail.
- [x] Extract GeckoSession progress/navigation/content delegate wiring behind a BrowseX-owned callback contract.
- [x] Extract bounded Gecko content-process crash recovery into a coordinator with host-injected UI, runtime and permission callbacks.
- [x] Move tab creation, activation, close and popup-session orchestration into BrowserTabController with injected platform callbacks.
- [x] Extract Android runtime-permission request ordering, cancellation and teardown rejection into BrowserAndroidPermissionQueue; Activity Result launchers remain host-owned.
- [x] Move startup restoration, external-link tab selection and post-privacy-clear tab restoration into BrowserTabController; keep persistence decoding and snapshot policy in the Activity/store boundary.
- [x] Split BrowserStore responsibilities into preference, session, library and download stores while preserving existing SharedPreferences keys and the feature-facing facade.
- [x] Extract tab snapshot persistence policy into BrowserTabPersistenceCoordinator and BrowserTabPersistencePolicy; tab membership, selection, activation, popup, restoration and Android permission queue orchestration are isolated.
- [ ] Migrate the focused stores from SharedPreferences to Room/DataStore with versioned, non-destructive migrations and Android-backed upgrade tests.
- [x] Restore existing tabs before opening a new external HTTP(S) link, without exceeding the 20-tab limit.
- [x] Deactivate inactive GeckoSessions on tab switch and Activity stop; persist state before teardown.
- [x] Add one automatic recovery attempt for crashed content sessions and a manual retry path after repeated crashes.
- [ ] Verify process recreation, recently-closed restore, tab switching, named-session persistence and GeckoSession lifecycle on Android 10 and newer.
- [ ] Verify back/forward, fullscreen, popup/new-session and share flows on device.

## Phase 2 — permissions, notifications and downloads

- [x] Add Android runtime permission handling for location, camera and microphone requests coming from GeckoView.
- [x] Enable GeckoView's built-in tracking protection for every regular and private browsing session; this is tracker protection, not a complete ad blocker.
- [x] Add per-site content permission prompts with Allow, Block and Not now outcomes.
- [x] Add Site Info controls to change stored content permissions to Allow, Block or Ask; persistent storage has an explicit non-revocable state.
- [x] Add a privacy center for selecting GeckoView cookies, DOM storage, cache, authentication-session and site-permission data to clear; close all sessions before clearing and restore validated tabs afterward.
- [x] Add host-scoped site-data clearing from Site Info, with an explicit warning about sign-out and third-party host boundaries.
- [x] Enrich local history with page titles and visit timestamps while migrating legacy URL-only history.
- [x] Store camera/microphone decisions by validated site origin and separately require Android runtime permission.
- [x] Bridge Gecko web notifications to Android NotificationManager channels, including Android 13+ POST_NOTIFICATIONS and tap/dismiss callbacks.
- [x] Suppress website notifications for private-browsing notifications and honor the app-level notification switch.
- [x] Map DownloadManager pending/running/paused/successful/failed/unknown states and Android reason codes.
- [x] Report missing DownloadManager records and allow their BrowseX registry entries to be removed.
- [x] Sanitize and reserve collision-safe filenames for downloads started by BrowseX.
- [x] Request legacy external-storage permission only on Android API 26–28.
- [x] Add a dedicated response-stream writer for Gecko external responses; save the received response body directly without re-fetching, including POST/authenticated/one-time URL responses.
- [x] Persist queued/running/completed/failed/cancelled stream-download states, byte progress and known Content-Length; show live progress and cancellation in Downloads; delete partial output on handled cancellation/failure.
- [x] Keep stream-download cancellation terminal-state persistence in the writer completion callback, avoiding duplicate cancellation writes from the UI coordinator.
- [x] Mark unfinished stream downloads interrupted on the next app startup; do not claim process-death continuation.
- [ ] Add safe retry/resume only when the original response can be continued; one-shot POST/authenticated responses are not resumable by default.
- [ ] Add Android-backed tests for GET, POST, authenticated responses, progress, cancellation, interrupted records and partial-file cleanup.
- [ ] Add Android-backed tests for notification callbacks, permission callbacks, privacy clearing callbacks and download registry behavior.

## Phase 3 — design coverage and accessibility

- [x] Add shared System/Light/Dark palette and preferred Gecko web-content color scheme.
- [x] Add Arabic/English/System language choices and apply RTL to the local home page and core native screens.
- [x] Apply theme palette to home, browser chrome, Settings, Site Info, Downloads, Reader and Saved Pages.
- [x] Improve primary control touch targets and accessible labels.
- [ ] UI/visual design is user-owned and intentionally out of this implementation task. The earlier Figma reference is superseded; do not design screens or treat it as acceptance criteria. Resume UI implementation only from the user's later-approved design/specification.
- [ ] Finish translations for remaining secondary prompts/toasts and the custom home page; add language-specific plural/date/number formatting.
- [ ] Verify compact, landscape, tablet, large-font and screen-reader layouts on devices.

## Phase 4 — release hardening

- [x] CI workflow defines unit-test, lint, ABI APK build, package verification and checksums.
- [x] Run the final comprehensive CI pass for commit `c015a4f`; JVM tests, lint, ABI builds, APK verification and artifact uploads succeeded.
- [ ] Run Android 10/API 29 smoke tests and current Android smoke tests.
- [ ] Verify cold start, offline/error page, downloads, external links, theme switching and process recreation.
- [ ] Configure stable release signing and version/update policy.
- [ ] Publish only artifacts from a green commit; attach SHA-256 checksums and release notes.

## Phase 5 — global product foundations

- [x] Add bounded bookmark folders, safe legacy migration, validated JSON import/export, title-aware search and manual ordering.
- [x] Persist home section visibility and shortcut layout preferences with reset behavior.
- [x] Add, edit, remove and move home shortcuts earlier/later with saved ordering and phone-safe editor controls.
- [x] Move browser chrome, Settings, Downloads, Saved Pages, Site Info, Reader and Recall labels into shared Android resource lookup with English/Arabic resources.
- [x] Normalize Unicode compatibility forms and case for bookmark/history/Recall search.
- [x] Accept validated BCP 47 app language tags and preserve device locale/RTL direction in the home page.
- [ ] Add professionally reviewed locale packs beyond English/Arabic; complete plural/number/date formatting and translation coverage for every feature screen. Unsupported locales currently fall back to English.
- [ ] Complete locale-aware mixed RTL/LTR rendering, font fallback, Unicode search normalization and accessibility verification.
- [ ] Define provider-neutral account identity and consent boundaries before adding Google, Microsoft, Apple or other identity providers.
- [ ] Define opt-in, per-data-type sync contracts, encryption/key ownership, conflict resolution, offline queue and account deletion/export before any cloud sync.
- [ ] Research and prioritize user-facing capabilities using `WORLD_BROWSER_PRODUCT_REQUIREMENTS.md`; do not clone feature lists without validating value, cost and privacy.
- [x] Extract the response-stream download writer and its persistence model from MainActivity.
- [ ] Continue extracting tab/session, privacy, library and preference coordinators; reduce MainActivity's remaining orchestration responsibilities.
- [x] Add a basic lifecycle-aware inactive-tab policy: keep the active tab, bound warm sessions, and close/recreate inactive sessions under memory pressure.
- [ ] Measure cold start, tab switching, peak memory and recovery on low-RAM devices; tune the policy from measurements.

## Explicitly deferred

- Ad blocking / uBlock integration is intentionally deferred. Do not expose a working toggle or claim protection until extension installation, permissions, persistence and real blocking behavior are tested.

## Phase 6 — accounts, sync and future clients

- [x] Add provider-neutral account/sync domain models with stable identity subjects, connection states, per-category consent, device metadata and versioned sync-record metadata.
- [x] Bind the password vault AES-GCM key to Android Keystore user authentication with a 60-second authorization window; migrate legacy v1 ciphertext after successful authentication.
- [ ] Add Android-wide AutofillService with explicit app-level consent and authenticated datasets; keep website credentials origin-scoped and never expose private browsing data.
- [ ] Evaluate WebAuthn/passkey support through the Android credential ecosystem and GeckoView capabilities; do not advertise passkeys before end-to-end tests.
- [ ] Define secure credential sync separately from bookmarks, including end-to-end encryption, device enrollment, recovery, rotation and revocation; credentials remain local until this is approved.
- [x] Define future Windows, macOS, Linux and Android TV product boundaries without creating empty app modules.
- [x] Define the account center, provider connection, sync overview, category consent, device management, conflict resolution and privacy/data screen flows for Uizard.
- [ ] Define backend API, account lifecycle, retention policy, threat model, encryption/key ownership and recovery.
- [ ] Implement a secure platform credential vault and verified OIDC Authorization Code + PKCE flow.
- [ ] Build real account UI and device registration/revocation; keep unsupported providers unavailable rather than fake.
- [ ] Implement opt-in encrypted bookmark sync first, then offline queue, idempotency, conflict resolution and schema migration.
- [ ] Add cross-device export/deletion, disconnect and local-data-retention flows.
- [ ] Extract stable pure Kotlin domain/sync code into KMP shared modules only after Android boundaries and tests are stable.
- [ ] Evaluate Windows, macOS and Linux browser engines independently; document licensing, security updates, sandboxing, extensions and platform integration.
- [ ] Design and verify Android TV remote/focus navigation, media behavior and low-memory constraints.
- [ ] Build platform clients and release pipelines only after the relevant engine and UX decisions are approved.
- [x] Evaluate account backend options and record a reversible Supabase/PostgreSQL + Auth + RLS direction behind BrowseX-owned interfaces.
- [ ] Complete Android/desktop PKCE proof of concept, RLS isolation tests, encryption/key recovery threat model and deployment decision before wiring provider UI.
