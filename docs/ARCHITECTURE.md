# BrowseX architecture

## Current application shape

BrowseX is a single Android application module (`:app`) using Kotlin, AndroidX AppCompat/Core and Mozilla GeckoView.

- `MainActivity`: application shell for browser chrome, tab/session coordination, navigation, delegates, settings entry points, sharing and fullscreen behavior.
- `BrowserRuntimeOwner` and `BrowserTab`: isolated ownership of the process-wide GeckoRuntime and serializable-per-tab UI/session state; private mode is selected before opening each GeckoSession.
- `BrowserStore`: SharedPreferences persistence for bookmark folders/items with legacy migration, generated stable bookmark IDs, title-aware history, validated JSON backup/import with conflict preview, home preferences, open-tab snapshots, permission decisions and BrowseX-owned DownloadManager IDs / filename reservations.
- `BrowserPalette`: shared light/dark/system color tokens and direction helpers for Android screens.
- `BrowseXPermissionDelegate`: separates Android runtime permission requests from Gecko content and media permission decisions.
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
8. Preserve duplicate URLs in tab snapshots: separate tabs may intentionally display the same page.
9. Bound persisted collections, enforce the 20-tab cap, and validate restored web/local-home URLs.
10. After a content-process crash, reopen and reload the affected session once automatically. A repeated crash leaves an explicit recoverable crashed tab; the user can activate it to retry.

## Feature integration contracts and limits

- **Site permissions:** `BrowseXPermissionDelegate` handles Android location/camera/microphone runtime permissions, Gecko content permissions and media-source grants separately. A site is never granted access just because Android permission exists. Content prompts expose Allow, Block and Not now; Site Info can change a stored content decision to Allow, Block or Ask. Camera/microphone decisions are stored per validated HTTP(S) origin. Android-level denial remains controlled by Android Settings. Persistent-storage grants cannot be revoked through GeckoView after they are granted.
- **Website notifications:** Gecko's WebNotificationDelegate is bridged to an Android notification channel. Android 13+ POST_NOTIFICATIONS is requested at runtime; the app-level website-notification switch can disable delivery. Private-browsing notifications are discarded. Tapping an Android notification invokes Gecko's click callback and opens a safe HTTP(S) source when one exists; dismissing invokes the close callback. OS-level notification controls can still disable delivery.
- **Theme, language and accessibility:** shared palette supports System/Light/Dark, Arabic/English/System language, Android locale direction and RTL, plus a matching preferred color scheme and locale for Gecko content. The local home page uses these preferences. Core native screens use the palette and larger touch targets/content descriptions. This does not translate website content, and some less-common browser prompts/toasts may still use English.
- **Downloads:** Gecko external responses are validated as HTTP(S), then `DownloadResponseWriter` consumes the exact `WebResponse.body` stream on a dedicated single-thread executor and writes it to MediaStore on Android 10+ or the public Downloads directory on Android 8/8.1/9. This does not replay the URL, so POST bodies, authenticated responses, cookies, one-time URLs and the original response bytes are preserved as delivered by Gecko. Android 8/8.1/9 still requires the legacy write-storage runtime permission; a pending response is retained until the user answers and closed if denied. Legacy file opens use a scoped FileProvider URI. `BrowserStore` records queued/running/completed/failed/cancelled states and byte progress; `DownloadsPage` shows progress and offers cancellation. Cancellation closes the original response stream and deletes partial output when the writer handles the cancellation. Unfinished records are marked interrupted at the next app startup. There is no process-death continuation or safe resume/retry for one-shot POST/authenticated responses; do not claim resumability.
- **Reader:** explicit user action -> GeckoView PageExtractionController -> native reader presentation -> preserve original URL and navigation state. No page scripts are injected. Current GeckoView extraction uses its default options.
- **Settings:** BrowserStore is the persisted source of truth for theme, language, notifications, bookmarks, history, home visibility/layout and download metadata. Android notification permissions can be managed from the app notification settings screen.
- **Accounts and sync (future contract):** keep identity providers behind a provider-neutral adapter; never couple browser data to Google/Microsoft/Apple SDK models. Sign-in remains optional. Sync must be separately consented per data type, use an explicit encryption/key model and exclude private tabs, cookies, passwords and site permission grants until their security and recovery requirements are designed. No sign-in or cloud sync is implemented today.
- **Sessions:** named sessions store validated regular-tab URLs and active index only; they are capped at ten saved sessions and twenty tabs per session. Recently closed regular tabs are stored separately (maximum ten) and consumed on restore. Private tabs, cookies, form data and live GeckoSession objects are excluded. Opening a saved session respects the live 20-tab limit.
- **Memory:** the app keeps the active GeckoSession and at most two warm background sessions; inactive sessions are closed and recreated from their URL under memory pressure. This preserves URL-level recovery, not in-page form/scroll state.
- **Home shortcuts:** the local start page stores up to eight validated HTTP(S) shortcuts and supports add/edit/remove plus explicit earlier/later ordering; changes persist in local storage and reset restores defaults.
- **Localization:** shared Android string resources now cover browser chrome, Settings, Downloads, Saved Pages, Site Info, Reader and Recall in English and Arabic; language preference accepts validated BCP 47 tags and unknown locales fall back to English until a reviewed locale pack is added.
- **Responsive UI:** use available width rather than device-brand checks; the existing visual tabs workspace uses a wider layout on tablet widths.

## Compatibility

- Minimum Android API: 26 (Android 8.0).
- Target SDK: 35.
- Java/Kotlin bytecode target: 17.
- ABI packaging includes `armeabi-v7a`, `arm64-v8a`, plus a universal APK.
- GeckoView is a native, relatively large dependency. Verify APK size and ABI contents on every release build.
- Android 10 / API 29 is an explicit compatibility test target. Public Downloads on API 26–28 use a runtime storage permission; API 29+ does not request that legacy permission.
