# BrowseX architecture

## Current application shape

BrowseX is a single Android application module (`:app`) using Kotlin, AndroidX AppCompat/Core and Mozilla GeckoView.

- `MainActivity`: currently owns browser chrome, GeckoRuntime lifecycle, tabs, navigation, menus, settings dialogs, saved pages, sharing and fullscreen behavior.
- `BrowserStore`: SharedPreferences persistence for bookmarks, history and search-engine preference.
- `app/src/main/assets/home.html`: local start page loaded through `resource://android/assets/home.html`.
- `AndroidManifest.xml`: network permission, launcher entry point and HTTP(S) deep links.
- `app/build.gradle.kts`: Android/Kotlin configuration, SDK levels, GeckoView and ABI split configuration.

## Runtime/lifecycle invariants

1. Create one process-wide `GeckoRuntime`; do not create one runtime per tab.
2. Each browser tab owns one `GeckoSession`.
3. Attach delegates before loading a tab URL.
4. Only the active tab is attached to the visible `GeckoView`.
5. Release the visible view session before closing sessions in `onDestroy`.
6. Validate external intents; never load arbitrary non-HTTP(S) schemes as browser pages.
7. Persistent user data must remain in app-private storage unless a feature explicitly exports it.

## Planned package boundaries (incremental, compile-checked)

Do not move everything in one large refactor. Extract one responsibility at a time, keeping public APIs small:

- `browser/engine`: runtime creation and GeckoView integration.
- `browser/tabs`: tab model, tab lifecycle and tab switching.
- `browser/navigation`: URL normalization, external-intent validation and navigation state.
- `data`: preferences, bookmarks, history and future download metadata.
- `feature/home`, `feature/reader`, `feature/downloads`, `feature/settings`: screen-level UI and feature logic.
- `ui/components`: shared browser chrome and reusable visual components.

Keep the app as a single Gradle module until these boundaries are stable. Avoid premature multi-module conversion.

## Feature integration contracts

- Downloads: GeckoSession download delegate -> validated download request -> Android DownloadManager or app-managed destination -> visible download state. Handle permission/errors and duplicate names. The current Downloads menu entry only opens Android's Downloads collection; it is not browser download interception.
- Reader: explicit user action -> GeckoView PageExtractionController -> native reader presentation -> preserve original URL and navigation state. No page scripts are injected. Current GeckoView 150 extraction uses its default extraction options; boilerplate-removal tuning is not enabled.
- Site notifications: use GeckoView permission delegates and Android notification permission/channel APIs; request only after a site gesture and only on supported Android versions.
- Settings: preferences have typed defaults and a single source of truth; UI must reflect persisted values.
- Responsive UI: test compact phone, landscape, and tablet widths; use available width rather than device-brand checks.

## Compatibility

- Minimum Android API: 26 (Android 8.0).
- Target SDK: 35.
- Java/Kotlin bytecode target: 17.
- ABI packaging currently includes `armeabi-v7a`, `arm64-v8a`, plus a universal APK.
- GeckoView is a native, relatively large dependency. Verify APK size and ABI contents on every release build.
- Android 10 / API 29 is an explicit compatibility test target.
