# BrowseX implementation roadmap

This file is the source of truth for feature sequencing. Keep each task small and buildable.

## Phase 0 — engineering baseline

- [x] Keep all work on `rewrite-v2`; protect `main` from experimental changes.
- [x] Record architecture, lifecycle rules, compatibility targets and feature contracts.
- [x] Record known incomplete features and test gates.
- [x] Add JVM unit tests for URL normalization, malformed URLs and unsafe schemes.\n- [ ] Add BrowserStore persistence tests (requires Android test doubles or instrumentation).
- [x] Add CI checks for unit tests, lint, APK signature verification and checksums.
- [ ] Verify APK signing/update strategy for repeat installs.

## Phase 1 — browser foundation

- [x] Extract URL parsing/validation from MainActivity into a pure tested policy.
- [x] Add tests for URL schemes, search queries and malformed URLs.\n- [ ] Add external-intent validation tests.
- [ ] Verify tab lifecycle, process recreation and session switching.
- [ ] Verify back/forward, fullscreen, popup/new-session and share flows.

## Phase 2 — core product screens

- [ ] Replace Settings AlertDialog with a full settings screen and persisted preferences.
- [ ] Implement real GeckoView download interception and a BrowseX downloads screen.
- [x] Implement initial reader mode with GeckoView page extraction and accessible native typography.\n- [ ] Add readerability/boilerplate-removal tuning when supported by the pinned GeckoView API.
- [ ] Implement bookmarks/history empty states, search and deletion confirmation.
- [x] Add read-only site information and current GeckoView permission visibility.\n- [ ] Add site permission grant/revoke flows and Android-version-aware notification permission handling.

## Phase 3 — design coverage

- [ ] Compare each implemented screen against its Figma frame and record deviations.
- [ ] Complete home/start screen states and visual tab workspace.
- [ ] Complete downloads, reader, alerts/notifications, share sheet and settings screens.
- [ ] Complete landscape and tablet layouts.
- [ ] Verify touch targets, contrast, font scaling, RTL and narrow 720px-wide devices.

## Phase 4 — release hardening

- [ ] Build debug and release variants for supported ABIs.
- [ ] Inspect APK contents, package IDs, versioning and signatures.
- [ ] Run Android 10/API 29 smoke tests and current Android smoke tests.
- [ ] Verify cold start, offline/error page, downloads, external links and rotation.
- [ ] Publish only artifacts from a green commit; attach SHA-256 checksums and release notes.

## Explicitly deferred

- Ad blocking / uBlock integration is intentionally deferred. Do not expose a working toggle or claim protection until extension installation, permissions, persistence and real blocking behavior are tested.
