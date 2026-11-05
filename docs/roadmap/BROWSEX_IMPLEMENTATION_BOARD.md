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
- Exact Room v2 schema snapshot is now committed at `app/schemas/com.browsex.app.storage.BrowseXDatabase/2.json` (commit `5498121d64cb9f7b09c5fc04a65a14e48a73f697`); CI generated it and the push workflow committed it automatically.
- Remaining: finish every legacy read/write cutover and verify migration parity.

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

- [x] Exact Room schema v2 snapshot committed; CI migration/unit test job passed on the generating commit.
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


## Latest implementation and verification — 2026-11-05

- CI run [#42088452485](https://github.com/meandmeandme-me/browsex/actions/runs/42088452485) passed for code commit `99c381744694148846068b36e9bff6b0f5f64da1`: unit tests, lint, locale validation, debug APK assembly, ABI/package/signature verification and artifact upload.
- Debug artifacts produced: ARM32, ARM64 and universal. Signed release APK generation was skipped because release signing is not configured.
- The exact Room v2 schema was generated, uploaded, and then committed by the successful push workflow run `42089582128`; source file: `app/schemas/com.browsex.app.storage.BrowseXDatabase/2.json` (commit `5498121d64cb9f7b09c5fc04a65a14e48a73f697`, identity hash `a8bb5bdfbd75a05ec09fb4f5ee8c5062`).
- Downloads UI now refreshes while Android DownloadManager jobs are active, and stream completion no longer reports success if the Room completion transition fails.
- Site information exposes stored geolocation and notification decisions even when there is no current Gecko permission row, and persists changes made there.
- Bookmark ordering is now stable per folder: new bookmarks append, moving folders appends to the destination, and the organization menu supports moving bookmarks up/down within a folder.

## Remaining work (not yet claimed complete)

- [x] Commit the exact generated Room schema JSON. CI push run `42089582128` completed the schema-commit step successfully; committed schema identity hash is `a8bb5bdfbd75a05ec09fb4f5ee8c5062`.
- [ ] Add a dedicated schema/migration parity assertion and keep it in the release gate.
- [ ] Finish legacy storage cutover and migration parity review.
- [ ] Complete DownloadManager enqueue/reconciliation and pause/resume/retry semantics; current Gecko response-body downloads use BrowseX's bounded stream writer.
- [ ] Complete API-level and device permission/privacy testing.
- [ ] Finish translation coverage beyond Arabic/English fallback, RTL, TalkBack and large-font audits.
- [ ] Add BrowseX-owned per-site uBlock exception UX and extension update/recovery coverage.
- [ ] Verify install/update behavior on Android 10 and the target TECNO device; CI artifact validation is not a substitute for device installation.


## Verification update — schema snapshot

- Pull-request CI run `42089596752` passed tests, lint, locale validation, debug APK assembly, APK verification and artifact upload; its schema-commit step was correctly skipped because it was a pull-request event.
- Push CI run `42089582128` passed the same build checks and successfully executed `Commit generated Room schema snapshot`.
- GitHub branch `rewrite-v2` now contains the generated schema commit `5498121d64cb9f7b09c5fc04a65a14e48a73f697`. This verifies source-control persistence of the generated schema, not Android device installation or runtime behavior.
- Release APK signing remains skipped because release signing secrets are not configured; the produced artifacts are debug APKs.
