# BrowseX QA checklist

Implementation and verification are tracked separately. Items under “implemented” describe code paths added to the branch; they are not marked as device-tested.

## Implemented paths to verify in the final pass

- [x] Pure URL normalization and bounded tab snapshot normalization have unit coverage.
- [x] Android location/camera/microphone runtime permission request bridge and allow/deny result handling.
- [x] Gecko content permission prompt provides Allow, Block and Not now; Site Info can change stored decisions.
- [x] Camera/microphone origin decisions are separate from Android runtime permissions.
- [x] Website notifications use Gecko WebNotificationDelegate and Android channel APIs.
- [x] API 33+ runtime notification permission, app-level site-notification switch, click/dismiss callbacks and private-browsing suppression.
- [x] Shared theme and locale preferences are applied to native screens and local home page.
- [x] Arabic RTL home page and core native screens; touch targets/content descriptions improved.
- [x] DownloadManager reason codes, status/progress, missing IDs, open/cancel/remove and filename reservations.
- [x] Crash recovery attempt, manual retry state, inactive-session deactivation and bounded tab restoration.

## Final automated CI gates (run once after implementation is complete)

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
- [ ] Opening, switching, closing and restoring tabs does not leak GeckoSessions.
- [ ] The 20-tab cap is enforced for new tabs and popups.
- [ ] Backgrounding, returning, low-memory callbacks and repeated content-process crashes recover without losing unrelated tabs.
- [ ] External links and share intents work.
- [ ] Bookmark/history add, remove and clear persist correctly.
- [ ] Site permission prompt appears only after a site request; Android deny, app settings re-grant, per-origin allow/block/ask and dialog cancellation behave correctly.
- [ ] Site Info refreshes stored Gecko permissions after every change; persistent storage explains its non-revocable grant.
- [ ] API 33+ notification permission prompt appears on first eligible request; app toggle, OS-level disable, tap, dismiss and private browsing are correct.
- [ ] BrowseX downloads list includes only IDs registered by BrowseX.
- [ ] Downloads complete, open, cancel, remove and survive process restart; missing Android records are clearable.
- [ ] Verify Android 8–9 legacy storage permission and Android 10+ scoped-storage behavior.
- [ ] Verify cookies, redirects, duplicate names, storage-full, network loss, HTTP failures and POST/custom-header limitations. DownloadManager re-fetches the URL; it is not a byte-for-byte continuation of every original Gecko request.
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
