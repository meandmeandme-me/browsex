# BrowseX — Open-source browser audit and product gaps

> Baseline: rewrite-v2 at c62542c9e21fca5562e117a48b30be94af658f1b. This is a source review, not a build, security certification, performance benchmark or device test.
> Competitor projects are references. Never copy code/assets without checking the exact license, notices, dependencies and attribution obligations.

## Executive assessment

BrowseX has a meaningful GeckoView foundation: regular/private tabs, bounded restore, permissions, Android website notifications, bookmarks/folders, local history, downloads, reader extraction, privacy controls and home customization. It is not yet a world-ready browser. The biggest gaps are architectural boundaries, durable storage, internationalization, release engineering, integration/device tests and several expected browser capabilities.

**Direction:** keep GeckoView for now, but do not treat current code as sacred. Rework or replace weak subsystems when justified. Keep the app usable without an account and private browsing local-only. Re-evaluate the engine only against capability, Android support, ABI size, security updates and maintenance cost.

## Verified source-tree assessment

| Area | Present | Gap / qualification |
|---|---|---|
| Engine | GeckoView 150, one process-wide GeckoRuntime, per-tab GeckoSession | No documented engine upgrade/security-update release gate. |
| Tabs | 20-tab cap, private sessions, named sessions, recently closed tabs, lazy sessions, crash retry | No groups/containers/pinned tabs/split view; lifecycle logic remains in MainActivity. |
| Navigation | HTTP(S) validation, IDN handling, search engine selection, external links | No documented HTTPS-first policy, safe-browsing integration or complete offline/error UX. |
| Privacy | Site permissions, notifications, selected site-data clearing, private notification suppression | No active tracker/content-blocking engine, privacy dashboard, cookie isolation UX or threat model. |
| Downloads | DownloadManager plus exact Gecko response stream writer, progress/status/cancel, interrupted-record marking | No safe resume/retry, guaranteed background continuation, durable queue/service architecture or Android-backed tests. |
| Library | Bookmarks/folders, history, Recall, JSON backup/import | BrowserStore is a large SharedPreferences-backed store; no Room/DAO layer or transactional migrations. |
| Reader | GeckoView extraction and native presentation | No typography controls, reading list/offline library or spoken reading. |
| Localization | English and Arabic resources; BCP 47 preference parsing and RTL helpers | Only two locale resource sets; secondary prompts/home localization and locale-aware formatting remain incomplete. |
| Accessibility/adaptive UI | Larger targets/descriptions in core flows; basic width-aware tab layout | No verified TalkBack, switch/keyboard, font scaling, contrast, foldable or tablet coverage. |
| Accounts/sync | Planning documents only | No provider adapters, token vault, sync engine, encrypted protocol or device management. |
| Passwords/passkeys | No complete password manager/autofill/passkey product | Needs Android Autofill/Credential Manager, secure storage, biometric policy and threat model. |
| Extensions | GeckoView engine foundation | No end-user extension catalogue/manager or verified install/update/permission lifecycle. |
| Translation | No integrated product flow | No provider, on-device model, consent or offline strategy. |
| Release | CI defines tests, lint, ABI APK build, APK verification and checksums | Gradle release is not production-signed/minified; no stable release/update strategy. |
| Quality | Six JVM test files and CI workflow | No androidTest suite in inspected tree; no verified current-head CI result or device QA established by this audit. |

## Open-source projects worth learning from

### Firefox for Android / Fenix + Android Components
- Learn the layered separation between GeckoView engine, reusable browser components and frontend.
- Study extension ecosystem, tracking protection, private browsing, tab collections, reader and sync consent.
- Keep one Gradle module initially while extracting packages/contracts; split modules only when justified.
- Sources: https://github.com/mozilla-mobile/firefox-android ; https://firefox-source-docs.mozilla.org/mobile/android/ ; https://mozilla.github.io/geckoview/

### Fennec F-Droid
- Useful patterns: customizable start page/navigation bar, tab collections, one-tap private mode, add-ons, tracking protection and sync.
- Its listing is a reminder that removing telemetry/proprietary pieces does not automatically mean an app has no service connections.
- Source: https://f-droid.org/packages/org.mozilla.fennec_fdroid/

### WebLibre
- Study isolated browsing containers, tree-style tabs, local-first search across tabs/history/feeds and optional proxy/Tor routing.
- These require proof that GeckoView offers the necessary isolation/routing guarantees before BrowseX promises them.
- Source: https://github.com/FaFre/WebLibre

### Cromite
- Study privacy-first defaults, reduced background communication, DNS-over-HTTPS, download warnings, bookmark import/export and background media.
- Its Chromium engine patches cannot simply be transplanted into GeckoView. Anti-fingerprinting is not anonymity.
- Sources: https://github.com/uazo/cromite ; https://github.com/strogo/cromite

## Recommended gaps by priority

### P0 — foundations
1. **Modular browser core:** extract TabRepository/TabSessionCoordinator, NavigationCoordinator, PermissionCoordinator and DownloadCoordinator. MainActivity becomes a thin host. Define typed state/events and explicit GeckoRuntime/GeckoSession/GeckoView ownership. Add lifecycle tests for configuration changes, process recreation, crashes, background/foreground and memory pressure.
2. **Persistence replacement:** Room (or justified equivalent) for bookmarks, folders, history, download records, saved sessions and future sync metadata. Keep DataStore/preferences for small settings. Add schema versions, atomic migrations, bounded retention and corruption recovery. Migrate existing SharedPreferences data without destructive reset and retain rollback until verified.
3. **Security/privacy architecture:** threat model and data inventory; centralized origin canonicalization, permission state, certificate/security display and external-intent validation; HTTPS-first and safe-browsing evaluation; content blocking as a separate capability with user controls and test fixtures. Never claim anonymity or complete tracking prevention.
4. **Release engineering:** separate debug/release signing, secure release keystore outside source control, version/update policy, artifact provenance, release notes, dependency/license inventory and ABI/package-size reporting. CI must validate the exact candidate commit.

### P1 — everyday browser capabilities
5. **Downloads v2:** durable queue and foreground/background execution where Android permits; resume only when range semantics, validators and authentication make it safe; retry classification, duplicate handling, storage destination, MIME/file safety and cleanup. Preserve exact-response-stream handling for POST/authenticated/one-shot responses; never replay them to fake resume.
6. **Privacy protection:** engine-supported tracker/ad blocking with filter provenance, update policy, exceptions, per-site controls and visible counts; cookie controls/isolation only where engine guarantees support; HTTPS warnings, certificate details, permission expiry/reset and truthful data-clearing scope. Do not expose a non-functional adBlockEnabled switch as protection.
7. **Passwords/passkeys:** prefer Android Autofill and Credential Manager over inventing a vault. Define encryption, biometric unlock, export, recovery and threat model before storing credentials. No credential sync before separate review and opt-in.
8. **Tab productivity:** groups/collections, pinning, search/filter, close-other/duplicate, recently closed and named sessions; optional containers only after GeckoView isolation feasibility; adaptive tablet/foldable workspace.
9. **Reader/media:** typography, spacing, themes, reading list/offline save, PiP/background playback where supported, autoplay controls.

### P2 — global reach
10. **Internationalization:** resource-based translations, plurals, locale-aware dates/numbers, BCP 47 negotiation and font fallback. Test Arabic, Persian, Hebrew, Urdu, Hindi, Bengali, CJK, Thai, Devanagari and mixed-direction URLs/email. Do not advertise all-languages support until verified.
11. **Accounts and encrypted sync:** optional provider-neutral identity boundary for Google, Microsoft personal/work-school, Apple and OIDC. Browser remains usable without sign-in. Per-category consent, device revocation, pause, offline queue, conflict handling, export/delete and recovery. Never sync private tabs, cookies, site permissions or raw provider tokens by default.
12. **Translation/local-first intelligence:** page translation with explicit consent and remote/on-device disclosure; local search across history/bookmarks/tabs/downloads. Any summarization must disclose page-content processing; no silent upload.
13. **User research loop:** usability sessions for search, tab switching, saving/re-finding pages, downloads, recovery, permission understanding and data clearing. Measure task success, time, errors, accessibility and low-end responsiveness. No browsing telemetry by default.

## Target architecture (not current implementation)

~~~text
App shell / navigation
  ├── Browser engine adapter (GeckoRuntime / GeckoSession / GeckoView)
  ├── Browser domain: tabs/session lifecycle, navigation/search, permissions/security, downloads
  ├── Data: Room repositories + migrations, settings DataStore, backup/import-export
  ├── Features: home, tabs, library, reader, privacy, site info, settings, accounts/sync, translation/extensions
  ├── Platform adapters: Android permissions/notifications, Autofill/Credential Manager, foreground downloads
  └── Design system + localization + adaptive UI
~~~

Keep one app module until boundaries are proven. Domain rules should not depend on Android Views or GeckoView types. Translate engine callbacks into BrowseX-owned events before they reach UI.

## Non-negotiable product principles
- Fast, reliable browsing before feature count.
- Works without account, cloud, telemetry or network-dependent extras.
- Private mode is clearly explained and never persisted/synced.
- Privacy controls are real, inspectable and honest about limitations.
- No fake toggles, placeholder sign-in or claims before end-to-end implementation.
- Low-memory behavior must not silently lose active or unsaved user data.
- Accessibility, localization, RTL/LTR and responsive layout are architectural requirements.
- Re-evaluate engine/library choices when requirements outgrow GeckoView; do not preserve old code for sentimental reasons.

## Follow-up checklist
- [ ] Map every feature to source owner, data owner, lifecycle owner and test coverage.
- [ ] Create architecture decision records for persistence, downloads, security, extensions and sync.
- [ ] Measure APK size/startup/tab-switch memory on representative ARM32 and ARM64 devices.
- [ ] Add Android instrumentation and process-death tests.
- [ ] Verify GeckoView feasibility before promising containers, extension installation, passkeys or background media.
- [ ] Establish release signing/update strategy.
- [ ] Complete localization and accessibility test matrix.
- [ ] Conduct user research before finalizing the next UI.

## Licensing and reuse
Before reusing code/assets, inspect the exact file license and notices, preserve required copyright/license text, document modifications and review transitive dependencies. Product inspiration does not grant permission to copy implementation code or branding.