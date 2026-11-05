# BrowseX Adaptive Runtime Architecture

## Goal

BrowseX should adapt its resource strategy to the capabilities of the device, rather than
assuming every Android phone has the same memory, CPU, display, or platform APIs. The
adaptation must be deterministic, testable, observable, and independent from the future UI.

## Important Android constraint

`minSdk` is a build-time compatibility boundary for an APK; it cannot change dynamically
for each installed phone. The current minimum remains API 26 (Android 8.0) while the
GeckoView/native dependency compatibility is audited. Do not raise it merely to simplify
implementation. Supporting devices below API 26 would require a separately validated
engine/dependency strategy and potentially a distinct build variant, not a runtime toggle.

## Capability pipeline

1. **Probe** — collect platform API, supported ABIs, low-RAM declaration, memory class,
   total physical RAM (when available), density, and smallest screen width.
2. **Classify** — map hardware into constrained, balanced, or high resource tiers.
3. **Resolve policy** — calculate live Gecko session budget, speculative preload policy,
   and parallel resource budget in one pure Kotlin policy.
4. **Apply** — engine, tabs, downloads, image/resource loading, and future UI consume
   the resolved policy through injected configuration. Features must not add scattered
   `Build.VERSION` or RAM checks.
5. **Observe and adapt** — lifecycle memory callbacks can temporarily lower budgets under
   memory pressure. Re-evaluate after configuration changes; do not persist transient
   memory pressure as a permanent device class.
6. **Explain** — diagnostics should report the selected tier and reason codes without
   collecting personal content or sending telemetry by default.

## Current implementation

- `DeviceCapabilityProbe` gathers a device snapshot using APIs available from minSdk 26.
- `AdaptiveRuntimePolicy` is pure and covered by unit tests.
- The policy is deliberately not yet wired into MainActivity's session lifecycle. Wire it
  only after the host/runtime responsibilities are extracted, so the UI reset does not
  entangle device policy with Activity code.

## Initial policy defaults

| Tier | Live Gecko sessions | Parallel resource budget | Speculative tab preload |
|---|---:|---:|---|
| Constrained | 1 | 1 | Off |
| Balanced | 2 | 2 | Off |
| High | 4 | 4 | On |

These are conservative starting points, not claims of benchmark-optimal values. Validate
with GeckoView memory measurements and representative low/mid/high-end devices before
changing defaults. The device screen class is exposed separately for future responsive UI.

## Safety and compatibility rules

- Never identify device capability from a marketing model name.
- Treat missing/unknown hardware data conservatively.
- Check API availability before calling newer Android APIs.
- A missing optional capability disables only the dependent feature; it must not prevent
  browser startup.
- Do not silently reduce privacy/security protections on low-end devices.
- Do not unload or delete user data to meet memory budgets.
- Do not preload tabs or run speculative work on constrained devices.
- Keep capability policy pure and unit-testable.
- Keep diagnostics local and privacy-preserving unless the user explicitly opts into sharing.

## Next integration sequence

1. Extract browser runtime/session ownership from MainActivity.
2. Inject the capability snapshot and resolved policy into the runtime/session lifecycle.
3. Replace the fixed live-session limit with the resolved budget.
4. Add memory-pressure overrides via existing lifecycle callbacks.
5. Add device-matrix tests for API 26, 29, 33, and current Android; test low-memory behavior
   on a 2 GB class device.
6. Only then remove the old visual shell and keep a minimal host entry point for engine
   verification; the future Uizard UI will be added as a separate presentation layer.

## UI refoundation status

The current Activity now hosts GeckoView directly without rendering the legacy browser
chrome. Existing domain stores, engine delegates, permission bridges, downloads, credential
vault, and tab/session lifecycle remain in place. Old presentation helpers are temporarily
retained as unreachable migration material and must be deleted after their remaining
feature responsibilities have been moved behind presentation-independent interfaces.
