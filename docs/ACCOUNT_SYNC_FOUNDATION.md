# BrowseX — account and sync foundation

Status: provider-neutral contract plus initial pure domain models. No real provider sign-in, token vault, account UI, backend or cloud synchronization is implemented yet.

Domain models: app/src/main/java/com/browsex/app/AccountDomainModels.kt. They contain no Android, GeckoView, OAuth SDK, database or network types and are intended for later extraction into a shared domain module.

## Product rules
- Local browsing remains fully usable without an account.
- Identity provider authentication, BrowseX profile and sync consent are separate.
- Provider adapters support Google, Microsoft personal/work-school, Apple and standards-based OIDC when actually implemented.
- Provider login never silently enables browser-data sync.
- Each signed-in device has a revocable device registration.

## Boundaries
- IdentityProviderAdapter: official authorization flow and short-lived authorization result.
- AccountIdentity: provider plus stable subject; email/name are optional profile claims, never the identity key.
- AccountSessionRepository: account state and revocation metadata; no raw tokens in SharedPreferences.
- SecureCredentialVault: platform adapter (Android Keystore, Windows Credential Manager/DPAPI, macOS Keychain, Linux Secret Service/libsecret).
- SyncConsentRepository: versioned and timestamped choice per category.
- SyncRepository: transfers versioned encrypted records; browser features never call provider SDKs or backend APIs directly.
- SyncConflictResolver: deterministic merge and user-visible conflict handling.
- DeviceRegistryRepository: lists and revokes signed-in devices.
- AccountDataLifecycle: export, cloud deletion, disconnect and local-data retention are distinct operations.

## Data categories and defaults

| Category | Default | Policy |
|---|---|---|
| Bookmarks and folders | Off | First candidate; stable IDs and merge preview. |
| Reading list / collections | Off | URLs/metadata only; never silently upload page contents. |
| Theme, language and UI preferences | Off | User-controlled. |
| Open tabs / named sessions | Off | Separate opt-in; regular tabs only. |
| Browsing history | Off | Defer until retention/deletion are proven. |
| Passwords / passkeys | Not in first sync release | Separate threat model, autofill and recovery design. |
| Cookies, site grants, form contents | Never sync by default | Device/site security state. |
| Private tabs and private history | Never sync | Excluded before persistence/serialization. |
| Downloaded file bytes | Never sync | Files remain local. |

## Authentication requirements
1. Use OIDC Authorization Code + PKCE; validate issuer, audience, signature, state, nonce and exact redirect URI.
2. Use official provider flows and minimum scopes. Never ship client secrets in an app.
3. Keep access tokens short-lived; refresh credentials only in platform secure vaults with rotation/invalidation.
4. Never log tokens, codes, cookies, private URLs or secrets.
5. Handle account removal, provider revocation, expiry, device loss, clock skew, offline mode and interrupted sign-in.
6. Define account-linking collision and recovery rules before supporting multiple linked identities.

## Sync protocol requirements
- TLS for transport. Decide end-to-end encryption before selecting the backend; do not claim E2EE if the service can decrypt user data.
- Version every record; use stable IDs, revisions, tombstones and idempotency keys.
- Keep a bounded offline queue with exponential backoff and jitter.
- Make sync idempotent across duplicate delivery, partial writes and device clock skew.
- Resolve concurrent bookmark/folder changes deterministically; preserve both or ask when safe merge is impossible.
- Define key creation, device enrollment, recovery, rotation and lost-device revocation before shipping.
- Provide export and cloud deletion; distinguish disconnecting identity, deleting local data and deleting cloud data.

## Device and consent UX
- Show current device, last-seen time and a clear revoke action for other devices.
- Revocation invalidates future sync access; define offline-device behavior on reconnect.
- Each category has its own opt-in, description, last-sync state and pause behavior.
- Pausing sync never erases local data. New categories default off.

## Rollout
1. [x] Define provider-neutral account and sync domain models without platform dependencies.
2. [ ] Define backend API, retention, threat model and encryption/key ownership.
3. [ ] Implement secure platform credential vault and provider authorization adapter.
4. [ ] Implement one verified provider end-to-end; Google is a candidate, not a promise.
5. [ ] Add real account center and provider connection UI; unsupported providers are never shown as active.
6. [ ] Add device registration/revocation and account export/deletion.
7. [ ] Implement encrypted bookmark sync behind explicit opt-in.
8. [ ] Add offline queue, conflict resolution, schema migration and recovery.
9. [ ] Add other providers/categories after security and interoperability tests.
10. [ ] Extract stable domain/sync code into KMP shared modules for desktop and TV.

## First-release non-goals
No forced account/paywall, background history collection, sync of private tabs/cookies/form data/site grants/raw credentials/downloaded file bytes, fake provider buttons or unsupported end-to-end encryption claims.
