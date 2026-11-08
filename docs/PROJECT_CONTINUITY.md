# Wren Browser — Project Continuity

> Current source of truth for the active branch. Historical BrowseX/rewrite-v2 notes were superseded by the Wren product direction and have been removed from this checkpoint.

## Current checkpoint

- Repository: `meandmeandme-me/browsex`
- Active branch: `wren-design-prep`
- Current HEAD: `3b780232f81e0a455b1b1af41dacf996681e9a68`
- Pull request #11 remains open and unmerged.
- Do not merge into or modify `main` without the owner's explicit instruction.
- Latest CI run: [#42525156452](https://github.com/meandmeandme-me/browsex/actions/runs/42525156452) — success for locale validation, tests, lint, debug APK build, Room schema verification, APK checksums and ARM32/ARM64/Universal uploads.
- CI produced Debug APKs. Release signing was skipped because owner-controlled `BROWSEX_RELEASE_*` secrets are not configured. No release-signed APK is claimed.

## Product direction

- Product name: **Wren Browser**, using the owner-approved Wren bird identity.
- Interaction is informed by familiar Chrome/Safari browser patterns, implemented with original Wren UI and assets.
- Phone default: bottom address/search glass dock for thumb reach. Settings persists Automatic / Top / Bottom in DataStore.
- Glass is progressive enhancement. Current chrome uses tinted translucent glass with matte/high-contrast/performance fallbacks. **Live in-window backdrop blur is intentionally disabled** until a concrete renderer is implemented and benchmarked against the actual GeckoView composition path.
- Android minSdk is API 26. Android 8–10 use the tinted-glass fallback; core browsing must never depend on blur.
- Arabic RTL and English LTR are first-class. Keep directional controls mirrored and preserve mixed-direction URL/email readability.
- The browser remains usable without sign-in, cloud sync or telemetry. Do not show fake connected/sync/provider states.

## Implemented foundations

### Adaptive presentation and performance

- `DeviceCapabilityProbe` and `AdaptiveRuntimePolicy` provide resource tiers and Gecko live-session budgets.
- `AdaptivePresentationPolicy` is a separate pure resolver for current-window class, address placement, material tier, motion policy and optional visual work.
- `AdaptivePresentationProbe` reads current Activity window bounds, acceleration, touch/D-pad/Leanback/TV mode, RAM tier, low memory, battery/thermal state, system animation preference and API 36+ high-contrast text where exposed.
- `MainActivity` re-evaluates on window resize, Activity resume, power-saver/thermal changes, memory-pressure callbacks and IME visibility.
- AndroidX JankStats keeps a rolling 60-frame boolean sample while resumed. Sustained jank downgrades visual effects; recovery can restore the normal presentation. No URL, page text or browsing history is collected for this.
- Phone, landscape, tablet and large-window layouts are resolved from current window size, not marketing model names.

### Address bar and navigation

- DataStore persists `addressBarPosition`; Settings exposes Automatic / Top / Bottom.
- Compact phone windows honor Top/Bottom; wider windows choose a top/side presentation to preserve usable page space.
- The navigation row hides while the phone keyboard is visible and returns when the IME closes. TV keeps its remote navigation actions.

### Tabs, library and remote focus

- `AdaptiveTabGridPolicy` resolves 2 columns for compact phone/landscape, 3 for medium, 4 for expanded, 5 for large/TV and 6 for extra-large windows.
- Shared focus overlays are applied to main toolbar controls, tab cards, settings rows, library/folder controls and download actions.
- TV mode enlarges tab cards, settings, library and downloads typography/targets, and uses five large tab columns.
- Arabic/English tab-active state is localized.

### Android TV / Google TV — initial implementation, not certification

- The manifest contains optional Leanback and touchscreen-independent TV features, a TV banner asset and a Leanback launcher intent filter.
- TV detection uses Leanback/Television features or Android TV UI mode. A D-pad attached to a regular phone/tablet alone does not activate TV mode.
- TV chrome uses a larger top dock, bigger targets and remote-first focus. The start page has a `bxtv` layout with larger shortcuts, focus-visible styling and an initial focus target.
- **Not yet device-certified:** physical/emulator D-pad QA remains required for search/IME handoff, home shortcuts, page links, tab tray, menus/dialogs, settings, downloads, Back behavior, focus restoration and overscan. Launcher metadata is not proof of Play certification or complete TV compatibility.
- Samsung Tizen and LG webOS are separate future clients; an Android APK does not run on those platforms.

### Browser engine, data and privacy

- GeckoView remains the Android engine.
- Room repositories cover bookmarks/folders, history, regular tab snapshots, named sessions/recently closed tabs, download metadata and per-origin site permissions.
- DataStore is the primary source for app settings. `BrowserStore` / SharedPreferences compatibility paths have not all been removed; audit every consumer before cleanup.
- Gecko one-shot `WebResponse` downloads must preserve their original response body. Do not replace with a URL-only re-fetch that can lose POST bodies, headers or authentication.
- uBlock Origin installation/management and Gecko tracking-protection fallback exist. Real-site blocking, extension recovery/update and low-end performance still require device verification.
- Google/Microsoft sign-in and cloud sync remain planning/foundation only; no live account or sync claim should be exposed.

## QA and artifact boundary

- Latest CI success is tied to commit `3b780232f81e0a455b1b1af41dacf996681e9a68`; it proves that workflow's locale checks, tests, lint and debug builds only.
- Latest Universal Debug APK from run #42525156452 contains ARM32 and ARM64 GeckoView native libraries. Verify checksums before sharing a copied APK.
- No physical install test on TECNO SPARK 5 Air / Android 10 is claimed.
- No physical Android TV / Google TV D-pad certification is claimed.
- No release-signed APK or production release is claimed.
- No Tizen/webOS support is claimed.

## Remaining priorities

1. **TV input certification:** verify initial focus, D-pad navigation, keyboard/IME launch/close, movement between native chrome and Gecko content, tab tray, menus/dialogs, Back behavior, scroll and focus restoration on Android TV/Google TV.
2. **Owner phone install QA:** install the exact current Universal Debug APK on TECNO SPARK 5 Air / Android 10; verify cold start, address search, keyboard docking, tabs, downloads, privacy and uBlock. Record actual outcomes and logs.
3. **Window/device matrix:** test split-screen, compact landscape, tablets, fold/hinge posture, connected displays and accessibility font/contrast settings.
4. **Blur research gate:** prototype a real in-window blur renderer only if it works with GeckoView composition and wins against tinted glass in measured frame-time/memory tests. Keep disabled otherwise.
5. **TV release gate:** complete D-pad/focus/IME/overscan QA and review Leanback distribution requirements before claiming Android TV support.
6. **Localization/accessibility:** broaden real translation packs beyond Arabic/English, audit TalkBack, font scaling, contrast and mixed RTL/LTR.
7. **Release engineering:** configure owner-controlled signing secrets and verify install/upgrade/signing before producing release APKs.
8. **Accounts/sync:** implement only after provider registration, secure credential handling, backend, key lifecycle, consent, revocation, conflict handling and deletion/export design.
