# BrowseX — Project Continuity & Handoff

> Update this file in the same commit as every meaningful implementation change. This is the handoff source of truth when a work session or model context ends.

## Current checkpoint

- Branch: `rewrite-v2`
- Latest implementation commit: `5117b2913cbcfdda909d4ce5ef3a256938775f7b` (Room repository composition root).
- Previous implementation commits in this phase:
  - `290478080bcfcc5ab4a99deac16a73800f3a6298`: validate completed download URI schemes.
  - `600f785e8810cd3a320ca175f712b6263bd503d3`: stable ordering for pruned filename reservations and tracked download IDs.
  - `0fee2a71e3669dd2f9d36d44feca5fec779df251`: atomic DataStore read-modify-write updates.
  - `9a658ec4a2e5a23241dd6d0daaf966c9073b7842`: remove duplicate DAO declarations discovered during static review.
  - `866fc1389ac8cebbe58b850d212c4543abc1cf0f`: download filename sanitization.
  - `9cbe2d0a2ab14dd8155fe1827193d6f0a6f4ffa8`: canonical Room site permission repository.
  - `ad03d18d1192e2082c740c950831f53b14bd06aa`: bounded download records, filename reservations, and platform download IDs.
  - `f225ffe06860b85d251aac88960b3ea985ae16c2`: Room history repository.
  - `c9f6961bdd0032e7276f6a8ab01167526595ea9d`: atomic saved-session deletion and privacy boundary documentation.
  - `2ecf5a4597036f8472803477c875c2051906bbe1`: Room named sessions and recently closed tabs.
  - `b96163a693736486fe76ca0feec052e80ca0598d`: bounded legacy folder parsing and added versioned Room bookmark backup import/export.
  - `3b9de5ed2d820fc26f42138e64fb7347187f244b`: capped legacy folder imports at 50 including Unfiled.
  - `0129e8e880fa9e6157b9be953de7ae3f151fda33`: isolated folder capacity assertions in repository tests.
- Last verified successful CI commit: `0129e8e880fa9e6157b9be953de7ae3f151fda33` (both build runs succeeded: [42065031924](https://github.com/meandmeandme-me/browsex/actions/runs/42065031924), [42065040085](https://github.com/meandmeandme-me/browsex/actions/runs/42065040085)).
- **Verification boundary:** commits `3b9de5e`, `b96163a`, and `26dd424` have not yet been treated as verified. Do not claim their tests/build pass until CI completes successfully.
- Current phase: **Phase 2 — Storage foundation and safe migration**
- Current task: finish repository parity and harden backup/import before any production cutover.
- `BrowserStorageGraph` is the composition root for all Room repositories. It is intentionally not connected to MainActivity yet; use it for feature-by-feature cutover only after parity and verification.
- Room groundwork now includes dedicated repositories for bookmarks/folders and backup, regular tab snapshots, named sessions/recently closed tabs, history, downloads (including 300-record retention, safe filename reservations, and platform download ID tracking), and per-origin camera/microphone decisions. These are persistence layers and tests, not yet connected to production UI/runtime controllers.
- Room is still **not wired as the production source of truth**. Legacy SharedPreferences remains active; do not delete or clear legacy keys.
- Room bookmark repository now has versioned bookmark-only JSON export/import matching the legacy portable backup contract (`format=browsex-bookmarks`, `version=1`, `folders`, `bookmarks`). Import is merge-only, validates web URLs, deduplicates folder names/URLs, remaps folders by case-insensitive name, never overwrites existing bookmark URLs, and enforces 50 folders / 200 stored bookmarks. It excludes history, sessions, cookies, credentials, and private browsing data.
- Legacy storage parser now caps folders at 50 total including the protected `unfiled` row while continuing past malformed/duplicate rows until the cap is reached.
- Added a Room site-permission repository using the shared canonical-origin policy, camera/microphone-only decisions, explicit allow/deny/ask semantics, per-origin clearing, and a 100-decision cap. This is persistence groundwork; platform permission prompts and website notification subscriptions remain separate runtime integrations.
- Added a Room download repository with bounded metadata, explicit queued/running/completed/failed/cancelled transitions, interrupted-download recovery, collision-safe filename reservations, and BrowseX-owned platform download ID tracking. It stores metadata only, not downloaded file contents.
- Added a Room history repository with URL validation, latest-visit deduplication, a 300-entry cap, removal, and clear operations. Private visits must be excluded by the caller before persistence.
- Added a Room tab-state repository for validated regular-tab snapshots, active-tab restoration, and safe home fallback. The UI/runtime caller must exclude private tabs before saving.
- Added a Room session repository for named sessions (10 max, replace-by-name, validated/restorable tab snapshots) and recently closed tabs (10 max, deduplicated by URL, one-time consume). The repository does not infer whether a URL came from a private tab; callers must never pass private tabs to persistence APIs.
- The owner requested one consolidated verification pass only after the implementation batch is complete. Do not pause after each small change to report tests; finish the remaining storage/runtime wiring and documentation first, then run the complete test/lint/APK/schema verification once.

- Branch: `rewrite-v2`
- Latest implementation commit: `af7ed3d270b6cdb65faed34331922b77a5a38e38` (bookmark-folder lifecycle).
- Last verified successful CI commit: `af7ed3d270b6cdb65faed34331922b77a5a38e38`
- Current phase: **Phase 2 — Storage foundation and safe migration**
- Current task: complete bookmark repository parity and establish safe production cutover boundaries.
- Last verified CI success: run [42062106037](https://github.com/meandmeandme-me/browsex/actions/runs/42062106037) on `af7ed3d270b6cdb65faed34331922b77a5a38e38` (tests, lint, APK builds and schema artifact succeeded).
- CI on previous HEAD `ac161663a58932007e963d4b66a2db6e7f0b2880`:
  - Push run [42060084325](https://github.com/meandmeandme-me/browsex/actions/runs/42060084325): **failure** at Kotlin compilation.
  - PR run [42060091076](https://github.com/meandmeandme-me/browsex/actions/runs/42060091076): **failure** at the same Kotlin compilation error.
  - Root cause: suspend DAO method was passed as a non-suspending function reference in `find`.
- Product direction: stable multilingual browser core first; replace transitional chrome UI later using the approved product map/Uizard workflow.
- User confirmed no user data exists at present. Keep migrations defensive and non-destructive anyway.

## Phase map

| Phase | Scope | Status |
|---|---|---|
| 0 | Repository baseline, product direction, compatibility constraints | Complete |
| 1 | Core policies, permissions, notifications, adaptive runtime foundations, defensive preference handling | Foundations present; audit remains |
| 2 | DataStore, Room, safe import, repository cutover | **Current** |
| 3 | Sessions/tabs, bookmarks/folders/history, downloads on one source of truth | Not started |
| 4 | Decompose MainActivity into lifecycle/navigation/permission/download coordinators | Not started |
| 5 | Privacy/security, site permissions, notifications, ad-block integration and recovery | Not started |
| 6 | Localization/RTL, accessibility, adaptive layouts and weak-device performance | Not started |
| 7 | Google/Microsoft/OIDC account and sync architecture | Planning only; live auth needs backend/client configuration |
| 8 | Release signing, APK/AAB validation and Android device matrix | Not started |
| 9 | New Uizard UI implementation after core behavior stabilizes | Planned |

## Implemented foundations

- Defensive DataStore settings wrapper and normalization policies; not yet the live settings source across the app.
- Legacy settings migration copies supported scalar preferences without deleting old keys.
- Room database schema v2, entities and DAOs.
- Legacy snapshot parser and importer.
- Room migration completion state is committed atomically with imported rows. SharedPreferences marker is only a mirror and cannot suppress import if Room has no migration-state row.
- Defensive handling for malformed legacy preferences and permission-denial sets.
- Adaptive runtime policy considers device profile, battery saver and thermal pressure.
- Product/UI map: `docs/design/BROWSEX_Uizard_PRODUCT_MAP_AR.md`.

## Current Room bookmark slice

- Follow-up parity patch enforces the legacy folder cap (50 total including `unfiled`) transactionally and matches the legacy 40-character folder-name limit. Production cutover remains deferred.

- Added DAO operations for atomic bookmark insert (IGNORE against the unique URL index), lookup, delete and folder move.
- Added `RoomBookmarkRepository` with URL and folder validation, title normalization, duplicate rejection, and basic CRUD.
- Repository lookup now invokes the suspend DAO method directly from a suspend body (not through an incompatible function reference).
- Long titles are safely normalized to the 160-character storage limit instead of rejecting the bookmark.
- Added Robolectric coverage for add/find/move/remove, duplicate URLs, invalid schemes, missing folders and long-title normalization.
- **Not wired to production callers yet.** `BrowserStore` and `MainActivity` still use SharedPreferences for bookmark operations. Do not claim cutover complete.

## Architecture truths — do not misreport

- Room is not yet the live application source of truth.
- DataStore is not yet wired as the live settings source throughout the app.
- Session, library and download persistence still use SharedPreferences and synchronous call sites.
- MainActivity remains large and contains transitional helpers; old UI removal is partial.
- Adaptive runtime pressure is sampled for runtime configuration; continuous live monitoring is not implemented.
- No production Google/Microsoft sign-in is configured.
- CI success does not prove installation on the user's TECNO SPARK 5 Air (Android 10 / 2 GB RAM).

## Next actions — ordered

1. Room schema v2 was generated and inspected from CI artifact (identity hash `a8bb5bdfbd75a05ec09fb4f5ee8c5062`); it is not yet committed to the repository. Check in the generated JSON before production activation. Every future schema change must add a versioned snapshot and an explicit Room migration; never use destructive fallback.
2. Complete bookmark repository parity: import/export, restart/round-trip, malformed-data and concurrency tests. Folder create/rename/delete/reorder is implemented in the current pending commit.
3. Add repository restart/round-trip, malformed-data, rollback and concurrency tests.
4. Wire bookmark reads and writes through one source of truth; remove legacy bookmark writes only after verified cutover.
5. Migrate sessions/tabs, history, then downloads through repository interfaces, one feature slice at a time.
6. Migrate settings to DataStore with lifecycle-aware Flow collection; never use `runBlocking` in UI getters.
7. Decompose MainActivity incrementally without behavior regressions.
8. Audit runtime permissions/cancellation, site notifications, downloads, private browsing isolation, content-process recovery, external intents, privacy/security and ad-block behavior.
9. Audit locale resources, RTL mixed-direction text, accessibility semantics, tablet/foldable layouts and low-memory behavior.
10. Validate minSdk/targetSdk/GeckoView compatibility, release signing and APK installation on Android 10 and a broader device matrix.
11. Implement the new UI from the product map after core states and behaviors are stable.

## Per-change protocol

1. Re-read branch HEAD before writing.
2. Update this file in the same commit as every meaningful code change.
3. Commit against the exact parent and update the branch with expected-SHA protection.
4. Re-read HEAD after updating.
5. Check CI for that exact commit and record pending/failure/success accurately.
6. A phase is complete only after code, tests and relevant runtime validation—not merely because implementation exists.

## Change log

### 2026-11-05 — Room bookmark folder lifecycle
- Added transactional folder create/rename/delete/reorder operations; names are normalized and checked case-insensitively for duplicates.
- Deleting a folder moves its bookmarks to `unfiled`; the default folder cannot be deleted.
- Added tests for folder lifecycle, duplicate/blank names, safe deletion and ordering.
- CI run [42062106037](https://github.com/meandmeandme-me/browsex/actions/runs/42062106037) passed on `af7ed3d270b6cdb65faed34331922b77a5a38e38`.

### 2026-11-05 — Room bookmark repository compile correction
- Fixed the suspend DAO lookup invocation that blocked Kotlin compilation.
- Normalized long titles to the repository's documented 160-character limit.
- Added a regression assertion for long-title handling.
- CI run [42060878488](https://github.com/meandmeandme-me/browsex/actions/runs/42060878488) passed on `c0b28ea9a850553bbd97ee1303c4243f31b5c9b5`.

### 2026-11-05 — Room bookmark repository foundation
- Commit: `7cc0d6301e77b2b433d217d16e3c0f09157f4773`
- Added DAO atomic insert, lookup, delete and folder-move operations.
- Added RoomBookmarkRepository and Robolectric CRUD/validation tests.
- Production cutover deliberately deferred to avoid dual sources of truth.

### 2026-11-05 — Room migration marker authority
- Commit: `eba399035b79ef652e33433fc0e9a26958c1af1f`
- Room migration-state row is authoritative; stale SharedPreferences marker cannot skip import into an empty Room database.
- Added regression coverage.

### 2026-11-05 — Continuity checkpoint
- Created this durable project handoff record.
