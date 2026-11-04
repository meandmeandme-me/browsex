# BrowseX architecture

## Current application shape

BrowseX is a single Android application module (`:app`) using Kotlin, AndroidX AppCompat/Core and Mozilla GeckoView.

- `MainActivity`: application shell for browser chrome, a process-wide GeckoRuntime reference, tab/session ownership, navigation, delegates, settings entry points, sharing and fullscreen behavior.
- `BrowserStore`: SharedPreferences persistence for bookmarks, history, validated search-engine preference, open-tab snapshots, permission decisions and BrowseX-owned DownloadManager IDs / filename reservations.
- `BrowserPalette`: shared light/dark/system color tokens and direction helpers for Android screens.
- `BrowseXPermissionDelegate`: separates Android runtime permission requests from Gecko content and media permission decisions.
- `BrowseXNotificationBridge` + `SiteNotificationActionReceiver`: WebNotificationDelegate adapter, Android notification channel, click and dismiss actions.
- `DownloadsPage`, `SiteInfoPage`, `SettingsPage`, `SavedPagesPage`, `ReaderPage`: feature screens; theme colors and RTL are shared through BrowserPalette.
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
7. Persist serializable regular-tab URLs and active index only; never persist GeckoSession objects or private-tab URLs.
8. Private tabs use GeckoSessionSettings.usePrivateMode(true), are excluded from browsing history and startup snapshots, and popup sessions inherit their parent's private mode.
8. Preserve duplicate URLs in tab snapshots: separate tabs may intentionally display the same page.
9. Bound persisted collections, enforce the 20-tab cap, and validate restored web/local-home URLs.
10. After a content-process crash, reopen and reload the affected session once automatically. A repeated crash leaves an explicit recoverable crashed tab; the user can activate it to retry.

## Feature integration contracts and limits

- **Site permissions:** `BrowseXPermissionDelegate` handles Android location/camera/microphone runtime permissions, Gecko content permissions and media-source grants separately. A site is never granted access just because Android permission exists. Content prompts expose Allow, Block and Not now; Site Info can change a stored content decision to Allow, Block or Ask. Camera/microphone decisions are stored per validated HTTP(S) origin. Android-level denial remains controlled by Android Settings. Persistent-storage grants cannot be revoked through GeckoView after they are granted.
- **Website notifications:** Gecko's WebNotificationDelegate is bridged to an Android notification channel. Android 13+ POST_NOTIFICATIONS is requested at runtime; the app-level website-notification switch can disable delivery. Private-browsing notifications are discarded. Tapping an Android notification invokes Gecko's click callback and opens a safe HTTP(S) source when one exists; dismissing invokes the close callback. OS-level notification controls can still disable delivery.
- **Theme, language and accessibility:** shared palette supports System/Light/Dark, Arabic/English/System language, Android locale direction and RTL, plus a matching preferred color scheme and locale for Gecko content. The local home page uses these preferences. Core native screens use the palette and larger touch targets/content descriptions. This does not translate website content, and some less-common browser prompts/toasts may still use English.
- **Downloads:** Gecko external responses are routed to Android DownloadManager after HTTP(S)/host validation and filename sanitization/collision reservation. Filename reservations are synchronized. BrowseX queries only IDs it registered. A failed/null system query is shown as a read error and is not misreported as missing downloads. The screen maps pending/running/paused/successful/failed/unknown states, reports Android pause/failure reasons, shows progress/size/time, opens successful files, cancels active items and removes stale records. Android 8/8.1/9 requires the legacy write-storage runtime permission for public Downloads; Android 10+ uses scoped storage. DownloadManager re-fetches the URL: POST bodies, one-time response URLs, arbitrary authorization headers and exact Gecko request semantics are not guaranteed. Cookies and a default User-Agent are forwarded. Pause/resume/retry is not exposed where DownloadManager cannot safely preserve the original request.
- **Reader:** explicit user action -> GeckoView PageExtractionController -> native reader presentation -> preserve original URL and navigation state. No page scripts are injected. Current GeckoView extraction uses its default options.
- **Settings:** BrowserStore is the persisted source of truth for theme, language, notifications, bookmarks, history and downloads metadata. Android notification permissions can be managed from the app notification settings screen.
- **Responsive UI:** use available width rather than device-brand checks; the existing visual tabs workspace uses a wider layout on tablet widths.

## Compatibility

- Minimum Android API: 26 (Android 8.0).
- Target SDK: 35.
- Java/Kotlin bytecode target: 17.
- ABI packaging includes `armeabi-v7a`, `arm64-v8a`, plus a universal APK.
- GeckoView is a native, relatively large dependency. Verify APK size and ABI contents on every release build.
- Android 10 / API 29 is an explicit compatibility test target. Public Downloads on API 26–28 use a runtime storage permission; API 29+ does not request that legacy permission.
