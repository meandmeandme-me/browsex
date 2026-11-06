# Falak — Google/Microsoft sign-in and encrypted sync implementation plan

Status: architecture, provider-neutral domain contracts, PKCE/OIDC request primitives and a backend decision record exist. No provider login, token exchange, ID-token verification, secure account-session vault, backend deployment, device registration or cloud sync is live.

## Product behavior
- Browsing, bookmarks, downloads and local sessions remain usable without signing in.
- Google and Microsoft are identity choices, not a requirement to create a BrowseX account before browsing.
- Signing in does not enable synchronization. Each sync category requires a separate explicit opt-in.
- The account identity key is the provider's stable subject/issuer pair, never the email address.
- Disconnecting a provider, signing out of BrowseX, pausing sync, deleting cloud data and clearing local data are separate actions.
- A provider button is shown as available only after its full authorization and account lifecycle path is configured and verified.

## Provider rollout
### Google — first candidate
- Use the official Google OpenID Connect Authorization Code + PKCE flow in the system browser.
- Configure an Android OAuth client and exact app redirect URI; never embed a client secret.
- Request only openid, profile and email initially; do not request Drive or broad Google API access for browser sync.
- Validate issuer, audience, signature against trusted JWKS, expiry, issued-at, nonce and exact redirect; reject replayed state/code.
- Test account cancellation, no-network, expired/revoked provider session, wrong audience, wrong nonce, clock skew and repeated callback.

### Microsoft — second candidate
- Use Microsoft identity platform Authorization Code + PKCE with the official system-browser flow.
- Decide and document authority policy for personal Microsoft accounts and work/school tenants; do not silently use a tenant-only authority.
- Validate issuer/tenant, audience, signature, nonce, expiry and exact redirect using trusted OpenID metadata/JWKS.
- Test personal accounts, work/school accounts, tenant restrictions, consent denial, Conditional Access/MFA, revoked sessions and account switching.
- Use only minimal OIDC scopes until a separate Microsoft API feature is approved.

### Account lifecycle
- Store short-lived access/refresh credentials only in an Android Keystore-backed secure vault with rotation and invalidation.
- Persist non-secret account display state separately from credentials.
- Support sign-out, disconnect identity, provider revocation, reauthentication, device loss and local account removal.
- Add account export and cloud deletion before production launch.
- Never log authorization codes, tokens, ID tokens, cookies, browsing URLs or private data.

## Sync rollout
1. Backend proof of concept: validate the recommended Supabase Auth/PostgreSQL/RLS direction behind BrowseX-owned interfaces. No service-role key in the app.
2. Device enrollment: register a stable device ID, platform, display name and last-seen metadata; support server-enforced device revocation.
3. Cryptographic design: define account data key, per-device wrapping, enrollment, recovery, rotation, lost-device handling and metadata leakage. Do not claim end-to-end encryption until the server cannot decrypt payloads and tests prove it.
4. First category: bookmarks and folders only, off by default. Stable IDs, versioned encrypted payloads, tombstones, idempotency, bounded offline queue and conflict preview.
5. Next categories: reading list and preferences after bookmark sync is stable.
6. Named sessions/open tabs: separate opt-in; regular tabs only. Private tabs are never serialized or synced.
7. History: defer until retention, privacy, deletion and conflict semantics are reviewed.
8. Credentials/passkeys: excluded from the first sync release and require a separate threat model and recovery protocol.

## Required production components
- Google and Microsoft OIDC adapters with trusted metadata/JWKS validation.
- One-use authorization transaction store with state, nonce, PKCE verifier, exact redirect and expiry.
- Token exchange and ID-token verifier; PKCE request creation alone is not authentication.
- Android Keystore credential vault; no plaintext tokens in Room, DataStore, SharedPreferences, logs or backups.
- Account/session repository, account UI, provider disconnect and reauthentication.
- Backend schema and strict RLS tests, device registry and revocation.
- Sync consent store, encrypted record repository, transport, cipher/key management, offline worker and deterministic conflict resolver.
- Export, cloud deletion, account deletion, pause/resume and per-category controls.
- Security tests for cross-account access, token replay, redirect hijack, invalid signatures/issuer/audience/nonce, revoked devices, replayed sync operations and key recovery.

## Definition of done
- Google and Microsoft can each complete real sign-in on supported Android devices, with verified OIDC claims and secure credential storage.
- Local browsing works after provider sign-out and while offline.
- No category sync occurs without explicit consent.
- A bookmark created on one enrolled device appears on another only after encrypted sync succeeds.
- Concurrent edits and deletions resolve predictably.
- Device revocation, account export, cloud deletion and account disconnect are tested.
- Private tabs, cookies, site permissions, form content, download bytes and raw credentials never enter the sync payload.
- CI security/unit tests pass, and device tests cover cancellation, MFA/Conditional Access, app restart, network interruption and token refresh.

## Current blockers outside the code repository
- Google OAuth client registration and verified redirect configuration.
- Microsoft app registration and supported account/tenant policy.
- Supabase project/deployment, protected provider credentials, RLS policies and operational ownership.
- Reviewed end-to-end encryption and key recovery design.
- A production privacy policy, retention/deletion policy and incident response owner.

Do not generate placeholder client IDs, secrets, provider buttons or fake sync status. Keep these integrations unavailable until the real configuration and end-to-end verification exist.
