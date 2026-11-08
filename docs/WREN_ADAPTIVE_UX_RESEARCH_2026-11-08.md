# Wren Browser — Research: browser UX, Glass UI, adaptive performance, phones, tablets and TV

**Research date:** 8 November 2026  
**Target branch:** `wren-design-prep`  
**Status:** product/design and architecture decision input — not a claim that every item is implemented.

## Executive direction

Wren should not force one layout or one visual effect onto every device. It should keep one recognizable product identity and one coherent interaction model, then adapt the presentation to the current window, input method, accessibility settings, platform capabilities and measured performance.

Three rules guide the work:

1. **Same identity, different expression.** Wren bird mark, typography, semantic colors, spacing and interaction language remain recognizable. Phone, tablet, foldable, connected display and TV do not need identical chrome.
2. **Glass is progressive enhancement.** The interface must remain attractive and legible when actual blur is unavailable, disabled, too expensive or disallowed by accessibility preferences. Never show a warning banner just because blur is not active.
3. **Adapt to the current environment, not the marketing model name.** Use current window bounds, screen posture, input capabilities, supported platform APIs and runtime pressure. A tablet in split screen may have a phone-sized window; a phone attached to an external display may have a desktop-sized window.

## 1. Research scope and limits

This review compares product patterns from Chrome, Safari, Firefox, Vivaldi, Opera, Brave, DuckDuckGo and Samsung Internet; examines Uiverse glass components; and uses official Android TV, adaptive-layout, runtime-blur, accessibility and performance documentation.

This is desk research, not a statistically representative worldwide user study. Community discussions and product patterns identify hypotheses to test; they do not prove that every user wants the same toolbar position or feature set. Validate key choices with task-based usability sessions on real devices.

## 1A. User behavior: evidence and product hypotheses

### One-handed use and thumb reach

A controlled study of 20 right-handed participants found that thumb performance changed with phone size, movement direction and orientation; performance was generally better on smaller devices and for some outward/adduction-abduction movements. A separate 104-participant study found that icon size and position affected one-handed tapping time, with vertical distance and smaller targets influencing performance.

**What this means for Wren:** bottom placement is useful as a default hypothesis for common phone tasks, not a universal rule. Put the highest-frequency actions within a comfortable lower reach area, use generous touch targets, and offer a top placement option. Do not assume every user holds the phone in the right hand or uses one hand. Evaluate left/right-handed use, large phones, accessibility input and landscape separately.

### Tab overload and the fear of losing pages

A 2026 CHI paper reviewing tab-management research reports that 50.7% of participants in one cited survey complained about tab overload; another cited study found browser clutter/tab overload serious for 24.8% of its participants. These are separate research samples, not worldwide prevalence rates. The paper also describes people keeping tabs as task reminders or because they are uncertain whether a page will matter later. Forcing people to name and organize every new page immediately can interrupt browsing; a field study of a lightweight task-centric approach found that users often made use of saving/grouping but rarely used added scheduling, prioritization and note-taking features.

**What this means for Wren:**
- Make tab search, recently closed, quick restore and close/reopen predictable.
- Offer groups/collections and saved sessions, but do not force a group decision on every new tab.
- Let people keep a page temporarily, then organize when its relevance becomes clear.
- Make private tabs unmistakably separate and exclude them from normal history/restore surfaces.
- Keep automatic tab cleanup opt-in or carefully explained; never silently discard user intent.

### Trust, predictability and cognitive load

Browser users switch repeatedly between the page and browser controls. Every state change should be visible: loading, secure/insecure connection, permission granted/blocked, download progress and ad-block status. Do not move controls unexpectedly as pages load. Prefer progressive disclosure: show common actions directly, less common actions in a menu, and advanced settings only when needed. Avoid turning the main toolbar into a dashboard of badges, counters and promotional controls.

### Wren usability-test plan

Test representative tasks rather than asking whether the interface simply "looks nice":
1. Open a new tab, search for a topic and return to the previous page.
2. Find a page among 10–20 tabs without closing useful tabs.
3. Save a page and retrieve it later without remembering its exact title.
4. Allow or block a website permission and understand how to change it later.
5. Download a file, identify its state and recover from a failure.
6. Switch between normal/private browsing without confusing the two.
7. Use the same core journeys with one hand, both hands, TalkBack, keyboard/mouse and TV remote.
8. Repeat after switching from full glass to tinted/matte mode; users should not notice any loss of functionality.

Measure task completion, time, mis-taps, backtracking, abandonment, recall errors and perceived clarity. Recruit varied ages, hand sizes, left/right-handed users, accessibility users, low-end devices and tablet/TV users. Do not infer satisfaction solely from raw tap counts.

## 2. Browser comparison: what Wren should learn

| Browser | Observed pattern / strength | Wren decision |
|---|---|---|
| **Chrome for Android** | User may choose top or bottom address-bar position. Chrome also supports tab groups. | Make bottom search a strong phone default, but keep a top option. Preserve familiarity and keep the layout preference explicit. |
| **Safari on iPhone/iPad** | Compact, bottom and top tab layouts; tab groups; floating controls layered above page content in the Liquid Glass design direction. | Keep controls in a distinct navigation layer. Allow content to scroll beneath it without reducing text contrast. |
| **Firefox for Android** | Tab tray supports grid/list, regular/private tabs, tab groups, synced tabs and inactive tabs. | Add tab search and sensible organization before adding ornamental tab animations. Keep inactive tabs recoverable and explain when memory policy releases sessions. |
| **Vivaldi Android** | Optional bottom address/tab bars, swipeable tab bar, tab stacks, panels for bookmarks/history/notes/downloads; tablet/landscape layout moves controls to the top. | One of the strongest examples for a responsive browser shell. Do not keep the phone dock at the bottom in every posture. |
| **Opera Android** | Different phone and tablet layouts; phone menu at the bottom and tablet menu at the top; includes ad blocking and start-page customization. | Treat form factor as a layout decision, not merely scaling. Keep menu entry points predictable. |
| **Brave** | Shields combine ad/tracker blocking, fingerprinting protection, HTTPS upgrades and per-site controls. | Privacy must be a real subsystem with exceptions and honest status—not a decorative shield icon or an unverified toggle. |
| **DuckDuckGo** | Privacy protection is a central onboarding/product promise. | Make privacy explanations understandable and brief; keep protections visible but do not overwhelm ordinary browsing. |
| **Samsung Internet** | Bottom toolbar, customizable menu (up to seven toolbar features), tab navigation gestures and a bottom address-bar option documented for supported mobile/tablet versions. | Offer a small, user-controlled toolbar customization surface after stable defaults; do not front-load dozens of settings. |

### Product decisions

- Phone portrait: bottom address/search dock by default, because one-handed reach is useful and familiar. Provide a top-bar alternative in Settings.
- Phone landscape: use a compact single-row or top/side chrome; do not reserve a large bottom dock that consumes the small vertical viewport.
- Tablets: use a larger-window layout with room for tabs, panels and split panes. A permanently floating phone dock is not the only correct tablet design.
- Tab switching: support both grid and list, tab search, private/regular separation, recently closed restoration and eventually tab groups. Add drag-reorder only after touch, mouse and keyboard behavior are specified.
- Search: support URL/query recognition, clear, explicit paste, keyboard submit, loading/progress and error states. Never read or upload clipboard contents automatically.
- Privacy: show site origin and permissions; make ad/tracker protection state truthful and per-site exceptions discoverable.

Sources: Chrome address-bar placement, Firefox tab management, Vivaldi interface/panels, Opera Android layout, Brave features, DuckDuckGo Android.

## 3. Uiverse and component-library policy

Uiverse is a useful design-research and prototyping source. Its glass collection currently lists 97+ glassmorphism UI elements. The broader library presents community-built elements in CSS, Tailwind, React and Figma, and states that its UI elements are published under MIT.

Use Uiverse to inspect:
- Glass search fields and address bars.
- Buttons, icon buttons and segmented controls.
- Compact cards, tab cards and floating action surfaces.
- Toggles, checkboxes, radio rows and permission controls.
- Loaders, progress bars, snackbars and focus/pressed states.
- Background patterns, gradients, borders and subtle highlight treatments.

**Important implementation distinction:** Uiverse is not an Android-native component library. Do not put a web page or CSS component inside the native GeckoView chrome just to obtain its appearance. Use the code as a visual reference, inspect the exact element, retain required author/license attribution, then rebuild it as a native Android View/drawable or an appropriate shared design primitive. Keep a reuse ledger containing the element URL, creator, exact license, date reviewed, modifications and attribution.

Suggested shortlist for a Wren component study:
1. Address/search field and clear/paste controls.
2. Floating glass dock and navigation buttons.
3. Tab card and selected/focused states.
4. Permission prompt and confirmation sheet.
5. Download row, progress and empty/error states.
6. Toggle, segmented selector and settings row.
7. Loading indicators and reduced-motion alternatives.

Avoid importing dozens of unrelated hover/3D animations. Android is primarily touch and remote-driven; hover-only interactions do not translate to phones or TVs.

## 4. Glass UI design principles

Apple's Liquid Glass guidance is useful as a design reference, not as a requirement to copy its implementation. It places glass primarily in the functional/navigation layer, above content, and warns against overusing glass in the content layer. It also accounts for reduced transparency, increased contrast and reduced motion.

### Wren glass recipe

- A translucent tinted fill, chosen separately for light and dark surfaces.
- One subtle border to separate the surface from page content.
- A restrained top-edge highlight or gradient; avoid constant animated shine.
- Soft shadow/elevation used sparingly, not multiple stacked glow shadows.
- Rounded shapes that follow one spacing/radius scale.
- Text and icons with adequate contrast over both dark and light page content.
- A consistent opaque fallback that retains the same geometry, spacing, hierarchy and color identity.

### Where glass belongs

Use it sparingly for:
- Address/search dock.
- Main browser navigation controls.
- Temporary menu/sheet surfaces where the underlying page remains visible.
- A small set of high-priority floating controls.

Prefer normal opaque/solid surfaces for:
- Reading content, long settings lists and text-heavy dialogs.
- Download/status rows where contrast and scanning matter more than translucency.
- High-contrast accessibility mode.
- Performance-constrained conditions.

Never stack glass surfaces on glass surfaces. Avoid a clear transparent surface over busy imagery unless a readability/dimming strategy is present.

### Why Wren does not force a live blur library onto GeckoView

A candidate such as **Dimezis/BlurView** is worth studying: it uses a `BlurTarget` snapshot and has API-specific rendering paths (RenderEffect on newer Android, OpenGL on Android 10/11, and RenderScript on older versions). Its published project license is Apache-2.0. However, this does **not** mean it can blur every browser surface transparently.

GeckoView's default `BACKEND_SURFACE_VIEW` is designed for better rendering performance. Mozilla documents that SurfaceView is outside normal View compositing; Android can composite normal sibling overlays above it, but a third-party View snapshot/blur should not be assumed to include the browser page. GeckoView offers `BACKEND_TEXTURE_VIEW`, but Mozilla explicitly describes it as slower and more memory-intensive. Android's TextureView documentation also lists capability trade-offs compared with SurfaceView, including HDR/DRM differences.

Android window blur APIs are a different mechanism: they blur a window background or the content behind a separate window. They are not a general-purpose way to blur an arbitrary rectangle of content inside Wren's full-screen Activity, and system availability can change at runtime.

**Decision for Wren:** keep the current GeckoView SurfaceView path and use translucent/tinted glass with contrast-safe surfaces. Do not add BlurView or switch the engine to TextureView just to claim a frosted-glass appearance. Reconsider only after a separate prototype proves all of the following on real devices:
- The blur target actually contains the Gecko page on the intended rendering backend.
- Video, protected media/DRM, fullscreen playback, keyboard and popup windows remain correct.
- Memory use and frame times remain within budget on high-end hardware.
- Android 10/11 fallback and low-memory mode remain smooth and readable.
- The feature can be disabled instantly when system/runtime availability or JankStats indicates pressure.

This keeps "Glass UI" a visual design language that works on all supported hardware; live blur remains a high-end optional enhancement rather than a core requirement.

## 5. Runtime capability detection: Adaptive Presentation Engine

The existing `DeviceCapabilityProbe` and `AdaptiveRuntimePolicy` already capture some stable memory/ABI/screen facts and battery/thermal pressure. Keep them as the resource-policy foundation, but do not mix their RAM tiers with UI layout decisions. Extend the architecture with a separate, testable `AdaptivePresentationPolicy`.

### Inputs — keep separate

**A. Platform capabilities**
- Android API level.
- Whether the required API exists.
- Whether the exact blur mechanism is currently enabled.
- Whether hardware acceleration is enabled for the relevant window/view.
- Touchscreen, pointer/mouse, physical keyboard, remote/D-pad and gamepad capabilities.
- Android TV / Leanback feature availability.
- Optional fold/hinge information from AndroidX WindowManager.

**B. Current window and posture**
- Current activity/window width and height in dp, density and insets.
- Current window size class, not a hard-coded phone/tablet label.
- Orientation and compact-height status.
- Fold/hinge orientation and occlusion.
- Whether the app is on a connected/external display.
- Whether the window was resized, moved, placed in split-screen or desktop windowing.

**C. Runtime resource pressure**
- Existing low-RAM and app memory-class profile.
- Current battery-saver mode.
- Thermal status where available.
- Memory-pressure callbacks and observed rendering jank.
- A conservative graphics tier established through tested device classes and runtime telemetry collected locally in diagnostics builds—not by running expensive benchmarks during ordinary browsing.

**D. User/accessibility preferences**
- Light/dark/system theme.
- Reduced motion.
- Increased contrast and reduced transparency, where system settings can be read reliably.
- User's optional top/bottom address-bar preference.
- TV remote/navigation preferences.

Do not collapse these inputs into one vague "device supports glass" boolean.

### Separate the capability questions

1. **Platform support:** Is the API available on this Android version?
2. **Runtime enablement:** Is the exact effect enabled right now? Android cross-window blur can be unavailable due to GPU limits or disabled dynamically by power-saving or other system conditions.
3. **Performance suitability:** Does this device/window remain within the Wren frame-time and memory budgets when the effect is used?
4. **Accessibility suitability:** Has the user requested reduced transparency, increased contrast or reduced motion?
5. **User preference:** Did the user choose glass, reduced effects, or an alternative appearance?

An API being available does not guarantee that a specific blur is enabled or cheap. Cross-window blur APIs such as `WindowManager.isCrossWindowBlurEnabled()` are specifically for window/background blur; they are not a universal switch for arbitrary in-window backdrop blur. Android `RenderEffect` and CSS `backdrop-filter` are distinct mechanisms with their own rendering costs and compatibility. Avoid treating them as interchangeable.

### Resolve a material mode

Suggested output enum:

- `GLASS_BLUR`: a verified blur path may be used on a small number of high-value navigation surfaces.
- `GLASS_TINTED`: translucent color, border, highlight and shadow; no live blur. This is the default graceful fallback.
- `MATTE`: mostly opaque surfaces with the same Wren design language.
- `HIGH_CONTRAST`: opaque and stronger edge/text contrast, honoring accessibility settings.
- `PERFORMANCE_MINIMAL`: no live blur, little/no non-essential animation, minimal shadows and less GPU-heavy decoration.

Recommended resolution order:
1. Accessibility override wins.
2. Explicit user preference wins unless a safety/accessibility setting requires otherwise.
3. Platform/API and runtime blur availability must be verified.
4. Performance suitability and current battery/thermal pressure may downgrade the effect.
5. Missing/unknown data selects the safe `GLASS_TINTED` or `MATTE` fallback, never a crash or a broken transparent control.

### User-facing behavior

- Switch modes seamlessly without a warning dialog or "your phone does not support glass" message.
- Preserve identical control positions, labels, touch targets, focus order and semantics across modes.
- Never lower font size, text contrast or navigation discoverability to achieve a glass look.
- Never disable privacy, safe browsing, permission checks or security features to improve performance.
- Do not run a continuous stress benchmark or capture screenshots of page content to produce blur.
- Keep transient device pressure transient; do not permanently downgrade a capable phone because it overheated once.

### Adapt during use

Probe stable capabilities at startup. Observe configuration/window changes and relevant dynamic system state. On Android 12+ where cross-window blur is used, register the documented blur-enabled listener and update the material mode when the OS changes availability. Use lifecycle memory callbacks to reduce session/resource budgets. Sample rendering performance in profiling/diagnostic builds or with lightweight instrumentation; do not run a busy frame loop in production.

The existing `AdaptiveRuntimePolicy` handles Gecko session/resource budgets. `AdaptivePresentationPolicy` should handle UI mode/layout/motion. They share input signals but return independent configs and are covered by pure unit tests.

## 6. Layout system by form factor

Use Android window size classes as the primary layout input. Official breakpoints:
- Compact width: under 600dp.
- Medium width: 600dp to under 840dp.
- Expanded width: 840dp to under 1200dp.
- Large width: 1200dp to under 1600dp.
- Extra-large width: 1600dp and above.

Also consider height classes; phone landscape often has compact height. Do not assume the physical device type determines the available window size.

| Current environment | Wren layout |
|---|---|
| **Phone portrait / compact window** | Bottom address/search dock with one-handed actions; content gets most of the viewport; toolbar can collapse while scrolling, but remains easy to summon. |
| **Phone landscape / compact height** | Compact one-row chrome at top or edge; hide non-essential controls until requested; do not cover video or page content with a tall dock. |
| **Tablet medium portrait** | Address/navigation at top or a compact floating edge dock; generous tab grid; library as a sheet or second pane where it fits. |
| **Tablet expanded / landscape** | Desktop-inspired browser shell: top address bar, horizontal tab strip or optional vertical tab rail, optional bookmarks/history/downloads panel, larger content area. |
| **Foldable** | Recompute layout when opened/closed; use hinge/fold features to avoid placing search field, buttons or text across a separating hinge. |
| **Split-screen / freeform window** | Respond to current bounds; preserve state while resizing; don't classify by device model. |
| **Phone/tablet on external display** | Use actual window metrics and connected peripherals; provide mouse/keyboard/shortcut behavior where present. |
| **Android TV / Google TV** | Dedicated remote-first layout; big readable targets, D-pad navigation and highly visible focus. No phone bottom dock or tiny touch icons. |

### TV is a distinct interaction mode

Android TV is a 10-foot experience: viewers are farther away and usually use a remote, not a touchscreen. A TV layout needs:
- A prominent search/address entry point and a remote-friendly keyboard.
- Large favorites and recent sites.
- Clear focus indication for every actionable item.
- Predictable D-pad left/right/up/down navigation and Back behavior.
- No hover-only, swipe-only, long-press-only or touch-only critical actions.
- TV-safe text sizes, margins and overscan awareness.
- Privacy design appropriate for a shared family display; hide sensitive recent content when the user requests it.

For Android TV distribution, the app needs the proper Leanback feature declaration, touchscreen-not-required declaration, TV launcher intent and TV banner assets; the entire core path must work with a D-pad. Do not add TV launcher support until the TV layout and focus behavior are actually implemented and tested.

**Scope honesty:** the current Kotlin/GeckoView application can target Android phones/tablets and, after a dedicated implementation, Android TV/Google TV. Samsung TVs use Tizen, and LG TVs use webOS; an Android APK does not become installable on those platforms by resizing its UI. Supporting those platforms requires separate platform clients, packaging, engine/media testing and store submissions. Shared product rules and design tokens can be reused, but Android code is not a universal smart-TV binary.

## 7. Performance and quality guardrails

### Design budgets to validate

- Avoid multiple simultaneous full-screen blur layers.
- Avoid blurring the full webpage behind continuously animated controls.
- Keep toolbar surfaces simple and cache static assets appropriately.
- Avoid large full-resolution thumbnails in the tab tray; generate/retain thumbnails at display size and release them when not needed.
- Disable speculative work in constrained/battery-saver/thermal conditions.
- Maintain browser functionality when all visual effects are off.
- Measure scrolling, tab switch, address-field open/close, keyboard show/hide, page load and tab tray on low/mid/high tiers.

### Instrumentation and tooling

- `JankStats` (`androidx.metrics:metrics-performance`) to report UI jank with UI-state context.
- Macrobenchmark and Baseline Profiles for repeatable startup, scroll and navigation benchmarks.
- Android Studio Profiler, GPU rendering profile, Perfetto and frame metrics for diagnosing layout/draw/upload/GPU bottlenecks.
- Real-device matrix; emulator results do not replace actual low-end GPU/thermal testing.
- A small local diagnostics panel may show current window class, material mode, memory tier, pressure state and reason codes. Keep it developer-only or user-invoked; do not upload browsing content.

### Required performance tiers

- Constrained: low-RAM devices, small memory class, persistent jank or thermal pressure; tinted/matte glass, minimal animation, no speculative tab work, conservative sessions.
- Balanced: translucent tinted glass with limited optional effects; moderate session/resource budgets.
- High: may use verified blur on a few surfaces and richer transitions if measured frame/memory budgets remain healthy.
- Accessibility mode: overrides all tiers and preserves clarity, contrast and reduced motion.

Thresholds must be tuned from measured frame time and memory behavior, not chosen based on marketing model lists.

## 8. Implementation sequence

1. Keep the current browser engine and product features stable while research becomes a versioned design contract.
2. Add `WindowPresentationSnapshot` based on current window metrics/configuration, separated from stable `DeviceCapabilityProfile`.
3. Add input-modality and TV/Leanback capability probes.
4. Add `AdaptivePresentationPolicy` as a pure, unit-tested resolver with reason codes.
5. Add a shared Wren Material/Design System that implements `GLASS_BLUR`, `GLASS_TINTED`, `MATTE` and `HIGH_CONTRAST` without changing screen behavior.
6. Wire platform blur availability listeners and accessibility preference handling.
7. Introduce JankStats/benchmark scenarios and tune visual quality tiers on representative hardware.
8. Add responsive tablet/foldable/connected-display navigation.
9. A preliminary Android TV/Google TV mode, Leanback launcher metadata, banner and remote-readable start page are now present. Complete D-pad/focus/IME/overscan testing before claiming TV support or store certification.
10. Evaluate Tizen and webOS as separate future clients only when product, engine and distribution budgets are approved.

## 8A. Current implementation checkpoint

- Latest checked commit: `0c4d34ce6e82bd095b57e4d39082713fc7c68fd3`; CI run [#42525156452](https://github.com/meandmeandme-me/browsex/actions/runs/42525156452) completed successfully for locale validation, tests, lint, debug APK build, schema/checksum verification and ARM32/ARM64/Universal artifact upload. Release signing was skipped because release secrets are not configured.

**Implemented in the working branch (not yet device-certified):**
- `AdaptivePresentationPolicy.kt` resolves current-window layout, address placement, material mode, motion policy and reason codes. Unit tests cover phone/tablet/TV decisions, accessibility overrides, battery saver, system low memory, constrained RAM, thermal state, measured jank and the requirement that API version alone never enables blur.
- `AdaptivePresentationProbe.kt` reads current Activity root dimensions and actual touch/D-pad/Leanback features, RAM tier, battery/thermal pressure, system low-memory state, system animation preference and API 36+ high-contrast text where the public API exists.
- `MainActivity` applies the resolved presentation to the live Wren toolbar: compact phone uses bottom chrome; compact landscape and medium/expanded windows use top chrome; large/extra-large windows use a side rail; detected TV/remote environments use a larger remote-oriented top dock.
- Power-saver and thermal changes, Activity resume, root-window resize and memory-pressure callbacks cause policy re-evaluation.
- AndroidX JankStats observes window-frame jank while the Activity is resumed. Wren keeps a rolling 60-frame boolean sample locally; sustained jank (at least 20%) selects performance-minimal surfaces; recovery to 10% or less allows the normal presentation to return. No URL, page content or browsing history is collected for this.
- The fallback surfaces are applied automatically without a user-facing warning. Matte/performance surfaces are fully opaque; tinted-glass surfaces keep the same dimensions and actions.

**Limitations that remain:**
- The current probe deliberately sets `hasVerifiedBackdropBlurPath=false`. There is no verified in-window live backdrop-blur implementation yet, so even newer/high-end devices currently use tinted glass rather than live blur. This avoids confusing Android cross-window blur support with in-window toolbar blur and avoids claiming a GPU path that has not been measured.
- Address-bar placement is saved in DataStore and exposed as Automatic/Top/Bottom in Settings. The Bottom preference applies on compact windows; medium/expanded windows use a top/side layout to preserve usable content space.
- Android's public high-contrast-text API is only used on API 36+. Reduced-transparency is not automatically detectable through one consistent public Android API and is not wired to a Wren setting yet.
- TV layout detection uses Leanback/Television features or Android TV UI mode; a connected D-pad alone does not turn a regular phone/tablet into TV mode. Android TV mode now includes optional Leanback launcher metadata, a banner, larger remote-first chrome, a remote-readable start page, adaptive tab grid and enlarged settings/library/downloads controls. Physical/emulator D-pad certification, IME handoff, overscan and complete TV journeys remain outstanding. Tizen and webOS need separate clients; they cannot install the Android APK.
- Fold-hinge posture and multi-window edge-case QA remain to be added. The layout does respond to current Activity window bounds, including ordinary resizing.

The latest CI run is used to validate compilation/tests/lint/build. A passing CI run is not physical-device verification; test on Android 10/low-memory hardware, tablets, landscape and Android TV before claiming release readiness.

## 9. Acceptance checklist

- [ ] No API newer than minSdk is called without a correct guard or AndroidX compatibility path.
- [ ] Android 8–10 can use Wren with no live blur and no degraded readability.
- [ ] Android 12+ blur mode follows real runtime availability changes.
- [ ] Battery saver, thermal stress and low-memory mode downgrade only visual/optional workload, never security.
- [ ] Accessibility contrast/reduced-transparency/reduced-motion overrides are tested.
- [ ] Layout uses current window bounds in portrait, landscape, split-screen, freeform and external display.
- [ ] The phone's bottom dock never overlaps the focused input or system keyboard.
- [ ] Tablets do not receive a stretched phone layout.
- [ ] TV remote can complete all core journeys with visible focus and predictable D-pad movement.
- [ ] No hover, swipe or touch-only interaction blocks TV task completion.
- [ ] Jank, startup, memory and tab-switch budgets are benchmarked on representative real devices.
- [ ] When an effect is unavailable, the user sees a coherent Wren fallback with no warning interruption.
- [ ] Product claims distinguish Android TV support from Tizen/webOS support.

## 10. Sources

### Human factors and user behavior
- One-handed thumb performance study (2012, 20 participants): https://pubmed.ncbi.nlm.nih.gov/22409102/
- Icon size/position and one-handed tapping study (2022, 104 participants): https://pubmed.ncbi.nlm.nih.gov/36161546/
- CHI 2026 research on tab overload and page management: https://doi.org/10.1145/3772318.3791979

### Browser UX
- Chrome Android address bar placement: https://support.google.com/chrome/answer/14181646
- Chrome Android tab groups: https://support.google.com/chrome/answer/2391819
- Safari tab groups: https://support.apple.com/en-au/guide/iphone/iph3028ebf68/ios
- Firefox Android tab management: https://support.mozilla.org/en-US/kb/manage-tabs-firefox-android
- Vivaldi Android interface: https://help.vivaldi.com/android/android-appearance/android-vivaldi-mobile-interface/
- Vivaldi Android tab stacks: https://help.vivaldi.com/android/android-browse/android-tab-stacks/
- Opera Android FAQ: https://help.opera.com/en/mobile/android/
- Brave feature overview: https://support.brave.app/hc/en-us/articles/15872271230221-What-are-all-the-features-Brave-Browser-offers
- DuckDuckGo Android browser: https://duckduckgo.com/duckduckgo-help-pages/mobile/android
- Samsung Internet Android overview: https://developer.samsung.com/browser/android/overview.html
- Samsung toolbar customization: https://www.samsung.com/us/apps/samsung-browser/
- Samsung bottom address-bar availability notes: https://hptotosamsung.com/africa_en/apps/samsung-browser/

### Browser rendering and blur implementation constraints
- Dimezis BlurView (API-specific blur backends, BlurTarget setup and Apache-2.0 license): https://github.com/Dimezis/BlurView
- GeckoView rendering backends (SurfaceView default vs TextureView trade-off): https://mozilla.github.io/geckoview/javadoc/mozilla-central/org/mozilla/geckoview/GeckoView.html
- GeckoView architecture and SurfaceView compositing notes: https://firefox-source-docs.mozilla.org/mobile/android/geckoview/contributor/geckoview-architecture.html
- Android SurfaceView sibling compositing and alpha-blend cost: https://developer.android.com/reference/android/view/SurfaceView
- Android TextureView vs SurfaceView trade-offs: https://developer.android.com/reference/android/view/TextureView
- Android window blur is a cross-window feature and can be disabled dynamically: https://source.android.com/docs/core/display/window-blurs

### Glass and components
- Uiverse glass components: https://uiverse.io/ui/glassmorphism-ui
- Uiverse library and formats: https://uiverse.io/
- Apple Liquid Glass WWDC25: https://developer.apple.com/videos/play/wwdc2025/219/
- Apple Materials HIG: https://developer.apple.com/design/human-interface-guidelines/materials
- Android WindowManager blur availability: https://developer.android.com/reference/android/view/WindowManager
- Android RenderEffect: https://developer.android.com/reference/android/graphics/RenderEffect
- MDN backdrop-filter compatibility: https://developer.mozilla.org/en-US/docs/Web/CSS/Reference/Properties/backdrop-filter

### Adaptive devices, TV and performance
- Android window size classes: https://developer.android.com/develop/adaptive-apps/guides/use-window-size-classes
- Android foldables/tablets guidance: https://android-developers.googleblog.com/2023/06/detecting-if-device-is-foldable-tablet.html
- Android connected displays: https://developer.android.com/develop/adaptive-apps/guides/support-connected-displays
- Android TV design: https://developer.android.com/design/ui/tv/guides/foundations/design-for-tv
- Android TV focus system: https://developer.android.com/design/ui/tv/guides/styles/focus-system
- Android TV app requirements: https://developer.android.com/training/tv/get-started/create
- JankStats: https://developer.android.com/reference/androidx/metrics/performance/JankStats
- GPU rendering profiling: https://developer.android.com/topic/performance/rendering/profile-gpu
- Measuring performance/janky frames: https://developer.android.com/topic/performance/measuring-performance

### Other TV platforms
- Samsung Tizen web applications: https://developer.samsung.com/smarttv/develop
- LG webOS TV developer portal: https://webostv.developer.lge.com/
