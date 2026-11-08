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

- `DeviceCapabilityProbe` gathers stable resource facts; `AdaptiveRuntimePolicy` supplies the live Gecko session budget used by the Activity's session lifecycle.
- `AdaptivePresentationPolicy` is a separate pure resolver with unit tests. It classifies the current window, address placement, material mode and optional visual work.
- `AdaptivePresentationProbe` reads current root-window bounds, density, hardware acceleration, touch/D-pad/Leanback/Television features, system high-contrast text where the platform exposes it (API 36+), system animation state, current battery/thermal pressure and `ActivityManager.MemoryInfo.lowMemory`. User contrast/transparency/motion choices are passed as a separate persisted accessibility snapshot.
- `MainActivity` now re-evaluates presentation when the root window changes size, when the Activity resumes, and when power-saver, thermal or memory-pressure callbacks fire.
- AndroidX JankStats tracks frame timing only while the Activity is resumed. A rolling 60-frame sample enters visual-minimal mode at 20% jank and exits it at 10% or less. Only boolean frame outcomes are retained locally; no page, URL or browsing data is collected.
- The live native UI applies tinted-glass, matte, high-contrast and performance-minimal surfaces through the shared `BrowserPalette` and chrome renderer. Phone, tablet, large-window and preliminary Android TV/Google TV layouts are distinct. TV support includes optional Leanback launcher metadata, a 320×180px xhdpi banner, larger controls, overscan-safe margins, initial focus and dedicated start-page styling. Recall/search, site information/permissions, reader, tabs, downloads, bookmarks/history, settings and privacy dialogs now have TV-specific sizing/focus improvements.
- **Live backdrop blur is intentionally not enabled yet.** The Android probe sets `hasVerifiedBackdropBlurPath=false`; the actual chrome is the safe tinted-glass style, which works on Android 8–10. No user warning is shown. A blur path must be implemented and benchmarked for the exact in-window renderer before it can be marked verified.
- Address-bar placement is persisted in DataStore and exposed as Automatic/Top/Bottom in Settings. Compact windows honor TOP or BOTTOM; wider windows use a top/side presentation.
- Wren now persists three explicit accessibility preferences: reduced transparency, increased contrast and reduced motion. The shared native palette makes panel/card/field surfaces opaque in light and dark themes when transparency is reduced or high contrast is enabled; high contrast strengthens ink, muted text, borders and selected states. The browser chrome and Wren start page receive the same preferences. Android system high-contrast text is also observed through the public API on API 36+; older versions use Wren's own setting.
- Sustained memory/jank/thermal/battery pressure selects an opaque performance-minimal native chrome and a CSS minimal-effects mode on Wren's start page. The page reload is restricted to the active internal home page; normal website tabs are not reloaded. This does not inject CSS into arbitrary websites.
- TV layout detection is based on Leanback/Television features or Android TV UI mode; a D-pad connected to a regular phone/tablet does not alone trigger TV mode. The initial Leanback launcher/banner and remote-first controls exist, and key native pages have larger targets/focus. This remains **initial implementation, not certification**: physical/emulator D-pad navigation through every journey, IME handoff, overscan, TalkBack/Switch Access and landscape/4K display QA remain unverified. Fold-hinge posture is not yet probed and remains a concrete next implementation gap.


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

1. Continue extracting runtime/session ownership from MainActivity without weakening current lifecycle safety.
2. Add fold/hinge and connected-display posture probes; the top/bottom address-bar preference is already persisted and exposed.
3. Add a verified, measured in-window blur renderer only if it beats the tinted fallback on representative hardware; keep it disabled otherwise.
4. Complete Android TV D-pad/IME/overscan/core-journey tests and review Leanback distribution requirements; launcher metadata and banner are already present.
5. Validate API 26, Android 10, Android 12+, API 36, 2 GB-class hardware, split-screen, tablet and physical/emulated Android TV behavior. Tizen/webOS remain separate clients.

## Latest verified adaptive/accessibility checkpoint (2026-11-08)

- Branch: `wren-design-prep`
- Verified commit: `062ae581ddb3fa1bf5fb95e934fa6f417cb58220`
- CI run: [#42527954402](https://github.com/meandmeandme-me/browsex/actions/runs/42527954402) — success for locale validation, Kotlin unit tests, Android Lint, Debug APK builds, Room schema verification, APK output/checksum verification and ARM32/ARM64/Universal artifact upload.
- Release signing was skipped because the repository's four `BROWSEX_RELEASE_*` signing secrets are not configured. Debug signing may use a runner-generated key if no stable debug keystore is configured; debug artifacts are for testing, not store release.
- APK artifact checksums are included in each workflow artifact. A passing CI run is not a substitute for physical Android 10, foldable, tablet or TV remote testing.

## Adaptive presentation and Glass UI extension (2026-11-08)

The existing `DeviceCapabilityProbe` and `AdaptiveRuntimePolicy` remain the resource-budget source of truth. Do not overload their RAM tiers or `smallestScreenWidthDp` with all presentation decisions.

A separate, pure `AdaptivePresentationPolicy` has now been added in `AdaptivePresentationPolicy.kt`, with unit tests in `AdaptivePresentationPolicyTest.kt`. It consumes a platform/window/input/accessibility/performance snapshot and resolves:
- A current-window layout class (compact phone, compact landscape, medium, expanded, large, extra-large or TV).
- Address placement (top, bottom, side or TV remote-first).
- A material mode (`GLASS_BLUR`, `GLASS_TINTED`, `MATTE`, `HIGH_CONTRAST` or `PERFORMANCE_MINIMAL`).
- Motion/speculative-visual-work flags and local reason codes.

The policy is wired into the live Activity and Wren chrome through `AdaptivePresentationProbe`. Window bounds trigger responsive repositioning; battery-saver and thermal listeners, low-memory callbacks and a rolling JankStats window trigger visual/performance re-resolution. The renderer currently applies tinted glass or opaque fallback surfaces. **Actual live backdrop blur remains disabled** because the current in-window View path has not been verified or benchmarked; the probe deliberately reports no verified blur path. Accessibility overrides come before blur/performance choices, and unknown data selects a readable fallback. The address-position preference is persisted and exposed as Automatic/Top/Bottom in Settings.

Do not equate Android API >= 31 with guaranteed blur. Cross-window blur availability can change at runtime and may be disabled by GPU constraints, power-saving or system conditions. Use the relevant API/listener only for the specific window-blur feature it controls. It is not a universal backdrop-blur switch. In-window RenderEffect and CSS backdrop-filter are separate rendering paths and must be measured independently.

Maintain these boundaries:
- `AdaptiveRuntimePolicy`: Gecko session/resource budgets and memory/thermal behavior.
- `AdaptivePresentationPolicy`: responsive layout, glass/material tier, and motion tier.
- Design System: renders semantic tokens/material variants; never probes hardware directly.
- Screen components: render state and accept actions; never make ad-hoc API/RAM checks.
- Device/window probes: gather facts; never directly choose colors or layouts.

The same user-visible geometry, semantics, focus order and touch targets must survive changes between blur, tinted glass and matte modes. Do not alert users when a visual effect falls back. Never reduce privacy/security to save GPU or memory.

See [Wren adaptive UX research](WREN_ADAPTIVE_UX_RESEARCH_2026-11-08.md) for browser comparisons, Uiverse guidance, phone/tablet/TV layouts, capability matrix, test gates and source references.

## UI refoundation status

The current Activity now hosts GeckoView directly without rendering the legacy browser
chrome. Existing domain stores, engine delegates, permission bridges, downloads, credential
vault, and tab/session lifecycle remain in place. Old presentation helpers are temporarily
retained as unreachable migration material and must be deleted after their remaining
feature responsibilities have been moved behind presentation-independent interfaces.
