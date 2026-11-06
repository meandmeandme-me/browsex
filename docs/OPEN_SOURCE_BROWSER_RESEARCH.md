# Open-source browser research notes for BrowseX

Research date: 2026-11-04. This is an architecture/product study, not permission to copy code. Any code reuse requires a file-by-file license and dependency review.

## Firefox for Android / GeckoView

Sources:
- https://firefox-source-docs.mozilla.org/mobile/android/
- https://firefox-source-docs.mozilla.org/mobile/android/geckoview/contributor/geckoview-architecture.html
- https://github.com/mozilla-mobile/firefox-android

Observed architecture:
- Mozilla separates the Gecko engine, GeckoView embedding API, reusable Android Components and the Fenix frontend.
- GeckoView deliberately does not define a browser tab concept; the embedding application owns tab records and storage.
- A GeckoRuntime is normally process-wide; GeckoSession represents a web-site instance; GeckoView is the rendering view.
- GeckoView retains limited state and expects the embedding app to own much of browser persistence.

BrowseX decisions:
- Keep the app-owned BrowserTab registry and process-wide BrowserRuntimeOwner.
- Continue extracting session orchestration, permissions, downloads, history and storage behind feature boundaries.
- Do not store UI state inside engine-specific classes.
- Keep GeckoView as Android's current engine; its public API is not a desktop engine abstraction.

License note: Fenix source is MPL-2.0. GeckoView and Mozilla components have their own licensing and notices; review each dependency and file before reuse.

## Brave

Sources:
- https://github.com/brave/brave-browser
- https://github.com/brave/brave-core
- https://brave.com/privacy/browser/

Observed strengths:
- One product spans Android, iOS, Windows, macOS and Linux, with platform-specific browser shells.
- Brave Core maintains Chromium customization and a separate ad-blocking engine integration.
- Brave documents encrypted sync where the decryption key remains on user devices; losing keys can mean losing synced data.

BrowseX decisions:
- Share product/domain/sync behavior, not one forced UI or browser engine.
- Treat ad/tracker protection as a dedicated engine/service with versioned filter updates, explicit controls, exception management and measurable performance.
- Decide encryption-key recovery before promising account sync. A design that cannot recover lost keys must say so clearly and offer user-controlled recovery options where secure.
- Brave is MPL-2.0 at the browser repository; check all subprojects and third-party dependencies individually.

## Cromite

Sources:
- https://github.com/uazo/cromite
- https://github.com/uazo/cromite/blob/master/docs/FEATURES.md
- https://github.com/uazo/cromite/blob/master/docs/FAQ.md

Observed strengths:
- Android, Windows and Linux release targets from a Chromium fork.
- Built-in ad blocking, tracking/privacy hardening and granular site/privacy controls.
- The feature documentation also shows trade-offs: disabling APIs can affect compatibility, DRM and push notifications; extension and cloud integrations are not universally supported.

BrowseX decisions:
- Offer privacy controls with understandable per-feature explanations; avoid turning every hardening toggle on without compatibility testing.
- Maintain an allowlist/exception flow and show when protection breaks a site.
- Preserve media, notifications, sign-in and accessibility compatibility as explicit test dimensions.
- Cromite is GPL-3.0 with project patches under additional stated terms. Do not copy its implementation into BrowseX unless a deliberate GPL compatibility review approves the consequence.

## DuckDuckGo Android

Sources:
- https://github.com/duckduckgo/Android

Observed strengths:
- A mature open-source Android browser project with privacy-oriented features and a large, active codebase.
- Apache-2.0 license at the repository level.

BrowseX decisions:
- Study its Android UX, privacy onboarding, feedback and settings organization as product references.
- Do not infer that every feature or dependency is Apache-2.0; inspect dependency and asset licenses before reuse.

## Cross-project product lessons

1. Browser engines and browser product logic are separate layers.
2. Privacy protection is a maintained subsystem, not a cosmetic toggle: filters, update cadence, exceptions, breakage handling and compatibility tests all matter.
3. Accounts are optional; sync consent is separate from authentication.
4. Cross-device sync requires explicit key lifecycle, revocation, conflict handling and deletion behavior.
5. Platform parity means coherent product behavior with honest capability differences, not identical UI or identical engine.
6. A global browser needs accessibility, language/RTL, low-memory behavior, media, notifications, downloads and site compatibility as first-class test dimensions.

## Reuse policy

- Prefer learning architecture and UX patterns over copying implementation.
- Before importing code: identify exact source files, SPDX/license headers, repository license, dependencies, patents/trademarks, attribution and distribution obligations.
- Do not combine GPL-derived code with the current BrowseX codebase without an explicit license decision.
- Track upstream engine security releases and supported ABI/OS versions; never assume a fork remains secure merely because its UI is current.
