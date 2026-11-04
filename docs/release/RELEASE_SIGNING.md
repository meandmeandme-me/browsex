# BrowseX release signing

## Local release builds

Generate a dedicated release key once and keep the keystore in a secure backup outside the repository:

```bash
keytool -genkeypair -v -keystore browsex-release.jks -alias browsex-release -keyalg RSA -keysize 4096 -validity 10000
```

Do not commit the keystore, passwords, signing properties or private key to Git. Do not reuse a debug key as the production signing identity. Loss of the release key can prevent users from receiving in-place updates.

Set these environment variables in the build environment:

- `BROWSEX_RELEASE_KEYSTORE_PATH`
- `BROWSEX_RELEASE_STORE_PASSWORD`
- `BROWSEX_RELEASE_KEY_ALIAS`
- `BROWSEX_RELEASE_KEY_PASSWORD`

Gradle rejects partial configuration. Without all four values, a local `assembleRelease` is intentionally unsigned and emits a warning.

## GitHub Actions

Configure repository Actions secrets:

- `BROWSEX_RELEASE_KEYSTORE_BASE64` — base64-encoded keystore bytes.
- `BROWSEX_RELEASE_STORE_PASSWORD`
- `BROWSEX_RELEASE_KEY_ALIAS`
- `BROWSEX_RELEASE_KEY_PASSWORD`

The workflow decodes the keystore into the ephemeral runner temp directory, builds release APKs only when all secrets are present, verifies signatures and package/minSdk metadata, and uploads signed artifacts separately from debug artifacts. If all secrets are absent, signed-release steps are skipped. Partial configuration fails the workflow.

Never upload the keystore as an artifact. Restrict who can modify workflow files and access signing secrets. Prefer protected environments and approval gates before publishing a public release.

## Version and update policy

- Increase `versionCode` for every distributable update; never reuse a code already released.
- Keep `applicationId` stable for in-place upgrades.
- Preserve the release signing identity across releases.
- Record ABI, package size, SHA-256 checksums, GeckoView version and release commit.
- Verify upgrades over the previous public APK on representative Android 8/10/13+ devices before publishing.
- Do not publish debug-signed or unsigned APKs as production releases.

## Current status

This establishes the signing mechanism and optional CI path. It does not mean a release key or GitHub secrets have been configured, a signed release has been built, or an APK has been device-tested.
