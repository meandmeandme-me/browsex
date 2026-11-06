# Wren Browser

A privacy-focused Android web browser built with Kotlin and Mozilla GeckoView. The repository is currently named `browsex`; the product name is **Wren Browser**.

## Project status

Active development: `rewrite-v2`. Design preparation: `wren-design-prep`. Keep experimental work off `main`.

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
- [Installation and APK troubleshooting](docs/INSTALLING.md)
- [World-browser product requirements](docs/WORLD_BROWSER_PRODUCT_REQUIREMENTS.md)
- [Account provider implementation plan](docs/ACCOUNT_PROVIDER_IMPLEMENTATION_PLAN.md)
- [Figma design brief and screen inventory](docs/FIGMA_DESIGN_BRIEF.md)

## Working rules

1. Preserve user data and backward compatibility.
2. Keep `main` stable; develop and validate changes on feature branches.
3. Prefer small, testable modules and explicit interfaces over empty placeholder packages.
4. Keep UI state, domain rules, persistence, and browser-engine integration separated.
5. Update tests and documentation whenever behavior changes.
6. Mark each feature as implemented, tested, or planned accurately.
