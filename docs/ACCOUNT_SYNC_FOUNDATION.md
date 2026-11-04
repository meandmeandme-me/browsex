# BrowseX — account and sync foundation

Status: design contract only. No provider sign-in, account UI, token storage or cloud synchronization is implemented.

## Product rules

- Local browsing works without an account and remains the default.
- Identity provider authentication is separate from BrowseX profile data and from sync consent.
- Support providers through adapters, not provider-specific types in browser features.
- Initial providers to evaluate: Google, Microsoft (personal and work/school where appropriate), Apple, and later OIDC-compatible providers.
- A provider login must never silently enable browser-data sync.

## Target boundaries

- `IdentityProviderAdapter`: starts a provider-approved authorization flow and returns a short-lived authorization result.
- `AccountIdentity`: provider, stable subject identifier, display name/email only when the provider grants the relevant scope.
- `AccountSessionRepository`: persists account state and revocation metadata; never stores raw access/refresh tokens in SharedPreferences.
- `SecureCredentialVault`: Android Keystore-backed encryption for secrets, with rotation and invalidation behavior documented.
- `SyncConsent`: explicit booleans for each data category, with timestamps and a versioned consent record.
- `SyncRepository`: uploads/downloads versioned encrypted records; browser features do not call provider SDKs or backend APIs directly.
- `SyncConflictResolver`: deterministic merge rules and user-visible conflict behavior for concurrent edits.
- `DeviceRegistry`: list and revoke signed-in devices; revocation invalidates credentials and stops sync.

## Data categories

| Category | Default | Notes |
|---|---|---|
| Bookmarks and folders | Off | User can opt in; merge/replace and duplicate rules must be visible. |
| Reading list / collections | Off | Sync metadata and URLs only; avoid silently uploading page contents. |
| Theme, language and UI preferences | Off | Low sensitivity but still user-controlled. |
| Open tabs / named sessions | Off | Exclude private tabs; allow per-device opt-in and bounded history. |
| Browsing history | Off | Highly sensitive; separate consent, retention and delete controls. |
| Passwords / passkeys | Not in first sync release | Requires a dedicated threat model, secure autofill integration and recovery design. |
| Cookies, website permissions, form contents | Never sync by default | These are device/site security state, not ordinary browser preferences. |
| Private tabs and private history | Never sync | Must never enter durable sync queues or telemetry. |

## Required security and lifecycle behavior

1. Use OAuth 2.0 Authorization Code with PKCE / OIDC where supported; validate issuer, audience, state, nonce and redirect URI.
2. Request minimum scopes and explain why each scope is needed.
3. Keep provider access tokens short-lived; store refresh credentials only in an encrypted vault protected by Android Keystore.
4. Never log tokens, authorization codes, cookies, URLs containing credentials or private browsing state.
5. Handle account removal, provider revocation, token expiry, device loss, clock skew, offline mode and interrupted sign-in.
6. Encrypt sync payloads in transit and at rest; document who controls encryption keys and whether the service operator can decrypt data.
7. Provide export and deletion of cloud data; local sign-out must clearly distinguish “remove account” from “delete local data.”
8. Make sync idempotent, versioned, bounded and resilient to duplicate delivery and partial network failures.
9. Resolve concurrent bookmark/folder edits deterministically; never silently overwrite a newer user change.
10. Keep private mode outside repositories and sync serializers at the type boundary, not merely by filtering at the UI.

## Rollout sequence

1. Define account/profile models and provider adapter contracts.
2. Implement one provider with official SDK/OIDC flow, PKCE and security review.
3. Add account state UI with explicit sign-in/out and local-data retention choices.
4. Implement encrypted sync for bookmarks only, behind a separate opt-in.
5. Add settings and tab/session sync only after conflict handling and device revocation are proven.
6. Consider history sync only after privacy review; password/passkey sync is a separate product project.

## Explicit non-goals for the first release

- No forced account creation.
- No account-based paywall for local browsing.
- No background collection of browsing history.
- No syncing private tabs, cookies, form data, site grants or raw browser credentials.
- No provider-branded sign-in button that does not complete a verified real flow.
