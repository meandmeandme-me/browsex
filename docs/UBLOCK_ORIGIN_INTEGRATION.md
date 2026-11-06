# uBlock Origin integration

## Integration approach

BrowseX uses GeckoView's WebExtension support and installs the official Mozilla Add-ons XPI at the user's request:

`https://addons.mozilla.org/firefox/downloads/latest/ublock-origin/latest.xpi`

The extension is not bundled into the BrowseX APK, modified, or redistributed by this repository. GeckoView validates the downloaded add-on through its extension installation pipeline. BrowseX checks the expected extension ID (`uBlock0@raymondhill.net`) before accepting it.

## User controls

- First installation requires a user action and an explicit acceptance of the extension permission prompt.
- Private browsing is a separate opt-in and is off by default.
- Settings allow enabling, disabling, or removing the extension.
- When uBlock exposes its browser action for the active session, BrowseX displays a toolbar control that opens the actual uBlock popup in a GeckoView popup session.
- The uBlock popup retains uBlock's site-specific power control. If a website refuses to work with blocking enabled, the user can pause uBlock for that site only. BrowseX does not silently disable protection for all sites.

## Adaptive behavior and limits

uBlock Origin maintains its own filter lists, including rules intended to mitigate known anti-adblock scripts and nuisance messages. This is filter-based compatibility, not a universal detector for every custom anti-adblock implementation.

For a site not covered by its filters, the browser exposes uBlock's per-site control. The recommended recovery is to pause blocking on that site only, reload it, and keep protection active elsewhere. BrowseX does not promise that every anti-adblock wall can be bypassed or that every site will work with all filters enabled.

The user may separately report a broken site or update filter lists through uBlock's own interface. BrowseX must not inject scripts into arbitrary pages to defeat site restrictions.

## Interaction with GeckoView protection

GeckoView Safe Browsing remains enabled independently.

When uBlock is enabled for regular sessions, BrowseX disables GeckoView Tracking Protection for those sessions to avoid running two independent content blockers at once. If uBlock is disabled or absent, GeckoView Tracking Protection is restored.

Private sessions continue using GeckoView Tracking Protection unless the user explicitly permits uBlock in private browsing. When private permission is granted, uBlock handles content blocking in that private session.

This policy is selected when each GeckoSession is created. Changing the extension's enabled/private state recreates browser sessions from the persisted regular-tab URLs. Private tab URLs are not persisted.

## Data and privacy notes

- The extension's requested host access is broad because content filtering requires observing and blocking requests across websites.
- The user sees the extension permission prompt before installation.
- BrowseX does not upload browsing history or uBlock filter events to its own service.
- The extension's own filter lists and update behavior are managed by uBlock/Firefox WebExtension mechanisms.
- Private tabs remain excluded from history, session restore and bookmark/session persistence.

## Verification status

CI verifies Kotlin compilation, unit tests, Android lint, APK generation and ABI packaging. CI does not install the XPI from Mozilla Add-ons or verify filtering behavior against live websites.

Physical-device testing remains required for:
1. First-time XPI install and signature validation.
2. Persistence after closing/restarting BrowseX.
3. Toolbar popup, popup sizing and navigation inside the popup.
4. Per-site power button and restoration after reloading.
5. Known anti-adblock pages and normal account/video/payment flows.
6. Private-browsing opt-in and isolation.
7. Offline install failure, extension disable/enable and uninstall.
