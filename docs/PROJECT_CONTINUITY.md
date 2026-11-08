# Wren Browser — Project Continuity

> **Source of truth:** active implementation checkpoint for branch `wren-design-prep`. Update this file after meaningful source, design, storage, or release changes. Historical notes below must not override the current product decisions.

## Current checkpoint

- Repository: `meandmeandme-me/browsex`
- Active working branch: `wren-design-prep`
- Current HEAD at this checkpoint: `7fbb376334fbbe96d281001758387b3722459ea9`
- Pull request: [#11 — Wren design preparation](https://github.com/meandmeandme-me/browsex/pull/11), open and not merged.
- Keep all new implementation on `wren-design-prep`; do not merge into `main` without the owner's explicit instruction.
- CI has been enabled for this working branch. The first UI build/test run was started while the source was still being updated; always check the latest run against the latest branch HEAD before claiming a green build.
- The Android app uses GeckoView. The existing engine, downloads, privacy, settings, permissions, storage and session logic must be preserved while the UI is redesigned.

## Product and collaboration decisions

- Product and visible brand name: **Wren Browser**. The user supplied the Wren bird logo in light and dark presentations and approved this identity.
- Approved UI direction (2026-11-08): familiar, highly usable mobile-browser interaction patterns inspired by Google Chrome and Safari, implemented as Wren's own design—not copied source code, proprietary assets, or exact trademark artwork.
- Primary phone layout: the address/search field is anchored near the bottom for thumb reach, inside a refined glass-style dock. Back/forward, home, bookmark, tabs and menu actions sit in the same bottom control area. A small Wren/site identity remains at the top.
- Use translucent surfaces, subtle outlines, restrained elevation, rounded geometry, clear contrast and consistent iconography. Avoid excessive blur or low-contrast glass.
- Android minSdk is API 26. Use real backdrop blur only when supported and retain a readable translucent fallback on Android 8–11; never make core navigation depend on a blur effect.
- Light, dark and system themes must use the same semantic tokens. Arabic RTL and English LTR remain first-class; directional controls mirror while brand marks and universal symbols do not.
- Work directly in the connected GitHub repository, explain results in simple Arabic, and avoid asking the owner to repeat already-confirmed decisions.
- Preserve current features and user data. Do not remove old storage consumers, browser engine paths, download response handling, private-mode guarantees, permission flows, or account security foundations as a shortcut for UI work.
- No false UI: sign-in, cloud sync, translation, extensions or ad-block status must not appear active unless the underlying feature is implemented and verified.
- Target Android API 26+, including Android 10 and lower-memory devices. Test compact phones, large phones, landscape, foldables and tablets; device QA is separate from CI.

## What is implemented at current HEAD

### Storage and core data
- Application-scoped `BrowserStorageRuntime` initializes the Room graph and recovers interrupted stream-download records.
- Room v2 schema snapshot is committed at `app/schemas/com.browsex.app.storage.BrowseXDatabase/2.json` (identity hash `a8bb5bdfbd75a05ec09fb4f5ee8c5062`).
- Room-backed repositories are used for bookmarks/folders, history, regular tab snapshots, named sessions/recently closed tabs, download metadata and per-origin site permissions.
- DataStore is the primary source for core settings and Android permission-denial history. Some compatibility mirroring/read paths remain; remove them only after call-site audit.
- Legacy SharedPreferences-to-Room/DataStore import has been removed as agreed. Do not add it back.
- No production `runBlocking` was found in the previous source audit; keep database work off the UI thread.

### Tabs and sessions
- Regular tab snapshots are persisted through Room; private tabs and their URLs are excluded.
- Startup restore is asynchronous and guarded against persisting an empty snapshot before restore completes.
- Named sessions and recently closed regular tabs use Room; recently closed consumption is transactional.
- Tab cap is 20; inactive Gecko sessions are released under the live-session budget and memory-pressure policies.
- Remaining: expand restore/process-death/race tests, verify lifecycle callbacks and memory pressure on real low-memory hardware, and inspect remaining fallback paths.

### Downloads
- Gecko `WebResponse` downloads preserve the original one-shot response body and use BrowseX's bounded background writer. Do not replace this path with a simple URL re-fetch: cookies, headers, POST bodies and response fidelity may be lost.
- Stream metadata, filename reservation, progress, completion/failure/cancellation and interrupted recovery are persisted through Room.
- Stream progress Room writes are throttled to 1 MiB increments; UI progress remains more frequent.
- Completed stream downloads support open/share and guarded deletion of files BrowseX can verify it owns.
- Android DownloadManager UI can query only explicitly tracked IDs and supports open/share/remove for successful tracked jobs.
- **Known gap:** there is no production `DownloadManager.enqueue()` plus Room ID-registration/reconciliation path. Do not claim platform-managed download ingestion, restart reconciliation, retry or resume is complete. Only add DownloadManager for requests that can be faithfully represented; keep original Gecko response bodies on the stream writer.
- A cancellation UI bug was found after HEAD: do not remove a tracked Room ID unless `DownloadManager.remove(id)` reports success. The follow-up fix is being applied.

### Permissions, privacy and notifications
- Per-origin camera, microphone, geolocation and website-notification decisions use Room.
- Android runtime permission handling is separate from website permission consent; notification delivery uses Android notification APIs and respects the global DataStore setting.
- Private sessions avoid persistent per-origin decisions and browsing history.
- Remaining: Android API-level permission matrix, cancellation/race testing across tab changes, notification delivery/tap/close verification and full privacy-clear audit on devices.

### Ad blocking
- Official Mozilla Add-ons uBlock Origin install/enable/disable/uninstall flow, extension identity check, private-mode consent, session attachment and extension action popup are implemented.
- Gecko Safe Browsing / tracking protection policy remains the fallback where uBlock is not active.
- Remaining: real-device installation/update/recovery tests, low-memory performance measurements, site-breakage tests and end-to-end verification on real sites.
- Do not claim universal anti-adblock bypass or show a blocked counter.

### Library, settings and localization
- Room-backed bookmark/history UI supports search, folders, moves, ordering, removal and clear operations; bookmark backup preview/import/export supports merge and replace.
- Core theme/language/search/homepage/notification/ad-block preferences use DataStore.
- Arabic and English string resources exist, with fallback behavior for other device locales.
- Remaining: remove residual compatibility reads/writes after audit, verify mixed RTL/LTR and all dialogs/menus, broaden translated locale packs, and complete TalkBack/large-font/tablet checks.

## Remaining work — ordered

1. **Downloads correctness:** finish cancellation/removal correctness; decide whether a real DownloadManager adapter is needed. Do not route Gecko one-shot response bodies through URL-only DownloadManager requests. Add safe ID registration/reconciliation only for genuinely platform-managed jobs and tests for missing IDs, process death and terminal transitions.
2. **Tabs/session reliability:** audit all restore/save entry points, duplicate sessions, popup/private inheritance, recently-closed behavior, lifecycle shutdown and low-memory session release; add regression tests.
3. **Permissions/notifications/privacy:** finish Android API matrix and callback cancellation/race tests; validate notification delivery, tap navigation and clear-data behavior on devices.
4. **uBlock:** validate Mozilla XPI install/restore/update, private consent, per-site action and real-site compatibility; measure resource impact on low-end devices.
5. **Storage cleanup:** audit and remove only demonstrably dead SharedPreferences compatibility paths; keep a single source of truth per feature and retain Room schema migrations.
6. **Localization/accessibility:** audit all user-visible strings, Arabic RTL and mixed-direction URLs/emails/numbers, font scaling, touch targets, TalkBack and tablet/foldable layouts.
7. **Release readiness:** configure owner-controlled release-signing secrets, produce signed release APKs, verify signatures/ABIs/upgrades, then install and test on the owner's TECNO SPARK 5 Air (Android 10) and a broader device matrix.
8. **New UI:** wait for Claude's approved design; then implement it without changing product behavior or inventing unsupported controls.

## Verification boundary

- CI success proves the workflow's tests, lint and debug APK checks for that exact commit only.
- No physical-device test, signed release APK, installation success on TECNO SPARK 5 Air, or full real-site uBlock verification is claimed.
- No Android instrumentation/device matrix completion is claimed unless an actual device run is recorded.

## Latest work log

### 2026-11-05 — HEAD audit
- Confirmed `rewrite-v2` HEAD `871c84192431a1a55fc656cc050e1e92715ddb45`.
- Confirmed push and pull-request CI runs `42121854379` and `42121859399` both succeeded.
- Confirmed PR #4 remains open and unmerged.
- Audited implementation board, open issues #5–#10, tab/session lifecycle, Room/DataStore paths, downloads, permissions and uBlock integration.
- Found and fixed a DownloadsPage cancellation bookkeeping bug in the follow-up commit: only forget a tracked platform download after Android confirms removal.


## 2026-11-06 — follow-up implementation after Issues #5–#10 review

- Branch: `rewrite-v2`; work remains unmerged from PR #4.
- Commit `3f69007bd2292fa35e0620ab7855b87b9f2f2214`: Room download failure/cancellation transitions now preserve the last persisted byte count when no final count is supplied. The DAO treats the sentinel as “keep current progress”; a focused repository regression test was added.
- Commit `fb74dce66c80ead8acad4083108858bb5c8c65dc`: Gecko one-shot response bodies are now closed if the stream coordinator is cancelled before ownership transfers to the background writer. Once accepted by the writer, writer cleanup remains authoritative.
- Commit `1bc0771e3d4973805bdb8a8c966e591df3eb3880`: Website notifications now use a stable SHA-256-derived Android notification tag based on origin + web tag. This prevents 32-bit notification ID collisions from causing one site's notification to replace or cancel another site's notification; dismissal uses the same tag.
- These changes are implementation updates, not a claim that Issues #5–#10 are complete. No device/runtime verification is claimed. GitHub Actions workflows may run automatically after branch pushes.


## Additional source-of-truth and protection updates — 2026-11-06

- Commit `d782d3e2d3c525bb116d7a593bbad5e400b7841c`: the website-notification bridge now reads the global enablement flag from the DataStore-backed settings snapshot (legacy cache is only a pre-load fallback).
- Commit `29993fd5e95dc034225a7ad5258146c69d9eb889`: startup reconciles persisted uBlock flags with GeckoView's discovered extension state.
- Commit `c3c82ac3628d46800c5f096c8f0969ac9f22acb7`: removed the redundant legacy SharedPreferences download-recovery call; application-scoped Room startup recovery remains authoritative for stream-download records.

These changes narrow remaining compatibility usage but do not complete the full legacy-store audit. Do not remove tab/session fallback until its failure behavior is intentionally replaced.

- Commit `97304032b315a6ff68129fa9b2c4a674439bbca5`: address-field text direction now uses first-strong behavior, keeping URL/domain input LTR while allowing Arabic and other RTL search queries to flow naturally.


## Follow-up implementation — 2026-11-06

- Commit `9c9e232729dd4c3cf7ff86ee0749614cf5c6607c`: browsing-data clearing now closes private GeckoSessions before removing private tab records; regular tabs are reset and retained.
- Commit `e7821ee698648e936f0a22903612202af5d8c783`: site-notification permission checks now use the DataStore-backed global setting rather than the legacy SharedPreferences value.
- Commit `262b1fc0a6c38432dc916415da01d3b6e45d9445`: tab closing and browsing-data clearing continue cleanup if recently-closed persistence or GeckoView detachment callbacks fail.
- Commit `11defa326272b61aef81bb6580a69c42b8bf89ec`: recently-closed entries expire after 30 days and are pruned when listed, recorded, or restored.
- Commit `7de2d38855f474f58fd000d704e439c6241233d4`: download coordinator shutdown no longer cancels its persistence scope before the writer's cancellation callbacks can save terminal states to Room; new stream requests are rejected and their bodies closed after shutdown.
- No tests were run for these changes. Issues #5–#10 remain open until their full acceptance criteria are implemented and verified.


## Latest continuation checkpoint — 2026-11-06

- Current implementation branch: `rewrite-v2`; latest documentation checkpoint is being updated alongside source changes. Do not merge to `main`.
- Commits `f2e98ef93c745f4f3e1ec7219c2c7e10c8dfbc52` and follow-ups: clearing browsing history now also clears recently-closed entries in Room and the emergency SharedPreferences fallback.
- Commits `2b7dab2138f751fba7766dbf9477bb2b722d645d` and `e7e1b79a872946c20c71b1750398d54393381d72`: added Arabic and English resource-backed messages for MainActivity, Downloads, Privacy, Library and Settings feedback. Both locale files currently contain 325 matching string keys; this was inspected from repository contents, not by running tests locally.
- Commits `182f5353e72d317ff406c0da2e59306946318e53` and `027f14ccbf66193de64d4d7c19e2a8eae202c549`: extended `scripts/check_locales.py` to validate that Kotlin UI translation helper calls resolve to declared English resource keys, in addition to locale parity and formatting placeholders.
- The last confirmed successful GitHub Actions build was for `f2e98ef`. The latest localization/validator commits have new Actions runs queued or in progress; do not claim they passed until the run for the latest head completes.
- No physical-device tests have been performed or claimed. GeckoView response downloads still use the exact original response body; DownloadManager enqueue/reconciliation is not added because a URL-only re-fetch could lose request/response fidelity.
- Remaining work is still open: full SharedPreferences compatibility audit, tab/session race and low-memory verification, safe DownloadManager integration where the original request can be represented faithfully, Android permission/notification device checks, real-site uBlock behavior, further accessibility and internationalization, and device/install/release verification.
