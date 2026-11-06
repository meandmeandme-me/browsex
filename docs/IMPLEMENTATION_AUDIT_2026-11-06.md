# Wren Browser — Implementation Audit and Release Gates

Audit date: 2026-11-06
Branch reviewed: `wren-design-prep`
Scope: account/sync, Room/DataStore cutover, locale coverage, content protection, and the supplied debug APK.

This is a source inspection report, not a claim that the app passed a build or device test.

## Verified current state

### 1. Google and Microsoft accounts

- Provider-neutral account models and sync contracts exist.
- PKCE/OIDC request primitives exist according to the implementation plan.
- No real provider adapter, token exchange, trusted JWKS signature verification, secure provider-token session, backend deployment, or device enrollment is present.
- The current plan correctly requires Google and Microsoft registrations and real redirect configuration. Do not show a provider as connected or enable sync until the full flow is verified.

**Remaining:** implement and test Google OIDC first; then Microsoft OIDC with explicit personal/work-school tenant policy. Store tokens only in a Keystore-backed vault. No client secret in the APK.

### 2. Sync

- Domain contracts exist, but a production sync transport and backend are not live.
- No sync must run by default.
- Private tabs, cookies, permissions, form contents, download bytes, browsing history, and password-vault data must remain excluded from the initial sync scope.
- Bookmarks/folders are the safest first opt-in sync category after backend isolation and encryption design are verified.

**External prerequisites:** provision and secure the backend, configure provider applications, define encryption/key recovery and deletion policy, and test row-level isolation. Never embed a backend service-role key in the Android app.

### 3. Room and DataStore migration — release blocker

Source inspection found:
- Room repositories and a Room database graph exist.
- DataStore preferences exist.
- `BrowserStorageRuntime` currently documents a clean-start path and does not invoke a legacy SharedPreferences import.
- `BrowserStore` and its focused SharedPreferences stores still exist.
- MainActivity uses the new storage runtime for settings and several repositories while still constructing the legacy BrowserStore facade.

This means the production cutover and preservation of existing installations must not be assumed complete.

**Required before cutover:**
1. Inventory every key and payload still read from SharedPreferences.
2. Add a one-time, versioned, idempotent importer into Room/DataStore.
3. Run import transactionally; preserve source preferences until verification succeeds.
4. Verify bookmark folders, history, tab/session snapshots, recently closed tabs, downloads, site permissions, theme, language, ad-block settings, and home preferences.
5. Test process death, interrupted import, duplicate retry, rollback, corrupt legacy JSON, and upgrade from the actual prior APK.
6. Remove legacy writes only after upgrade tests pass on Android 10 and newer.

Never clear old preferences merely because a migration marker exists; verify imported row counts and a stable content digest first.

### 4. Languages

- English and Arabic resource packs exist and are registered in Android locale configuration.
- The app language setting accepts BCP-47 tags, but unsupported locales fall back to English.
- The roadmap still marks reviewed locale packs, secondary prompts/toasts, plural/date/number formatting, mixed-direction rendering, and accessibility verification as incomplete.

**Required:** audit every user-visible string, compare resource keys and format placeholders, translate the full surface, and test Arabic RTL mixed with URLs, numbers, filenames, and English brand names. Add reviewed locale packs incrementally; do not machine-label unreviewed translations as complete.

### 5. Ad blocking and privacy — high-priority verification

Source inspection found:
- A uBlock Origin GeckoView WebExtension manager exists.
- It downloads the extension from the official Mozilla Add-ons endpoint only after the user starts installation and grants consent.
- Startup discovers an installed extension and reconciles its actual enabled/private-browsing state.
- The browser has a separate Gecko tracking-protection fallback policy, but tracking protection is not equivalent to a full ad blocker.
- The supplied APK contains Gecko assets but no bundled `.xpi` extension. Therefore the extension must be obtained at runtime; an offline first launch cannot install it.

**Required functional verification:**
1. Fresh install, online first launch, accept install prompt, wait for completion, restart app.
2. Confirm installed extension ID is exactly `uBlock0@raymondhill.net`, enabled state is true, and browser UI reports the real state.
3. Load controlled ad/tracker test pages and verify network requests/elements are blocked; do not rely only on the toolbar icon.
4. Verify per-site pause/resume and recovery after navigation/restart.
5. Test private browsing only after explicit opt-in.
6. Test AMO offline, timeout, interrupted install, failed update, extension missing/disabled, and app restart.
7. Verify Gecko tracking protection stays enabled when uBlock is absent/disabled or extension discovery fails.
8. Test login, banking, streaming, shopping, and anti-adblock sites; provide per-site exceptions rather than disabling all protection.
9. Add automated policy tests and documented device test results.

Do not report “ads blocked” merely because uBlock is installed or enabled. Record the test page, observed blocked request/element, extension version, GeckoView version, and device.

## Supplied APK inspection

- File received: `app-universal-debug.apk`
- Size: approximately 248 MiB.
- APK ZIP contains `assets/home.html` and GeckoView's `assets/omni.ja`; no XPI asset was found.
- This confirms that uBlock is not bundled in this APK. It does not prove whether the extension was successfully installed later at runtime on the user's device.
- This environment did not run the APK on a physical Android device, and no successful install/runtime ad-block test is claimed.

## Release gates

Do not mark the requested work complete until all of these are true:

- [ ] Google OIDC end-to-end tests pass with verified issuer, audience, signature, expiry, nonce, state, PKCE and redirect.
- [ ] Microsoft OIDC end-to-end tests pass for the declared account/tenant policy.
- [ ] Secure token storage, sign-out, revocation, reauthentication and account deletion are tested.
- [ ] Sync is opt-in per category; backend isolation, encryption, conflict handling, offline queue, device revocation, export and deletion are tested.
- [ ] Legacy SharedPreferences data is imported into Room/DataStore without loss on upgrade.
- [ ] All supported locale resources are complete and validated; Arabic RTL and accessibility are device-tested.
- [ ] uBlock installation and real blocking are verified on-device, including failure/offline/private-mode cases.
- [ ] Gecko tracking protection remains active as a fallback.
- [ ] JVM tests, Android Lint, APK build/verification, and Android 10+ physical-device smoke tests pass.
- [ ] Release signing and upgrade compatibility are verified.

## Safe implementation order

1. Protect existing data: finish and test Room/DataStore migration first.
2. Fix and prove uBlock installation and real blocking on device.
3. Complete English/Arabic resource coverage and reviewed locale packs.
4. Implement Google OIDC; then Microsoft OIDC.
5. Implement opt-in encrypted bookmark sync only after backend/security gates.
6. Run full CI and Android 10+ device QA; publish only a verified artifact.
