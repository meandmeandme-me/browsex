# BrowseX architecture

## Current application shape

BrowseX is a single Android application module (`:app`) using Kotlin, AndroidX AppCompat/Core and Mozilla GeckoView.

- `MainActivity`: Android composition root for browser chrome, GeckoView attachment, Activity Result launchers and user-facing platform feedback. Tab membership, tab operations, Activity/memory lifecycle, crash recovery, browsing-data transactions and persistence policy are delegated to focused coordinators; UI rendering remains in feature pages.
- `BrowserTabController`: owns identity-safe tab creation, selection, closing, popup routing and activation; session teardown is delegated to `BrowserSessionLifecycleCoordinator`.
- `BrowserTabEventCoordinator`: owns Gecko page progress, history, navigation-state, title, fullscreen, close, download and crash event routing; platform actions are injected from the composition root.
- `BrowserHostLifecycleCoordinator`: owns Activity start/stop, tab persistence on stop/memory pressure and inactive-session deactivation/release.
- `BrowserContentProcessRecoveryCoordinator`: bounds automatic content-process recovery, rejects stale callbacks and closes failed/repeatedly crashed sessions.
- `BrowserBrowsingDataCoordinator`: owns the clear-data transaction, including regular-tab snapshotting, session reset, Gecko storage clearing and selection restoration.
- `BrowserSessionDelegateFactory`: maps GeckoSession progress/navigation/content callbacks into a BrowseX-owned callback contract. It does not directly render UI or persist data.
- `TabsPage`: renders the responsive tab workspace and named/recent session actions through injected callbacks; it does not own GeckoSession lifecycle or mutate browser state directly.
- `BrowserRuntimeOwner` and `BrowserTab`: isolated ownership of the process-wide GeckoRuntime and serializable-per-tab UI/session state; private mode is selected before opening each GeckoSession.
- `BrowserStore`: compatibility facade preserving the existing feature-facing storage API while delegating persistence to focused stores.
- `BrowserDownloadStore`: owns DownloadManager ID tracking, response-stream download records, filename reservations and interrupted-record recovery.
- `BrowserDownloadRequestCoordinator`: owns Gecko response validation, the bounded Android 8–9 storage-permission wait queue, permission-result draining and response-stream cleanup when the host is destroyed. `MainActivity` now supplies the Activity Result launcher and delegates the request lifecycle.
- `BrowserLibraryStore`: owns bookmark folders/items, title-aware history, legacy bookmark migration and validated JSON backup/import with conflict preview.
- `BrowserSessionStore`: owns regular-tab snapshots, named sessions and recently-closed tab persistence; private tabs remain excluded and existing SharedPreferences keys are preserved.
- `BrowserTabPersistenceCoordinator`: owns the snapshot policy for restoring regular tabs and persisting only non-private tab URLs plus the selected regular-tab index; `BrowserHostLifecycleCoordinator` calls it through an injected persistence callback.
- `BrowserPreferencesStore` + `BrowserPreferencePolicy`: isolated browser-wide settings and per-origin media/platform permission decisions; pure BCP 47, theme and home-layout normalization is separately testable. `BrowserSessionStore` and these preference stores currently use the existing SharedPreferences file to preserve user data; a future DataStore/Room migration can now happen behind these boundaries.
- `BrowserPalette`: shared light/dark/system color tokens and direction helpers for Android screens.
- `BrowseXPermissionDelegate`: separates Android runtime permission requests from Gecko content and media permission decisions.
- `BrowserAndroidPermissionQueue`: serializes Android runtime-permission prompts, validates the selected/live GeckoSession, and rejects queued/active Gecko callbacks exactly once on tab cancellation or Activity teardown. The Activity still owns the Activity Result launchers.
- `BrowseXNotificationBridge` + `SiteNotificationActionReceiver`: WebNotificationDelegate adapter, Android notification channel, click and dismiss actions.
- `DownloadsPage`, `SiteInfoPage`, `SettingsPage`, `SavedPagesPage`, `ReaderPage`: feature screens; theme colors and RTL are shared through BrowserPalette. `BrowseXStrings` resolves UI labels from Android locale resources. `BrowserTextNormalizer` applies Unicode NFKC and locale-independent case normalization to library/Recall search.
- `app/src/main/assets/home.html`: local start page; receives the stored language/theme selection through validated local query parameters.
- `AndroidManifest.xml`: version-gated permissions, launcher entry point, HTTP(S) deep links and internal notification receiver.
- `app/build.gradle.kts`: Android/Kotlin configuration, SDK levels, GeckoView and ABI split configuration.

## Runtime/lifecycle invariants

1. Create one process-wide `GeckoRuntime`; do not create one runtime per tab.
2. Each browser tab owns one `GeckoSession`.
3. Attach progress, navigation, content and permission delegates before loading a tab URL.
4. Only the active tab is opened at startup and attached to the visible `GeckoView`; restored background tabs retain validated URLs and open only when selected. Open inactive sessions are marked unfocused/inactive on tab switch and when the Activity stops.
5. Release the visible view session before closing sessions in `onDestroy`.
6. Validate external intents; never load arbitrary non-HTTP(S) schemes as browser pages.
7. Persist serializable regular-tab URLs, active index and named regular-tab session snapshots only; never persist GeckoSession objects, private-tab URLs, cookies or page form data.
8. Private tabs use GeckoSessionSettings.usePrivateMode(true), are excluded from browsing history and startup snapshots, and popup sessions inherit their parent's private mode.
9. Preserve duplicate URLs in tab snapshots: separate tabs may intentionally display the same page.
10. Bound persisted collections, enforce the 20-tab cap, and validate restored web/local-home URLs.
11. After a content-process crash, attempt one automatic recovery. A repeated failure closes the native session but preserves the tab record so the user can explicitly retry.

## Feature integration contracts and limits

- **Site permissions:** `BrowseXPermissionDelegate` handles Android location/camera/microphone runtime permissions, Gecko content permissions and media-source grants separately. A site is never granted access just because Android permission exists. Content prompts expose Allow, Block and Not now; Site Info can change a stored content decision to Allow, Block or Ask. Camera/microphone decisions are stored per validated HTTP(S) origin. Android-level denial remains controlled by Android Settings. Persistent-storage grants cannot be revoked through GeckoView after they are granted.
- **Website notifications:** Gecko's WebNotificationDelegate is bridged to an Android notification channel. Android 13+ POST_NOTIFICATIONS is requested at runtime; the app-level website-notification switch can disable delivery. Private-browsing notifications are discarded. Tapping an Android notification invokes Gecko's click callback and opens a safe HTTP(S) source when one exists; dismissing invokes the close callback. OS-level notification controls can still disable delivery.
- **Theme, language and accessibility:** shared palette supports System/Light/Dark, Arabic/English/System language, Android locale direction and RTL, plus a matching preferred color scheme and locale for Gecko content. The local home page uses these preferences. Core native screens use the palette and larger touch targets/content descriptions. This does not translate website content, and some less-common browser prompts/toasts may still use English.
- **Downloads:** Gecko external responses are validated as HTTP(S), then `DownloadResponseWriter` consumes the exact `WebResponse.body` stream on a dedicated single-thread executor and writes it to MediaStore on Android 10+ or the public Downloads directory on Android 8/8.1/9. This does not replay the URL, so POST bodies, authenticated responses, cookies, one-time URLs and the original response bytes are preserved as delivered by Gecko. Android 8/8.1/9 still requires the legacy write-storage runtime permission; a pending response is retained until the user answers and closed if denied. Legacy file opens use a scoped FileProvider URI. `BrowserStore` records queued/running/completed/failed/cancelled states and byte progress; `DownloadsPage` shows progress and offers cancellation. Cancellation closes the original response stream and deletes partial output when the writer handles the cancellation. Unfinished records are marked interrupted at the next app startup. There is no process-death continuation or safe resume/retry for one-shot POST/authenticated responses; do not claim resumability.
- **Reader:** explicit user action -> GeckoView PageExtractionController -> native reader presentation -> preserve original URL and navigation state. No page scripts are injected. Current GeckoView extraction uses its default options.
- **Settings:** BrowserPreferencesStore owns theme, language, notifications, home visibility/layout and supplemental permission decisions. BrowserLibraryStore owns bookmarks/history, BrowserSessionStore owns tab/session snapshots and BrowserDownloadStore owns download metadata. BrowserStore remains the compatibility facade used by existing screens. Android notification permissions can be managed from the app notification settings screen.
- **Accounts and sync (future contract):** keep identity providers behind a provider-neutral adapter; never couple browser data to Google/Microsoft/Apple SDK models. Sign-in remains optional. Sync must be separately consented per data type, use an explicit encryption/key model and exclude private tabs, cookies, passwords and site permission grants until their security and recovery requirements are designed. No sign-in or cloud sync is implemented today.
- **Sessions:** named sessions store validated regular-tab URLs and active index only; they are capped at ten saved sessions and twenty tabs per session. Recently closed regular tabs are stored separately (maximum ten) and consumed on restore. Private tabs, cookies, form data and live GeckoSession objects are excluded. Opening a saved session respects the live 20-tab limit.
- **Memory:** the app keeps the active GeckoSession and at most two warm background sessions; inactive sessions are closed and recreated from their URL under memory pressure. This preserves URL-level recovery, not in-page form/scroll state.
- **Home shortcuts:** the local start page stores up to eight validated HTTP(S) shortcuts and supports add/edit/remove plus explicit earlier/later ordering; changes persist in local storage and reset restores defaults.
- **Localization:** shared Android string resources cover the current reviewed English and Arabic UI. BCP 47 preference normalization is separate from translation coverage; unsupported locales fall back to default resources. `scripts/check_locales.py` validates resource parity, placeholders and locale-config registration in CI. See `docs/LOCALIZATION.md`; do not claim all-languages support until reviewed locale packs and RTL/accessibility QA are complete.
- **Responsive UI:** use available width rather than device-brand checks; the existing visual tabs workspace uses a wider layout on tablet widths.

## Compatibility

- Minimum Android API: 26 (Android 8.0).
- Target SDK: 35.
- Java/Kotlin bytecode target: 17.
- ABI packaging includes `armeabi-v7a`, `arm64-v8a`, plus a universal APK.
- GeckoView is a native, relatively large dependency. Verify APK size and ABI contents on every release build.
- Android 10 / API 29 is an explicit compatibility test target. Public Downloads on API 26–28 use a runtime storage permission; API 29+ does not request that legacy permission.

## Cross-platform and account foundations

- Current product delivery is Android-only. Windows, macOS, Linux and Android TV are planned targets, not implemented clients.
- Account domain models live in AccountDomainModels.kt and intentionally avoid Android/GeckoView/OAuth SDK dependencies. They are initial pure models, not a functioning account feature.
- `OAuthPkceTransaction` creates cryptographically random OAuth state and RFC 7636 S256 PKCE verifier/challenge pairs, enforces a short callback lifetime, and validates the exact configured redirect target and returned state before yielding an authorization code. It is a security primitive only: provider client IDs, provider authorization/token endpoints, Android callback registration, token vault, account UI and backend are not wired, so Google/Microsoft/Apple sign-in is not available.
- Account sign-in, BrowseX profile, sync consent, device registry and encrypted sync are separate boundaries. Local browsing never requires an account.
- Future shared code should be extracted gradually, with Kotlin Multiplatform evaluated for pure domain and sync logic. Do not move GeckoView, Android permissions, native UI or platform storage into common code.
- Desktop browser engines must be selected per OS after compatibility, security, sandbox, extension, accessibility, performance, licensing and update evaluation. GeckoView remains the Android engine.
- Android TV requires a remote-first focus-navigation design, not a scaled phone layout.
- See MULTIPLATFORM_ARCHITECTURE.md, ACCOUNT_SYNC_FOUNDATION.md and ACCOUNT_SYNC_UX_MAP.md.
## GeckoSession lifecycle ownership

`BrowserTabController` owns tab identity, selection, activation and popup routing. `BrowserSessionLifecycleCoordinator` owns session release for memory pressure, the live-session budget, session reset and teardown. `BrowserHostLifecycleCoordinator` owns Activity visibility and memory-pressure policy. `BrowserContentProcessRecoveryCoordinator` owns bounded crash recovery. `BrowserBrowsingDataCoordinator` owns the clear-data transaction. `BrowserTabEventCoordinator` translates Gecko callbacks into browser state and injected actions. `MainActivity` remains the Android composition root for visible UI, GeckoView attachment and Activity Result launchers. `BrowserAndroidPermissionQueue` owns request ordering, stale-tab rejection, per-session cancellation and teardown completion. Browser orchestration is split into focused boundaries, while platform event callbacks and visual UI remain hosted by the Activity.
## Android permission lifecycle ownership

`BrowserAndroidPermissionQueue` serializes Gecko-originated runtime permission requests while Android Activity Result launchers stay registered with `MainActivity`. It binds each callback to its originating session, rejects requests when their tab is no longer selected/live, and guarantees queued or active callbacks are rejected during session cancellation and Activity teardown. Website notification permission remains a separate Android 13+ flow because it has a distinct user-facing lifecycle.

## Browsing-data lifecycle ownership

`BrowserBrowsingDataCoordinator` snapshots validated regular-tab URLs, closes live sessions before Gecko storage deletion, restores the selected regular tab and persists the resulting snapshot. `MainActivity` supplies the progress dialog and completion feedback; it no longer owns the storage-clear transaction.

## Download lifecycle ownership

`BrowserDownloadRequestCoordinator` validates HTTP(S) Gecko responses, owns the bounded pending-response queue for Android 8–9 storage permission and closes one-shot response bodies on denial, queue overflow, launch failure or Activity teardown. `StreamDownloadCoordinator` owns stream-download metadata, progress updates, completion/failure state and cancellation around `DownloadResponseWriter`. `MainActivity` supplies the platform launcher and user feedback; the writer persists the original Gecko response body rather than re-fetching its URL.


## Account and sync contract boundary

`AccountDomainModels.kt` defines provider-neutral value types. `AccountSyncContracts.kt` defines suspend repository/gateway boundaries for identity, account state, consent, device registry, encrypted transport/cipher, sync records/status and account data lifecycle. These are contracts only; no OAuth provider, token vault, sync server or cloud data path exists yet.
