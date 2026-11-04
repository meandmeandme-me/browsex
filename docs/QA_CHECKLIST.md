# BrowseX QA checklist

Implementation and verification are tracked separately. Items under “implemented” describe code paths added to the branch; they are not marked as device-tested.

## Implemented paths to verify in the final pass

- [x] Pure URL normalization and bounded tab snapshot normalization have unit coverage.
- [x] Stream-download queued/running/terminal/cancelled state predicates have JVM unit coverage (not run in this implementation pass).
- [x] Download filename policy has JVM unit coverage for path separators, control characters, bidi overrides, Unicode preservation and length bounds (not run in this implementation pass).
- [x] Android location/camera/microphone runtime permission request bridge and allow/deny result handling.
- [x] Gecko content permission prompt provides Allow, Block and Not now; Site Info can change stored decisions.
- [x] Camera/microphone origin decisions are separate from Android runtime permissions.
- [x] Website notifications use Gecko WebNotificationDelegate and Android channel APIs.
- [x] API 33+ runtime notification permission, app-level site-notification switch, click/dismiss callbacks and private-browsing suppression.
- [x] Shared theme and locale preferences are applied to native screens and local home page.
- [x] Arabic RTL home page and core native screens; touch targets/content descriptions improved.
- [x] DownloadManager reason codes, status/progress, missing IDs, open/cancel/remove and filename reservations.
- [x] Crash recovery attempt, manual retry state, inactive-session deactivation and lazy tab restoration.
- [x] Site Info permission rows expose Allow/Block/Ask controls.
- [x] A failed DownloadManager query is shown as a read error, not misreported as deleted downloads.
- [x] Launching an external URL preserves saved tabs and opens the new link as the active tab.

## Release signing coverage

- [x] Gradle supports a dedicated environment-based release signing identity and rejects partial configuration.
- [x] CI skips signed release artifacts when no key is configured and fails on partial secret configuration.
- [ ] Configure real protected signing secrets and verify release APK signatures, ABI contents and in-place upgrade behavior.

## Final automated CI gates (required; not verified for the current HEAD)

- [ ] Gradle configuration resolves reproducibly.
- [ ] Kotlin compilation succeeds.
- [ ] Unit tests pass, including the configured-home-URL snapshot test.
- [ ] Android lint completes with no new errors.
- [ ] Debug APKs are generated for `armeabi-v7a`, `arm64-v8a` and universal output.
- [ ] APK package ID and minSdk are correct.
- [ ] APKs contain the expected GeckoView native libraries for each ABI.
- [ ] CI uploads debug artifacts only after every required gate passes; public release publishing waits for stable signing.

## Functional checks (physical-device validation still required)

- [ ] Home page loads offline from app assets and keeps its Arabic/RTL and theme selection after process restart.
- [ ] HTTP and HTTPS navigation work; unsafe schemes are rejected.
- [ ] Search engine selection persists after restart.
- [ ] Back/forward state updates correctly.
- [ ] Opening, switching, closing and restoring tabs does not leak GeckoSessions; 20 saved tabs must not all load simultaneously.
- [ ] Launching through an external HTTP(S) link preserves saved tabs and opens the external link as the active tab.
- [ ] The 20-tab cap is enforced for new tabs and popups.
- [ ] Backgrounding, returning, low-memory callbacks and repeated content-process crashes recover without losing unrelated tabs.
- [ ] External links and share intents work.
- [ ] Bookmark/history add, remove and clear persist correctly.
- [ ] Site permission prompt appears only after a site request; Android deny, app settings re-grant, per-origin allow/block/ask and dialog cancellation behave correctly.
- [ ] Site Info refreshes stored Gecko permissions after every Allow/Block/Ask change; permission rows are keyboard/TalkBack accessible; persistent storage explains its non-revocable grant.
- [ ] API 33+ notification permission prompt appears on first eligible request; app toggle, OS-level disable, tap, dismiss and private browsing are correct.
- [ ] BrowseX downloads list includes only IDs registered by BrowseX.
- [ ] DownloadManager downloads complete, open, cancel, remove and survive process restart; missing Android records are clearable.
- [ ] Response-stream downloads show queued/running/terminal states, determinate progress when Content-Length exists, indeterminate progress otherwise, and update without losing the current scroll position.
- [ ] Cancelling a queued or active response-stream download closes its source stream, removes partial MediaStore/filesystem output, persists a cancelled state and never exposes an incomplete file as complete.
- [ ] Stress the cancel-versus-finalize race on Android 8–9 and Android 10+; each attempt must end as either a cancelled record with no visible partial file or a completed record with a valid file.
- [ ] Unfinished response-stream records are marked interrupted after a forced process stop; inspect and clean any orphaned pending MediaStore rows before release.
- [ ] Verify Android 8–9 legacy storage permission and Android 10+ scoped-storage behavior.
- [ ] Verify direct response-stream downloads for GET, POST, authenticated responses, cookies, one-time URLs, redirects, duplicate names, storage-full and network loss; verify the exact received bytes and no second network request.
- [ ] Reader mode handles non-article pages, large font and extraction failures gracefully.
- [ ] Rotation, system theme changes and fullscreen return browser chrome correctly.
- [ ] Arabic RTL, English LTR, 200% font scale, TalkBack and narrow 720px-wide screens have no clipped controls.

## Device matrix

| Device profile | Required checks |
|---|---|
| Android 10, 2 GB RAM, 720px width | install, cold start, memory stability, keyboard, navigation, permissions, downloads, tabs, rotation |
| Android 13+ phone | notification runtime permission, channel settings, tap/dismiss, downloads, system bars, share sheet |
| Android tablet | multi-column tabs, reader, settings, landscape |
| Small-screen / large font / RTL | no clipped controls, scrollable screens, readable Arabic layout |

## Release rules

Never call a build successful until its workflow conclusion is `success` and the artifact is attached to that exact commit. Never claim physical-device validation unless it was performed on a device. Do not overwrite or ask the user to uninstall an existing install as a troubleshooting shortcut.

## Extracted browser storage boundaries

- [x] DownloadManager IDs, response-stream records, filename reservations and interrupted-record recovery are delegated to BrowserDownloadStore using the existing persisted keys.
- [x] BrowserStore is reduced to a compatibility facade; feature callers retain their existing API while storage responsibilities are separated.
- [ ] Verify active response-stream records, completed records, cancellation and DownloadManager ID ownership after upgrade/restart.

- [x] Bookmark folders/items, history, legacy bookmark migration and JSON backup/import are delegated to BrowserLibraryStore without changing existing persisted keys.
- [ ] Verify bookmark/folder ordering, legacy migration, history and backup/import on an upgraded installation.

- [x] Open-tab snapshots, named sessions and recently-closed entries are delegated to BrowserSessionStore using the existing persisted keys and validation rules.
- [ ] Verify legacy saved tabs, named sessions and recently-closed entries survive upgrade and process restart.

## Localization tooling coverage

- [x] CI validates Android locale resource parity, plural categories, formatting placeholders and locale-config registration.
- [ ] Add reviewed translations and run native-speaker, RTL/LTR, TalkBack and large-font QA before advertising each new locale.

## Extracted browser policy coverage

- [x] Browser theme, language and home-layout normalization is isolated as pure policy with JVM tests added (not run in this implementation pass).
- [x] Browser-wide preferences and per-origin media/platform permission decisions are delegated from BrowserStore without changing the existing SharedPreferences keys.

## Tab registry and lifecycle coverage

- [x] Tab collection and selected-tab membership are owned by BrowserTabRegistry; equal-URL tabs remain distinct by identity.
- [x] Tab workspace rendering is moved to TabsPage and browser mutations are injected through callbacks.
- [x] GeckoSession delegate wiring is moved to BrowserSessionDelegateFactory with BrowseX-owned callbacks.
- [ ] Verify popup/new-session routing, content-process crash recovery, page progress/history callbacks and fullscreen transitions on device.
- [ ] Verify tab workspace, saved-session replace/open/delete, recently-closed restore and 2/3-column layouts on phone/tablet.
- [x] JVM tests added for add/remove/select/replace/clear invariants (not run in this implementation pass).

## Tab lifecycle policy coverage

- [x] Pure session-budget selection preserves the active tab and releases least-recently-used inactive sessions.
- [x] Memory-pressure selection releases inactive opened sessions in deterministic oldest-first order.
- [x] JVM unit tests added for budget, tie-breaking, memory pressure and minimum-budget behavior (not run in this implementation pass).

## Additional coverage for the current foundation work

- [ ] Save a named session, close/reopen BrowseX, and verify the saved session remains available.
- [ ] Open a saved session and verify its active tab is selected; verify the 20-tab cap and partial-open warning.
- [ ] Verify private tabs are never included in a named session, recently-closed list or normal restore.
- [ ] Close a regular tab, restore it from Recently closed, and verify the entry is consumed; verify the ten-entry cap.
- [ ] Save a session using an existing name and verify replacement requires confirmation.
- [ ] Import backups with colliding folder IDs and duplicate folder names; verify bookmarks map to the intended folder.
- [ ] Import malformed, oversized, unsupported-version and non-HTTP(S) bookmark data; verify preview counts invalid rows and no partial writes occur.
- [ ] Import duplicates already on device and duplicates inside the backup; verify preview counts conflicts and merge/replace behavior is explicit.
- [ ] Reorder bookmarks within a folder and verify items in other folders retain their order.
- [ ] Search bookmarks/history by page title, domain and URL.
- [ ] Trigger Android memory pressure with multiple tabs; verify active tab remains usable and inactive tabs reopen from validated URLs.
- [ ] Verify response-stream writer cleanup on process death, MediaStore pending-row cleanup, FileProvider access on Android 8/9, and large-file behavior.
- [ ] Verify English/Arabic resources, device-locale fallback, mixed RTL/LTR text, screen readers, large font scale, landscape and tablet layouts.
- [ ] Verify Unicode NFKC search using full-width Latin, Arabic, CJK and mixed-script page titles/URLs.
- [ ] Add, edit, remove and reorder home shortcuts; verify order persists after reload and reset restores defaults.
- [ ] Add reviewed translations and plural/date/number tests for each locale pack before advertising that locale as fully supported.
