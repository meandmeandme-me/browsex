# BrowseX target project tree

This is the intended long-term shape of BrowseX. It is a destination map, not a claim that every package or feature already exists. Create packages when their first real implementation lands; do not add empty placeholder classes.

```text
BrowseX/
├── app/                                  # Android application composition root
│   ├── src/main/
│   │   ├── AndroidManifest.xml           # permissions, activities, deep links
│   │   ├── assets/
│   │   │   └── home.html                 # local start page assets only
│   │   ├── java/com/browsex/app/
│   │   │   ├── BrowseXApplication.kt     # process-wide setup (when needed)
│   │   │   ├── MainActivity.kt           # host/navigation shell; keep thin
│   │   │   ├── BrowserTabRegistry.kt     # open-tab membership and selected-tab state
│   │   │   ├── TabsPage.kt                # responsive tab workspace UI with injected actions
│   │   │   ├── TabLifecyclePolicy.kt     # pure Gecko session budget/memory policy
│   │   │   ├── BrowserPreferencesStore.kt # settings and site-permission persistence
│   │   │   ├── BrowserSessionStore.kt     # regular tabs, named sessions and recently closed tabs
│   │   │   ├── BrowserLibraryStore.kt     # bookmark folders/items, history and backup/import
│   │   │   ├── BrowserDownloadStore.kt    # download queue metadata, IDs and filename policy
│   │   │   ├── BrowserPreferencePolicy.kt # pure preference normalization
│   │   │   ├── di/                       # dependency wiring; introduce only when useful
│   │   │   ├── core/
│   │   │   │   ├── common/               # Result, dispatchers, shared primitives
│   │   │   │   ├── model/                # shared domain models
│   │   │   │   ├── designsystem/         # colors, typography, spacing, shapes
│   │   │   │   ├── ui/                   # reusable buttons, sheets, dialogs, states
│   │   │   │   └── platform/             # Android capability/version wrappers
│   │   │   ├── browser/
│   │   │   │   ├── engine/               # GeckoRuntime, GeckoSession, GeckoView adapter
│   │   │   │   ├── tabs/                 # tab models, lifecycle, restore, switching and named sessions
│   │   │   │   ├── navigation/           # URL parsing, search, intents, back/forward
│   │   │   │   ├── security/             # scheme validation, safe browsing boundaries
│   │   │   │   ├── permissions/          # site permissions and Android permission bridge
│   │   │   │   └── downloads/            # download delegate, queue, progress, persistence
│   │   │   ├── data/
│   │   │   │   ├── local/                # preferences/database/DAO implementations
│   │   │   │   ├── repository/           # repository implementations
│   │   │   │   ├── migration/            # schema and preference migrations
│   │   │   │   └── backup/               # explicit export/import, if supported
│   │   │   ├── domain/
│   │   │   │   ├── model/                # bookmark, history, download, site, settings
│   │   │   │   ├── repository/           # repository contracts
│   │   │   │   └── usecase/              # focused user actions
│   │   │   ├── feature/
│   │   │   │   ├── home/                 # start page, shortcuts, search entry
│   │   │   │   ├── tabs/                 # visual tab workspace
│   │   │   │   ├── bookmarks/            # saved pages, folders, search
│   │   │   │   ├── history/              # visit timeline, search, clear
│   │   │   │   ├── downloads/            # in-app download manager UI
│   │   │   │   ├── reader/               # article extraction and reader view
│   │   │   │   ├── settings/             # preferences and privacy controls
│   │   │   │   ├── siteinfo/             # permissions, certificates, site details
│   │   │   │   ├── notifications/        # site notification inbox and channels
│   │   │   │   ├── share/                # share sheet and send/open actions
│   │   │   │   ├── findinpage/           # page text search
│   │   │   │   ├── translate/            # translation integration (future)
│   │   │   │   ├── accounts/             # provider-neutral identity and account lifecycle (future)
│   │   │   │   ├── sync/                 # opt-in encrypted cross-device sync (future)
│   │   │   │   └── privacy/              # tracking protection/ad blocking (deferred)
│   │   │   └── ui/
│   │   │       ├── browserchrome/        # address bar, toolbar, bottom controls
│   │   │       ├── components/           # shared visual components
│   │   │       ├── navigation/           # app-level destinations
│   │   │       └── adaptive/             # phone, landscape, tablet layouts
│   │   ├── res/                          # Android resources, themes, icons, strings
│   │   └── ...
│   ├── src/test/                         # JVM unit tests: parsing, use cases, stores
│   ├── src/androidTest/                  # device/instrumented UI and lifecycle tests
│   └── build.gradle.kts
├── docs/
│   ├── ARCHITECTURE.md
│   ├── PROJECT_TREE.md                   # this map
│   ├── ROADMAP.md
│   ├── QA_CHECKLIST.md
│   ├── decisions/                        # short architecture decision records
│   ├── design/                           # Uizard screen map, tokens, deviations
│   ├── WORLD_BROWSER_PRODUCT_REQUIREMENTS.md
│   ├── ACCOUNT_SYNC_FOUNDATION.md
│   └── release/                          # signing, versioning, release checklist
├── .github/workflows/                    # test, lint, ABI build, artifact verification
├── gradle/                               # wrapper and dependency version catalog (future)
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
└── README.md
```

## Current implementation files

The current application is intentionally still a single package under `com.browsex.app`. Real files now present include `MainActivity`, `TabsPage`, `BrowserTabRegistry`, `TabLifecyclePolicy`, `BrowserPreferencesStore`, `BrowserPreferencePolicy`, `BrowserSessionStore`, `BrowserLibraryStore`, `BrowserDownloadStore`, `BrowserStore`, `BrowserStorePolicy`, `TabSessionState`, `NavigationInput`, `BrowserTextNormalizer`, `BrowseXStrings`, `BrowserPalette`, `BrowseXPermissionDelegate`, `BrowseXNotificationBridge`, `SiteNotificationActionReceiver`, `DownloadResponseWriter`, `DownloadFileNamePolicy`, `StreamDownloadRecord`, `BookmarkImportPreview`, `RecentlyClosedTab`, `DownloadsPage`, `SiteInfoPage`, `SettingsPage`, `SavedPagesPage`, `RecallPage` and `ReaderPage`. The deeper package tree above remains a target for gradual extraction; it is not a description of current package layout.

## Dependency direction

Keep dependencies pointing inward and avoid feature-to-feature coupling:

```text
UI / feature screens
        ↓
Domain use cases + repository contracts
        ↓
Data repositories / browser adapters
        ↓
Android platform + GeckoView
```

- A feature calls a domain use case; it does not reach into another feature's Activity or private UI.
- Domain code must not depend on Android Views, GeckoView, or concrete storage.
- GeckoView callbacks are translated into app-owned models before reaching feature UI.
- Data implementations satisfy domain repository contracts; UI never reads SharedPreferences/SQLite directly.
- Shared design-system components contain presentation only, not business rules.
- Keep one Gradle module while extracting packages. Split modules only after boundaries and build times justify it.

## Cross-feature contracts

- **Navigation → tabs → engine:** validated navigation commands target a tab; tab lifecycle owns its GeckoSession; engine owns runtime/session adapters.
- **Engine → downloads / permissions / site info:** GeckoView delegates emit typed events; coordinators validate and route them to the relevant domain flow.
- **Features → domain → repositories → data:** bookmarks, history, downloads, settings and notification preferences use repository interfaces and explicit use cases.
- **Settings → platform adapters:** preferences are persisted centrally; platform-specific permission requests remain version-gated and user initiated.
- **Design system → all screens:** shared tokens and components ensure Figma consistency and accessibility.
- **QA → every layer:** unit tests cover pure logic; integration tests cover adapters; instrumented tests cover Android lifecycle and UI.

## Planned cross-cutting capabilities

Accessibility and font scaling; RTL/localization; responsive layouts; offline/error states; crash-safe tab restoration; privacy controls; storage cleanup; telemetry only with explicit privacy review; database/preferences migrations; import/export; secure external intents; performance budgets for low-memory devices; release signing and reproducible artifact checks.

## Rules for growing the tree

1. Add a file only when it owns a real responsibility.
2. Keep `MainActivity` as an application shell; extract one tested responsibility at a time.
3. No global mutable state for tabs, settings, or downloads.
4. Do not persist GeckoView objects; persist serializable tab/session metadata only.
5. Every new feature includes loading, empty, error, permission-denied and success states where applicable.
6. Every new package comes with a clear owner, dependency direction and tests.
7. Keep Android 8/API 26+ compatibility, explicitly smoke-test Android 10/API 29, ARM32/ARM64, 720px phone layouts and tablets.
8. Ad blocking remains deferred until a supported engine integration is implemented and verified end-to-end.
9. Do not split Gradle modules or introduce frameworks just to match this diagram.
