# BrowseX implementation roadmap

This file is the source of truth for feature sequencing. Keep each task small, tested, and buildable.

## Phase 0 — engineering baseline

- [x] Keep all work on `rewrite-v2`; do not modify `main`.
- [x] Record architecture, lifecycle rules, compatibility targets and feature contracts.
- [x] Extract URL parsing/validation into a pure policy with unit tests.
- [x] Add CI gates for unit tests, lint, APK verification and checksums.
- [ ] Add Android-backed persistence tests for BrowserStore.
- [ ] Establish a stable release signing/update strategy; debug signatures are not a release strategy.

## Phase 1 — browser foundation

- [x] Validate external HTTP/HTTPS intents and reject unsafe schemes.
- [x] Persist and restore a bounded, URL-validated tab snapshot and active tab.
- [x] Enforce the 20-tab cap for regular and popup tab creation.
- [ ] Verify process recreation, tab switching and GeckoSession lifecycle on Android 10 and newer.
- [ ] Verify back/forward, fullscreen, popup/new-session and share flows on device.

## Phase 2 — core product screens

- [x] Add settings, bookmarks, history, reader mode and read-only site information screens.
- [x] Route GeckoView external responses to Android DownloadManager.
- [x] Track BrowseX-owned DownloadManager IDs and filter the Downloads screen so it does not list other apps' downloads.
- [x] Support opening completed downloads and removing/cancelling queued or active downloads.
- [ ] Improve download fidelity for POST requests and custom authorization headers; current DownloadManager integration re-fetches the response URL and only forwards cookies and a default User-Agent.
- [ ] Add durable Android-backed tests for the download registry and device tests for redirects, duplicate names, failures and cancellation.
- [ ] Implement site permission prompts using the exact pinned GeckoView 150 API, including Android runtime permissions for location/camera/microphone.
- [ ] Implement web notifications end-to-end: content permission, GeckoView WebNotificationDelegate, Android notification channels and API 33+ runtime permission.
- [ ] Add permission revoke controls using GeckoView's stored ContentPermission objects.

## Phase 3 — design coverage and accessibility

- [ ] Compare each implemented screen against its Figma frame and record deviations.
- [ ] Complete home/start screen states and visual tab workspace.
- [ ] Complete downloads, reader, alerts/notifications, share sheet and settings screens.
- [ ] Complete landscape and tablet layouts.
- [ ] Verify touch targets, contrast, font scaling, RTL and narrow 720px-wide devices.

## Phase 4 — release hardening

- [x] CI builds debug APK outputs for ARM32, ARM64 and universal output and verifies package ID, minSdk, native libraries, signatures and checksums.
- [ ] Run Android 10/API 29 smoke tests and current Android smoke tests.
- [ ] Verify cold start, offline/error page, downloads, external links and rotation.
- [ ] Configure stable release signing and version/update policy.
- [ ] Publish only artifacts from a green commit; attach SHA-256 checksums and release notes.

## Explicitly deferred

- Ad blocking / uBlock integration is intentionally deferred. Do not expose a working toggle or claim protection until extension installation, permissions, persistence and real blocking behavior are tested.
