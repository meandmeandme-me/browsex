# BrowseX QA checklist

## Required automated gates

- [ ] Gradle configuration resolves reproducibly.
- [ ] Kotlin compilation succeeds.
- [ ] Unit tests pass.
- [ ] Android lint completes with no new errors.
- [ ] Debug APKs are generated for `armeabi-v7a`, `arm64-v8a` and universal output.
- [ ] APK package ID and minSdk are correct.
- [ ] APKs contain the expected GeckoView native libraries for each ABI.
- [ ] CI publishes artifacts only after every required gate passes.

## Manual device matrix

| Device profile | Required checks |
|---|---|
| Android 10, 2 GB RAM, 720px width | install, cold start, memory stability, keyboard, navigation, tabs, rotation |
| Android 13+ phone | notifications permission, downloads, system bars, share sheet |
| Android tablet | multi-column tabs, reader, settings, landscape |
| Small-screen / large font | no clipped controls, scrollable settings and dialogs |

## Functional smoke tests

- [ ] Home page loads offline from app assets.
- [ ] HTTP and HTTPS navigation work; unsafe schemes are rejected.
- [ ] Search engine selection persists after restart.
- [ ] Back/forward state updates correctly.
- [ ] Opening, switching and closing tabs does not leak sessions.
- [ ] External links and share intents work.
- [ ] Bookmark/history add, remove and clear persist correctly.
- [ ] Downloads are intercepted, saved, visible and openable (not just delegated to the system Downloads app).
- [ ] Reader mode handles non-article pages and extraction failures gracefully.
- [ ] Site permission prompts are user-initiated and can be revoked.
- [ ] Rotation/fullscreen returns the browser chrome correctly.
- [ ] App survives process recreation and low-memory conditions.

## Release rules

Never call a build successful until its workflow conclusion is `success` and the artifact is attached to that exact commit. Never claim physical-device validation unless it was performed on a device. Do not overwrite or ask the user to uninstall an existing install as a troubleshooting shortcut.
