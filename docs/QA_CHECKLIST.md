# BrowseX QA checklist

## Automated CI gates

- [ ] Gradle configuration resolves reproducibly.
- [ ] Kotlin compilation succeeds.
- [ ] Unit tests pass.
- [ ] Android lint completes with no new errors.
- [ ] Debug APKs are generated for `armeabi-v7a`, `arm64-v8a` and universal output.
- [ ] APK package ID and minSdk are correct.
- [ ] APKs contain the expected GeckoView native libraries for each ABI.
- [x] CI uploads debug artifacts only after every required gate passes; public release publishing waits for stable signing.

## Functional checks (device validation still required)

- [x] Unit coverage exists for safe URL normalization and bounded tab snapshot normalization.
- [ ] Home page loads offline from app assets.
- [ ] HTTP and HTTPS navigation work; unsafe schemes are rejected.
- [ ] Search engine selection persists after restart.
- [ ] Back/forward state updates correctly.
- [ ] Opening, switching, closing and restoring tabs does not leak GeckoSessions.
- [ ] The 20-tab cap is enforced for new tabs and popups.
- [ ] External links and share intents work.
- [ ] Bookmark/history add, remove and clear persist correctly.
- [ ] BrowseX downloads list includes only IDs registered by BrowseX, not the system-wide download collection.
- [ ] Downloads complete, open, cancel, remove and survive process restart.
- [ ] Verify download cookies, redirects, duplicate names, failure states and POST/custom-header limitations on device. DownloadManager re-fetches the URL; it is not a byte-for-byte continuation of every original Gecko request.
- [ ] Reader mode handles non-article pages and extraction failures gracefully.
- [ ] Site permission prompts are user-initiated and can be revoked (not implemented yet).
- [ ] Website notifications work through GeckoView and Android notification channels (not implemented yet).
- [ ] Rotation/fullscreen returns browser chrome correctly.
- [ ] App survives process recreation and low-memory conditions.

## Device matrix

| Device profile | Required checks |
|---|---|
| Android 10, 2 GB RAM, 720px width | install, cold start, memory stability, keyboard, navigation, tabs, rotation |
| Android 13+ phone | notification runtime permission, downloads, system bars, share sheet |
| Android tablet | multi-column tabs, reader, settings, landscape |
| Small-screen / large font / RTL | no clipped controls, scrollable screens, readable Arabic layout |

## Release rules

Never call a build successful until its workflow conclusion is `success` and the artifact is attached to that exact commit. Never claim physical-device validation unless it was performed on a device. Do not overwrite or ask the user to uninstall an existing install as a troubleshooting shortcut.
