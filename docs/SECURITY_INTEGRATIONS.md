# BrowseX security integrations

## Phishing and malicious-site protection

The browser runtime enables GeckoView Content Blocking Safe Browsing with the engine's default unsafe-site categories. This is engine-level protection, not a hand-maintained URL list. Coverage and freshness depend on GeckoView's provider configuration, network access, and device/runtime; do not claim every phishing URL is detected. Tracking protection remains separate and is not a full ad blocker.

## Saved passwords and in-browser autofill

BrowseX uses GeckoView's native Autocomplete storage delegate and login prompts; it does not scrape forms with injected JavaScript. Stored credentials are encrypted with AES-GCM using a non-exportable Android Keystore key and kept in app-private preferences. Only HTTPS origins are accepted, form-action origins must match, and lookups require an exact origin match. Autofill is deliberately not automatic: the user must choose a saved sign-in in the GeckoView prompt. Saving requires explicit confirmation. Private tabs reject save and selection prompts. The manager UI allows the user to review, copy, and delete saved entries.

This is an in-browser password manager, not an Android-wide AutofillService or passkey provider. Before saved credentials are filled, revealed, or copied, BrowseX requires AndroidX BiometricPrompt verification using a supported device biometric or device credential. The AES-GCM key itself is not authentication-bound, so this does not protect against a compromised app process or a rooted device. Credentials are not synced or exported. This is not system-wide AutofillService or cross-device sync.

## Android permissions

Gecko media/location permission requests are scoped to the active GeckoSession and coordinated by BrowserAndroidPermissionCoordinator over BrowserAndroidPermissionQueue. Android notification permission requests use BrowserNotificationPermissionCoordinator, which coalesces concurrent callbacks and handles pre-Android-13 behavior separately. Activity Result launchers remain Activity-owned; coordinators own ordering, cancellation, stale-session rejection, and callback completion.
