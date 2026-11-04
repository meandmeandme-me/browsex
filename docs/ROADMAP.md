# BrowseX implementation roadmap

This file records implementation state separately from device verification. A checked implementation item is not a claim that it has passed the final build or physical-device QA.

## Phase 0 — engineering baseline

- [x] Keep all work on `rewrite-v2`; do not modify `main`.
- [x] Record architecture, lifecycle rules, compatibility targets and feature contracts.
- [x] Extract URL parsing/validation into a pure policy with unit tests.
- [x] Add CI gates for unit tests, lint, APK verification and checksums.
- [x] Add pure tests for persisted URL validation, duplicate tabs, storage limits and search-engine fallback.
- [ ] Add Android-backed persistence tests for BrowserStore.
- [ ] Establish a stable release signing/update strategy; debug signatures are not a release strategy.

## Phase 1 — browser foundation

- [x] Validate external HTTP/HTTPS intents and reject unsafe schemes.
- [x] Accept internationalized domain names using IDNA ASCII validation while rejecting credentials, malformed authorities and invalid ports.
- [x] Persist and restore a bounded, URL-validated tab snapshot and active tab.
- [x] Enforce the 20-tab cap for regular and popup tab creation.
- [x] Restore tab metadata without opening every saved tab at startup; open only the active session and load background tabs on demand.
- [x] Add Gecko private-mode tabs; exclude their URLs from persisted sessions and history, inherit private mode for popup tabs, and close their session data with the tab.
- [x] Add BrowseX Recall to search regular open tabs, bookmarks and recent history from one surface; private tabs are intentionally excluded.
- [x] Save, list, reopen and delete up to ten named regular-tab sessions; private tabs are never included in a saved session.
- [x] Extract process-wide GeckoRuntime ownership and the tab/session state model from MainActivity into a dedicated browser-core file.
- [x] Restore existing tabs before opening a new external HTTP(S) link, without exceeding the 20-tab limit.
- [x] Deactivate inactive GeckoSessions on tab switch and Activity stop; persist state before teardown.
- [x] Add one automatic recovery attempt for crashed content sessions and a manual retry path after repeated crashes.
- [ ] Verify process recreation, tab switching, named-session persistence and GeckoSession lifecycle on Android 10 and newer.
- [ ] Verify back/forward, fullscreen, popup/new-session and share flows on device.

## Phase 2 — permissions, notifications and downloads

- [x] Add Android runtime permission handling for location, camera and microphone requests coming from GeckoView.
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
- [x] Add a dedicated response-stream writer for Gecko external responses; save the received response body directly without re-fetching, including POST/authenticated/one-time URL responses; persist completed/failed records and expose them in Downloads.
- [ ] Add live byte progress, cancellation, recoverable interrupted state and safe retry/resume only when the original response can be continued; add Android-backed tests for GET, POST and authenticated responses.
- [ ] Add Android-backed tests for notification callbacks, permission callbacks, privacy clearing callbacks and download registry behavior.

## Phase 3 — design coverage and accessibility

- [x] Add shared System/Light/Dark palette and preferred Gecko web-content color scheme.
- [x] Add Arabic/English/System language choices and apply RTL to the local home page and core native screens.
- [x] Apply theme palette to home, browser chrome, Settings, Site Info, Downloads, Reader and Saved Pages.
- [x] Improve primary control touch targets and accessible labels.
- [ ] Produce a complete Uizard screen/flow map from the product requirements and compare every delivered screen against the approved Uizard direction.
- [ ] Finish translations for every secondary prompt and toast; some uncommon strings remain English.
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
- [ ] Expand Android string resources beyond the initial English/Arabic browser-chrome migration; add locale packs and resource-based translation for Settings, Downloads and Library screens, plus plural/number/date formatting.
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
