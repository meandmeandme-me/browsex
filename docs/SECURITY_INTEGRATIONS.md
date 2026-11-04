# BrowseX security integrations

## Phishing and malicious-site protection

The browser runtime enables GeckoView Content Blocking Safe Browsing with the engine's default unsafe-site categories. This is engine-level protection, not a hand-maintained URL list. Coverage and freshness depend on GeckoView's provider configuration, network access, and device/runtime; do not claim every phishing URL is detected. Tracking protection remains separate and is not a full ad blocker.

## Saved passwords and in-browser autofill

BrowseX uses GeckoView's native Autocomplete storage delegate and login prompts; it does not scrape forms with injected JavaScript. Stored credentials are encrypted with AES-GCM using a non-exportable Android Keystore key and kept in app-private preferences. Only HTTPS origins are accepted, form-action origins must match, and lookups require an exact origin match. Autofill is deliberately not automatic: the user must choose a saved sign-in in the GeckoView prompt. Saving requires explicit confirmation. Private tabs reject save and selection prompts. The manager UI allows the user to review, copy, and delete saved entries.

This is an in-browser password manager, not an Android-wide AutofillService or passkey provider. The Keystore key is not biometric-auth-bound in this implementation; anyone with access to the already-unlocked app may access saved entries. Credentials are not synced or exported. Do not claim biometric protection, system-wide autofill, or cross-device sync.

## Android permissions

Runtime permission requests are scoped to the active GeckoSession and serialized through BrowserAndroidPermissionQueue. Activity Result launchers remain Activity-owned; the queue owns ordering, cancellation, stale-session rejection, and exactly-once callback completion.
