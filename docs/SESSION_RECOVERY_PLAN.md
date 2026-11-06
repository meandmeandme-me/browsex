# Falak — session management and recovery plan

Status: core local persistence and tab lifecycle are implemented; full Android process-recreation and device-level recovery verification remains open. This document is an implementation/QA contract, not a claim that all recovery scenarios are complete.

## Existing implementation

- A Room-backed regular-tab snapshot stores validated URLs and the active-tab index.
- Startup restores tab metadata without opening every GeckoSession; the selected tab opens first and background tabs load on demand.
- Private tabs are excluded from saved snapshots, named sessions, recently closed history and cloud-sync categories.
- Named sessions support save, list, replace-by-name, open and delete, with a 10-session cap and up to 20 validated tabs per session.
- Recently closed regular tabs are bounded to 10 entries, expire after 30 days, and are consumed atomically when restored.
- Gecko content-process recovery has one automatic retry and a manual reopen path after repeated failure.
- Session teardown, permission cancellation and GeckoSession release are centralized in the session lifecycle coordinator.
- Memory pressure can release inactive GeckoSessions while retaining durable tab metadata.

## Remaining product behavior

1. **Crash recovery checkpoint**
   - Preserve the latest validated regular-tab snapshot and selected tab when Android kills the process.
   - On next launch, restore the selected tab and offer a clear recovery path for the remaining tabs if startup recovery was interrupted.
   - Never persist private tab URLs or private session metadata.
2. **Recovery status**
   - Distinguish normal cold start, process recreation, content-process crash and storage failure.
   - Avoid repeatedly showing a recovery prompt after the user has dismissed or completed it.
   - If Room is unavailable, fail safely to the home page and retain an actionable error; do not silently overwrite a valid snapshot with an empty one.
3. **Session management**
   - Keep save/replace/open/delete behavior explicit.
   - Show saved date and tab count; confirm destructive deletion.
   - When restoring into a partially full workspace, open only tabs that fit, preserve the saved active-tab selection among those opened, and explain skipped tabs.
   - Recheck capacity before consuming a recently-closed entry. If a race still prevents opening, preserve the entry rather than losing it.
4. **Recovery actions**
   - Provide reopen/retry for a crashed tab and a bounded retry policy.
   - Provide restore recently closed and restore named session actions.
   - Provide clear browsing data without accidentally deleting named sessions unless the user explicitly selects them.
5. **Reliability and privacy**
   - Keep tab count bounded to 20.
   - Validate every restored URL and reject unsafe schemes.
   - Never sync private tabs, cookies, permissions, form state or downloaded file bytes.
   - Ensure shutdown and memory-pressure cleanup do not race the final durable snapshot.

## Required automated coverage

- Save/restore active index with valid, invalid, duplicate and over-limit URLs.
- Process recreation with a mixed regular/private workspace; only regular tabs return.
- Repeated lifecycle snapshots do not let an older write overwrite a newer snapshot.
- Room unavailable/corrupt snapshot behavior does not erase a previously valid state.
- Recently-closed restore at tab limit does not consume and lose the item.
- Named session replace, delete, open into partially full workspace and preserve active selection.
- Content-process crash auto-retry exactly once, then manual recovery; stale callbacks must not revive a closed/replaced tab.
- Clear browsing data behavior for tabs, recently closed and named sessions.
- Android device smoke tests for cold start, force-stop/process death, low-memory reclamation, orientation change, Android 10+ and a low-RAM device.

## Sync boundary

Local session recovery works without an account. Cloud sync of named sessions/open tabs is a separate opt-in category and remains off by default. Do not enable it until account sign-in, device registration, encryption/key recovery, conflict handling and cloud deletion are implemented and reviewed. Private tabs remain excluded permanently.

## Completion gate

Do not mark the feature complete until automated tests pass in CI and the Android device scenarios above are verified. CI success alone does not prove Android process-death behavior.
