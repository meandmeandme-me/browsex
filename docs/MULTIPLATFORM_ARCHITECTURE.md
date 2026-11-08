# BrowseX — cross-platform product and architecture foundation

Status: planning contract. The current delivered client is Android only. Windows, macOS, Linux and Android TV are future targets, not existing builds.

## Direction

Build one browser product with shared domain behavior, account identity, sync protocol, privacy rules, localization and design tokens. Keep the operating-system shell, browser engine, secure storage, permissions, files, notifications and window management behind native platform adapters.

Planned order:
1. Android phone/tablet (current).
2. Android TV with a dedicated remote-first interface.
3. Windows and macOS desktop clients.
4. Linux desktop client with distribution-aware packaging.

## Shared-code strategy

Adopt Kotlin Multiplatform gradually for pure Kotlin domain and sync logic only after boundaries are stable. Candidate areas: domain models, validation, consent rules, sync record versioning, conflict resolution, localization metadata and design tokens. Do not migrate GeckoView, Android UI, Android permissions or platform storage into common code. Do not create empty Gradle modules just to make a tree look complete.

## Browser engine strategy

- Android: GeckoView remains the current engine.
- Windows: evaluate WebView2 and Chromium Embedded Framework.
- macOS: evaluate WKWebView and Chromium alternatives.
- Linux: evaluate WebKitGTK and Chromium Embedded Framework.
- Android TV: use the Android engine adapter only after D-pad, media, focus, accessibility and memory testing.

No desktop engine is selected yet. Compare web compatibility, security update cadence, sandbox/process isolation, extension APIs, media codecs/DRM, accessibility, low-end performance, binary size, license obligations, packaging and update distribution. The engine is a platform capability, not BrowseX's shared domain.

## Account and sync

Use ACCOUNT_SYNC_FOUNDATION.md, AccountDomainModels.kt and ACCOUNT_SYNC_UX_MAP.md. Keep provider authentication, BrowseX account/profile, device registry, per-category consent, encrypted sync transport and export/deletion as separate services. Local browsing must remain complete without sign-in.

Recommended sign-in is OIDC Authorization Code + PKCE with state, nonce, issuer, audience, signature and exact redirect validation. Never embed a client secret in a distributed client. Sync records require stable IDs, schema versions, revisions, tombstones, idempotency keys, bounded offline queues, deterministic conflict resolution, key recovery/rotation and device revocation. Private tabs/history, cookies, site grants, form contents and auth tokens are never sync records.

## TV platform boundary

Android TV / Google TV is a planned Android target using the Android client only after a dedicated remote-first layout, Leanback launcher declaration, touchscreen-not-required declaration, large-screen banner and full D-pad/focus QA are complete.

Samsung Tizen and LG webOS are separate operating-system platforms. An Android APK cannot be installed on those TVs merely because its layout is responsive. Tizen offers its own TV web-app package and APIs; webOS has its own web-app platform, packaging and distribution workflow. Reuse product/domain rules, design tokens and compatible web components where practical, but plan separate platform shells, engine/media adapters, signing and store submissions.

## Platform UX

Desktop: resizable windows, keyboard shortcuts, context menus, drag/drop, file chooser integration, native credential vaults and desktop notifications.

TV: D-pad completion of primary tasks, visible focus state, no hover-only actions, large readable typography, overscan-safe layout and no phone-only bottom navigation.

All clients: screen reader semantics, high contrast, text scaling, reduced motion, RTL/LTR, locale-aware formatting and safe mixed-direction URLs/emails.

## Delivery gates

Before account release: provider security review, OAuth redirect tests, secure-vault tests, revocation and deletion tests, and no-secret logging scan.

Before sync release: encryption/key recovery threat model, duplicate/offline delivery tests, schema migration, conflict, device revoke, export/deletion and private-data exclusion tests.

Before each new platform: engine compatibility and license review, sandbox review, accessibility review, supported-device matrix, update/signing pipeline and privacy review.
