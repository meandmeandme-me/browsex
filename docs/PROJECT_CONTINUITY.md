# BrowseX — Project Continuity & Handoff

> Update this file in the same commit as every meaningful implementation change. This is the handoff source of truth when a work session or model context ends.

## Current checkpoint

- Branch: `rewrite-v2`
- Latest implementation commit: pending bookmark-folder lifecycle commit.
- Last verified successful CI commit: `ac7c27f90b6335775ad4c82fc40702c2f6b9a50c`
- Current phase: **Phase 2 — Storage foundation and safe migration**
- Current task: complete bookmark repository parity and establish safe production cutover boundaries.
- Last verified CI success: run [42060878488](https://github.com/meandmeandme-me/browsex/actions/runs/42060878488) on `c0b28ea9a850553bbd97ee1303c4243f31b5c9b5` (tests, lint, APK builds and schema artifact succeeded).
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
- CI pending for this commit.

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
