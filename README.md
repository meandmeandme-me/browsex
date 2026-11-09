# Wren Browser

A privacy-focused Android web browser built with Kotlin and Mozilla GeckoView. The repository is currently named `browsex`; the product name is **Wren Browser**.

## Project status

Active implementation and approved UI redesign: `wren-design-prep`. Keep all work on this branch until its checks and device tests are reviewed; do not modify `main`.


### Approved Wren UI direction (2026-11-08)

- The supplied Wren bird mark is the visible product identity.
- The Android phone browser uses a bottom-anchored address/search field inside a restrained glass-style dock, with common navigation actions grouped beneath it.
- Interaction patterns are informed by familiar Chrome and Safari behavior, but the implementation and visual assets are Wren's own.
- Light/dark glass surfaces keep readable text and a translucent fallback on Android 8–11; essential controls do not depend on backdrop blur.
- See [Wren design brief](docs/FIGMA_DESIGN_BRIEF.md), [screen map](docs/design/UIDIZARD_SCREEN_MAP.md), and [UI blueprint](docs/design/UI_BLUEPRINT.md).

### Implemented foundations

- GeckoView browsing, navigation, a bounded tab workspace, private tabs, tab/session restore, recently closed tabs, and named sessions.
- Home page customization, search, bookmarks, history, in-page search, Reader Mode, sharing, and unified Recall search.
- Website permission prompts and controls, Android runtime permission handling, website notifications, and Android notification channels.
- Light/dark/system appearance, Arabic and English resources, and RTL support on core screens.
- Download management with progress, cancellation, error states, and direct response-stream handling.
- Local encrypted password storage using Android Keystore, explicit user confirmation, biometric/device authentication, and optional Android Autofill (fill-only).
- GeckoView Safe Browsing and optional official uBlock Origin WebExtension integration.
- Provider-neutral account/sync models and tested OIDC Authorization Code + PKCE request/callback foundations.

## Not yet complete

- Google and Microsoft sign-in, token exchange/validation, and cloud synchronization.
- Production Room/DataStore storage cutover; SharedPreferences remains the operational source pending migration verification.
- Full locale coverage beyond English and Arabic.
- Physical-device verification across Android versions, screen sizes, low-memory devices, and all critical flows.
- Stable production release signing and upgrade verification.

Do not present planned work as shipped functionality.

## Build and test

Requirements: JDK 17, Android SDK 36, Gradle 8.13.

```bash
gradle testDebugUnitTest lintDebug assembleDebug
```

Debug APK output: `app/build/outputs/apk/debug/`.

CI runs JVM tests, Android Lint, APK checks, and artifact generation. A successful CI run is not a substitute for physical-device testing.

## Important release notes

- Minimum supported Android version: Android 8.0 (API 26).
- Debug APKs may not update an existing installation if their signing certificates differ.
- Never commit keystores, passwords, API secrets, OAuth client secrets, or private signing material.
- DownloadManager has platform limitations for replaying some original requests; direct Gecko response-stream downloads preserve the response body.
- uBlock Origin is installed from the official Mozilla Add-ons distribution after user consent; verify extension behavior on real devices before release.

## Project documentation

- [Architecture](docs/ARCHITECTURE.md)
- [Project structure and boundaries](docs/PROJECT_TREE.md)
- [Roadmap](docs/ROADMAP.md)
- [QA checklist](docs/QA_CHECKLIST.md)
- [Implementation audit and release gates](docs/IMPLEMENTATION_AUDIT_2026-11-06.md)
- [Installation and APK troubleshooting](docs/INSTALLING.md)
- [World-browser product requirements](docs/WORLD_BROWSER_PRODUCT_REQUIREMENTS.md)
- [Account provider implementation plan](docs/ACCOUNT_PROVIDER_IMPLEMENTATION_PLAN.md)
- [Figma design brief and screen inventory](docs/FIGMA_DESIGN_BRIEF.md)
- [Wren browser, Glass UI, adaptive performance and TV research](docs/WREN_ADAPTIVE_UX_RESEARCH_2026-11-08.md)

## Working rules

1. Preserve user data and backward compatibility.
2. Keep `main` stable; develop and validate changes on feature branches.
3. Prefer small, testable modules and explicit interfaces over empty placeholder packages.
4. Keep UI state, domain rules, persistence, and browser-engine integration separated.
5. Update tests and documentation whenever behavior changes.
6. Mark each feature as implemented, tested, or planned accurately.


## Android compatibility

- Minimum Android: API 26 (Android 8.0).
- Compile SDK: 36; target SDK: 36 (Android 16) for current Google Play submissions.
- Adaptive UI target: compact and large phones, foldables, tablets, desktop/connected displays, and a dedicated Android TV remote flow. Samsung Tizen and LG webOS require separate clients.
