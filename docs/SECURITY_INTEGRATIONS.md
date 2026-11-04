# BrowseX security integrations

## Phishing and malicious-site protection

The browser runtime enables GeckoView Content Blocking Safe Browsing with the engine's default unsafe-site categories. This is engine-level protection, not a hand-maintained URL list. Coverage and freshness depend on GeckoView's provider configuration, network access, and device/runtime; do not claim every phishing URL is detected. Tracking protection remains separate and is not a full ad blocker.

## Password storage and autofill

A password manager must use GeckoView's Autocomplete storage delegate and login prompt callbacks, not scrape password fields with injected JavaScript. Credentials must be encrypted with an Android Keystore-backed key, scoped to exact HTTPS origins, excluded from private browsing, and offered only after explicit user confirmation when multiple accounts exist. Do not enable login autofill or claim saved-password support until that storage and prompt lifecycle is implemented and tested.

## Android permissions

Runtime permission requests are scoped to the active GeckoSession and serialized through BrowserAndroidPermissionQueue. Activity Result launchers remain Activity-owned; the queue owns ordering, cancellation, stale-session rejection, and exactly-once callback completion.
