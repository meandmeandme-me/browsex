# ADR-004: Backend direction for BrowseX accounts and encrypted sync

- Status: Recommended direction; deployment and provider credentials are not configured.
- Date: 2026-11-04

## Decision summary

Use Supabase (PostgreSQL + Auth + Row Level Security) as the first backend candidate for BrowseX account identity and sync metadata, while keeping the app behind BrowseX-owned repository interfaces and a small HTTP API adapter. Do not bind browser features directly to the Supabase SDK.

This is a reversible infrastructure choice, not a claim that the service is configured or that sign-in works today.

## Why this direction

- Supabase Auth documents support for Google, Azure/Microsoft and Apple identity providers, plus custom OIDC-compatible providers.
- PostgreSQL provides relational constraints and transactional writes suitable for devices, sync metadata, consent and tombstones.
- Row Level Security can enforce per-account ownership at the database boundary.
- Supabase can be self-hosted or managed; deployment and operational ownership still need an explicit decision.
- The community Kotlin Multiplatform SDK lists Android, macOS, Windows and Linux targets, but its own documentation notes platform limitations (including missing built-in OAuth on some desktop targets and no persistent session storage on Linux). Therefore BrowseX should not make that SDK the cross-platform authentication contract.

## Integration boundary

- BrowseX domain uses AccountSyncContracts.kt only.
- An Auth adapter performs OIDC Authorization Code + PKCE in the system browser and handles platform-specific redirects.
- A secure session vault stores only BrowseX/Supabase session credentials using each platform's secure credential facility.
- A transport adapter calls Supabase Auth/PostgREST/Edge Function endpoints using short-lived authenticated requests.
- The database stores opaque encrypted sync payloads plus minimal routing metadata. It must not receive plaintext bookmarks/history if end-to-end encryption is the product promise.
- The client never contains the Supabase service-role key. Only a publishable/anon key may be distributed, protected by strict RLS and server-side authorization.

## Initial schema proposal

- accounts/profile: minimal user-facing profile metadata; provider subject/email claims are not used as sync ownership keys in client payloads.
- devices: account_id, device_id, platform, display_name, created_at, last_seen_at, revoked_at, public_key_version.
- sync_consents: account_id, category, enabled, policy_version, updated_at.
- sync_records: account_id, record_id, category, schema_version, revision, origin_device_id, operation, encrypted_payload, payload_digest, updated_at.
- sync_tombstones: account_id, record_id, category, revision, deleted_at.
- sync_cursors: account_id, device_id, category, cursor, updated_at.

All user tables require RLS. A signed-in user can only read/write rows whose account_id equals the verified auth subject. Device revocation must be enforced server-side, not only by hiding the device in the UI. Use server functions for multi-row operations that need atomicity and authorization checks.

## Encryption decision required before production

The recommended product target is end-to-end encrypted sync for user browsing data:
- Each account has a data-encryption key; device-specific keys wrap or derive access to it.
- The server stores ciphertext and minimal metadata only.
- Device enrollment requires an authenticated key exchange.
- Recovery must be designed before release: user recovery key, trusted-device transfer or another reviewed mechanism.
- Key rotation and device revocation must be specified.
- If BrowseX cannot implement and test this safely in the first release, ship only lower-risk categories with transparent encryption wording; do not claim E2EE.

## Provider setup required outside the repository

- Create OAuth applications for Google, Microsoft/Azure and Apple with verified redirect URIs.
- Configure the provider credentials in the backend's protected settings, never in GitHub or distributed app source.
- Register platform-specific callback URIs and validate state, nonce, issuer, audience, signatures and PKCE verifier.
- Apple requires its own Services ID/key/team configuration for web-style flows on Android and desktop.
- Microsoft needs a decision on personal accounts vs work/school tenants and the supported authority.
- Add provider integrations one at a time, beginning with one provider and security tests.

## Alternatives considered

- Appwrite: supports OAuth2 provider login and multiple linked identities, but its documented SDK/platform matrix is less aligned with the current Kotlin Multiplatform desktop target strategy.
- Self-hosted Keycloak + custom sync API: stronger identity-provider control but adds operational complexity and still requires a separate sync database/API.
- Fully custom auth/backend: maximum control but not appropriate until the product has the operational capacity for secure account recovery, abuse prevention, monitoring, backups and incident response.

## Risks and exit criteria

- Reassess if Supabase pricing, data residency, retention, RLS or provider policy does not meet BrowseX requirements.
- Run a proof of concept for PKCE redirects on Android and desktop before adding production UI.
- Verify RLS with negative cross-account tests and device revocation tests.
- Complete a threat model for ciphertext, metadata leakage, key recovery and account deletion.
- Confirm data export, backup, restore, incident response and service migration procedures.
- Do not add SDK dependencies or provider buttons until the proof of concept passes review.

## Source material checked

- Supabase Auth provider support and RLS model: https://supabase.com/docs/guides/auth
- Supabase Kotlin OAuth flow: https://supabase.com/docs/reference/kotlin/auth-signinwithoauth
- Supabase Kotlin platform support and known limitations: https://github.com/supabase/supabase-kt/blob/master/Auth/README.md
- Supabase Kotlin redirect/PKCE behavior: https://supabase.com/docs/reference/kotlin/initializing
- Appwrite OAuth alternative: https://appwrite.io/docs/products/auth/oauth2
