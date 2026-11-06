# BrowseX account and sync — UI/flow specification for Uizard

Status: design specification only. Do not present any provider as connected until its real authorization integration ships.

## Entry points
- Settings > Account.
- A restrained sync indicator in the account area; never block browsing with sign-in prompts.
- First-run education is dismissible and does not ask for an account.

## Screen A — Account center (signed out)
- Explain benefits: keep selected bookmarks/settings available on other BrowseX devices.
- State clearly: BrowseX works without an account.
- Provider connection choices are rendered only for integrations enabled in the build.
- Include privacy summary and link to sync explanation.
- Empty state: no account connected; no scary warning or forced CTA.

## Screen B — Account center (signed in)
- Provider identity, optional display name/email and connection status.
- BrowseX account/device identity separated from the provider identity.
- Actions: Sync settings, Devices, Export data, Disconnect account.
- Disconnect flow explicitly asks whether local data should remain.

## Screen C — Connect provider
- Provider-branded authorization action only when a verified OIDC/official flow exists.
- Explain minimum requested profile scopes before redirect.
- Loading, browser handoff, cancellation, denied consent, offline, invalid state/nonce, expired session and provider-revoked states.
- Never ask for or collect provider passwords inside BrowseX.

## Screen D — Sync overview
- Sync paused/on state, last successful sync and readable current error.
- Pause/resume control; pausing does not delete local or cloud data.
- Per-category rows with status and last sync.
- Explain that account sign-in alone does not enable sync.

## Screen E — Category consent
Each category has an independent default-off switch, plain-language explanation, sample data and sensitivity note:
- Bookmarks and folders.
- Reading list/collections.
- Browser preferences.
- Named sessions.
- Open tabs.
- Browsing history (clearly marked sensitive and unavailable until its retention policy is approved).
Private browsing, cookies, site permissions, form contents and credentials are not selectable categories.

## Screen F — Devices
- Current device is visually distinct.
- Other devices show platform, user-defined label if available, last-seen time and revoke action.
- Revoke requires confirmation and explains that the device loses future sync access when online.
- Empty state for one-device accounts.

## Screen G — Conflict resolution
- Show the two changed versions with timestamps and device labels.
- Actions: keep this version, keep both (when supported), or cancel.
- Explain automatic merge when conflict can be resolved deterministically.
- Never silently discard a user's bookmark/folder edit.

## Screen H — Privacy and account data
- Export BrowseX cloud data.
- Delete cloud data.
- Disconnect provider identity.
- Sign out of this device.
- Local-data retention choice, with distinct wording for clearing local data.
- Destructive actions require confirmation and progress/error/retry states.

## Required interaction states
Signed out, signing in, user cancelled, consent denied, provider unavailable, offline, signed in but sync off, sync paused, syncing, synced, partial failure, token expired, provider revoked, device revoked, conflict pending, export ready, cloud deletion pending/completed/failed.

## Responsive and accessibility
- Phone: single-column cards, bottom sheets only for short actions, reachable primary controls.
- Tablet/desktop: adaptive two-pane settings layout where useful.
- TV: D-pad focus order, large focus ring, no hover-only controls, explicit confirmation and no dense multi-column consent switches.
- RTL/LTR: mirror directional layout while preserving provider logos and URL/email direction.
- Screen reader labels must include category, enabled state and consequences; support large text, contrast and reduced motion.

## Non-negotiable design rule
No decorative fake sync progress, fake connected status, fake devices or non-functional provider buttons. Every displayed state must come from a real account/sync implementation.
