# BrowseX — Project Continuity & Handoff

> Living engineering checkpoint. Update this file in the same commit as every meaningful code/documentation change. It is the authoritative handoff note when a work session or model context ends.

## Current checkpoint

- Branch: `rewrite-v2`
- Last verified base commit: `2ac9b8a48e2767e082c48a75437bf87d148fe12e`
- Current phase: **Phase 2 — Storage foundation and safe migration**
- Current status: CI passed for the base commit; storage foundation exists but is not yet wired as the application's live source of truth.
- Product direction: rebuild the browser experience around a stable, multilingual browser core; old chrome UI is transitional/partly removed. New visual UI is planned separately via Uizard.
- User data: project owner confirmed there is no user data to preserve at this stage. Still keep migrations defensive and non-destructive.

## Latest verified checks

- GitHub Actions run: [42055302138](https://github.com/meandmeandme-me/browsex/actions/runs/42055302138)
- Commit tested: `2ac9b8a48e2767e082c48a75437bf87d148fe12e`
- Result: **completed / success**
- This confirms the workflow for that commit only; it is not a claim that the app has passed device installation or manual runtime testing.

## Phase map

| Phase | Scope | Status |
|---|---|---|
| 0 | Repository baseline, product direction, compatibility constraints | Complete |
| 1 | Core policies, permissions, notifications, adaptive runtime foundations, defensive preference handling | In progress / foundations present |
| 2 | Storage architecture: DataStore settings, Room entities/DAOs, safe legacy import, repository cutover | **Current** |
| 3 | Session/tab lifecycle, restore, bookmarks/folders/history, downloads on a single source of truth | Not started |
| 4 | MainActivity decomposition into focused lifecycle/navigation/permission/download coordinators | Not started |
| 5 | Privacy/security, site permissions, notification behavior, ad-block integration and recovery paths | Not started |
| 6 | Accessibility, localization/RTL, adaptive layouts and weak-device performance | Not started |
| 7 | Account/sync contracts (Google/Microsoft/OIDC architecture; real sign-in requires backend/client configuration) | Foundations/planning only |
| 8 | Release engineering, APK/AAB validation, signing, Android-version/device matrix | Not started |
| 9 | New Uizard UI implementation after core behavior stabilizes | Planned |

## Completed foundations (verify before changing)

- DataStore settings wrapper and defensive normalization policies.
- Legacy settings migration copies supported scalar preferences without deleting old keys.
- Room database, entities and DAOs are present; schema is version 2.
- Legacy storage snapshot parser and Room importer are present.
- Import completion state is written in the same Room transaction as imported rows, preventing marker loss from triggering a destructive re-import.
- Defensive handling for malformed legacy preference values and permission-denial sets.
- Adaptive runtime policy considers device profile, battery saver and thermal pressure.
- Product/UI map: `docs/design/BROWSEX_Uizard_PRODUCT_MAP_AR.md`.
- Account/sync domain contracts exist but do not imply a working login provider or backend.

## Important architecture truth / do not misreport

- Room is **not yet the live application source of truth**. Do not claim storage cutover is complete.
- DataStore exists but is not yet wired as the live settings source throughout the app.
- BrowserStore and its session/library/download stores still rely on SharedPreferences and synchronous call sites.
- MainActivity still contains transitional/legacy helpers and is large; the old UI is only partly removed.
- Adaptive runtime pressure is currently sampled as a startup/runtime configuration input; do not claim continuous live thermal monitoring.
- No production Google/Microsoft sign-in is configured.
- CI success does not prove APK installation on the user's TECNO SPARK 5 Air (Android 10 / 2 GB RAM).

## Next actions — ordered

1. Inspect current storage code and all BrowserStore call sites before choosing a migration slice.
2. Add/verify Room schema export and a safe schema migration strategy before Room is enabled for production. Confirm whether any released build could have created schema v1.
3. Introduce repository interfaces and migrate one bounded feature slice (prefer bookmarks/library or sessions) end-to-end; avoid duplicate sources of truth.
4. Keep UI/main-thread code non-blocking: no `runBlocking` in activity/UI getters. Use lifecycle-aware coroutine/Flow collection and explicit suspend operations.
5. Add round-trip, process-restart, malformed-data, duplicate-import, rollback and concurrency tests for each migrated slice.
6. Migrate settings to DataStore through a repository and remove legacy reads only after a verified transition.
7. Migrate sessions/tabs, bookmarks/history/folders, then downloads; document source-of-truth and migration behavior for each.
8. Decompose MainActivity incrementally into testable coordinators without changing browser behavior unintentionally.
9. Complete runtime audits: permission prompts and cancellation, site notifications, downloads, private browsing isolation, content-process recovery, navigation/external intents, security and ad-block behavior.
10. Audit locale resources, RTL mixed-direction text, accessibility semantics, tablet/foldable layouts and low-memory behavior.
11. Validate minSdk/targetSdk/GeckoView compatibility, release signing and APK/AAB installation on Android 10 and a broader device matrix.
12. Implement the new UI from the product map only after core capabilities and states are accurately represented.

## Per-change update protocol

For every meaningful code or documentation change:
1. Re-read branch HEAD and ensure it matches the expected parent.
2. Update this file in the same commit: current phase, what changed, what remains, tests/checks and current commit.
3. Commit against the exact current parent and update the branch with an expected-SHA lease.
4. Re-read HEAD after the update.
5. Check the workflow run for that exact commit. Record pending/failure/success accurately.
6. Never mark a phase complete based only on code being written; require tests and relevant runtime validation.

## Change log

### 2026-11-05 — Continuity checkpoint created
- Base: `2ac9b8a48e2767e082c48a75437bf87d148fe12e`
- Confirmed branch HEAD is `rewrite-v2`.
- Confirmed GitHub Actions run 42055302138 completed successfully for the base commit.
- Current phase set to storage foundation and safe migration.
- Next: inspect the storage architecture and move one feature slice safely toward repository-backed persistence.
