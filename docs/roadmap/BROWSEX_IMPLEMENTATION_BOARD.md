# BrowseX Implementation Board

> Working branch: `rewrite-v2`  
> Tracking epic: [Issue #3 — Complete BrowseX](https://github.com/meandmeandme-me/browsex/issues/3)

This is the repository-side phase board for the A–F implementation. Each phase has a dedicated GitHub issue with its detailed checklist, known gaps and acceptance criteria.

| Phase | Workstream | Issue | Current state |
|---|---|---:|---|
| A | Storage migration and Room production wiring | [#5](https://github.com/meandmeandme-me/browsex/issues/5) | In progress |
| B | Tabs, sessions, restore and memory safety | [#6](https://github.com/meandmeandme-me/browsex/issues/6) | In progress |
| C | Downloads and Android integration | [#7](https://github.com/meandmeandme-me/browsex/issues/7) | In progress |
| D | Website permissions, notifications and privacy | [#8](https://github.com/meandmeandme-me/browsex/issues/8) | In progress |
| E | Bookmarks, history, settings, localization and accessibility | [#9](https://github.com/meandmeandme-me/browsex/issues/9) | In progress |
| F | Ad blocking and advanced browser protections | [#10](https://github.com/meandmeandme-me/browsex/issues/10) | In progress |

## Current implementation baseline

### A — Storage
- Room repositories exist for bookmarks, history, tabs, sessions, downloads and site permissions.
- Application-scoped storage runtime performs lazy, one-time legacy migration before Room operations.
- History writes/clear, bookmark backup import/export, and multiple feature reads/writes now use Room.
- DataStore now owns core browser settings; a compatibility mirror keeps older readers aligned.
- Remaining: finish every legacy read/write cutover, commit the exact generated Room schema snapshot, and verify migration parity.

### B — Tabs and sessions
- Regular-tab restore/save uses Room and excludes private tabs.
- Recently closed tabs and named sessions use Room.
- Recently-closed consumption is transactional.
- Remaining: race/crash/memory-pressure hardening and full lifecycle verification.

### C — Downloads
- Streamed download metadata and progress are persisted through Room.
- SQL state transitions are atomic; queued records move to running before transfer.
- Downloads UI reads Room stream records and Room platform DownloadManager IDs.
- Remaining: complete Android DownloadManager enqueue/reconciliation, resume/retry semantics, cleanup edge cases and device coverage.

### D — Permissions and privacy
- Per-origin camera, microphone, geolocation and notification decisions use Room.
- Android permission-denial history uses DataStore.
- Site information, notification bridge and browsing-data clear paths use Room permission decisions.
- Remaining: full Android/API permission matrix, lifecycle cancellation tests and privacy audit.

### E — Library and settings
- Room-backed bookmarks/history page supports search, folder create/rename/delete, move, remove and clear.
- Bookmark backup preview/import/export supports merge and replace.
- Settings screen and homepage preferences use DataStore for core settings.
- Remaining: remove all legacy compatibility reads/writes, complete reorder UX, full translations/RTL/accessibility audit.

### F — Ad blocking
- Official Mozilla Add-ons uBlock Origin install/enable/disable/uninstall, private-mode consent, session attachment and site-action popup are already implemented.
- Global/private state now persists through DataStore.
- Remaining: update/version lifecycle verification, BrowseX-owned per-site exception UX, recovery tests and low-memory performance measurements.

## Release gates

- [ ] Exact Room schema v2 snapshot committed and migration tests pass.
- [ ] No private browsing state is persisted.
- [ ] No production database work blocks the UI thread.
- [ ] All user-facing controls are connected to real behavior.
- [ ] Unit/Robolectric/instrumented tests pass on the final commit.
- [ ] CI passes on the exact final commit.
- [ ] Android 10/API 29 and newer Android permission/storage behavior verified.
- [ ] ARM32, ARM64 and tablet layouts verified where devices are available.
- [ ] Release APK is reproducible and signed; installation is tested on the target device.
- [ ] Remaining limitations are documented; no unverified device test is claimed.

**Important:** This file is a repository-side implementation board. GitHub Project V2 board creation is not exposed by the connected GitHub actions in this workspace; the live task cards are the linked Issues above.
