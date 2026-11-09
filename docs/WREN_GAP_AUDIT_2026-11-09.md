# Wren Browser — Implementation Gap Audit

**Date:** 9 November 2026  
**Branch:** `wren-design-prep`  
**Scope:** adaptive presentation, Glass UI, runtime performance, phone/tablet/foldable/TV readiness, browser workflow completeness and CI health.

This is a source-and-CI audit, not a claim of physical-device certification. Findings below distinguish implemented code from items that still need integration or real-device validation.

## Executive findings

Wren has a useful foundation: a pure adaptive presentation policy, a separate Gecko runtime budget policy, a platform capability probe, JankStats sampling, window-size re-resolution, fold posture support, persisted address-bar preference, material fallbacks, and initial TV remote/focus helpers. The design direction is sound: preserve one Wren identity while adapting layout and effects to the current window, input method, accessibility preferences and runtime pressure.

The largest remaining risk is not the absence of a glass library. It is the gap between policy-level decisions and verified end-to-end behavior on real devices, especially Android 10 / low-memory phones, large-screen multitasking and TV remote journeys.

## Verified in source

- `AdaptivePresentationPolicy` is a pure decision layer; it distinguishes phone, compact landscape, medium, expanded, large, extra-large and TV windows.
- The policy treats live backdrop blur as unavailable unless a concrete rendering path is verified. The current probe deliberately reports no verified blur path, so the current glass effect is tinted/translucent rather than a live backdrop blur.
- Material fallbacks include tinted glass, matte, high contrast and performance-minimal modes.
- Accessibility preferences are inputs to the policy: high contrast, reduced transparency and reduced motion.
- Runtime pressure inputs include battery saver, thermal status, low-memory pressure and measured jank.
- Runtime budgets are separate from presentation decisions; constrained devices use fewer live Gecko sessions and no speculative preload.
- Window size and fold posture influence address-bar placement; TV is detected through Leanback/television configuration.
- MainActivity re-applies adaptive presentation and reparents existing browser rows, preserving their click listeners.
- TV helper code adds safe padding and D-pad focusability/minimum touch targets.
- Current repository locale resources are English and Arabic; broad worldwide localization is not yet complete.

## Corrections made in this audit

1. **Explicit bottom address-bar preference on large windows:** the policy previously converted a user's explicit Bottom choice back to Top on medium/large windows. The policy now honors Bottom on all non-TV window classes; AUTO remains adaptive. A regression test was added.
2. **Robolectric test SDK pinning:** the latest CI run failed in two Robolectric test classes with `UnsupportedOperationException at DefaultSdkProvider.java:170`. Both affected test classes are now pinned to Robolectric SDK 35. This is a targeted compatibility correction; it is not yet considered fixed until a new CI run confirms it.

## Gaps and recommended work

### P0 — Verify build and core runtime before feature expansion

- Re-run the complete CI workflow after the Robolectric SDK pin. Confirm all unit tests, lint, APK builds, Room schema checks and artifact checksums.
- Keep release signing clearly separate from debug builds; no signed production release can be claimed without the required signing configuration.
- Run a clean-install and upgrade-install smoke test on Android 10 / TECNO SPARK 5 Air and at least one modern Android device.
- Test WebView/Gecko startup, process death/recovery, tab restore, downloads, permissions and ad-block behavior on-device. CI cannot establish these behaviors.
- Capture exact failing test XML/log details if the two Robolectric failures persist; do not suppress or skip the tests as a workaround.

### P1 — Adaptive layout and input parity

- Validate every layout breakpoint in portrait, landscape, split-screen and freeform/resizable windows. Use current window bounds, not only physical device class.
- Add tests for address-bar preference transitions at every window class, including fold posture and TV overrides.
- Verify side-rail geometry, RTL placement, insets, IME visibility, gesture navigation and content occlusion. Ensure the page viewport is not hidden behind the floating dock.
- Finish foldable hinge handling: use actual hinge bounds to partition panes, not posture labels alone; test tabletop/book postures and separating hinges on hardware/emulators.
- Define and test keyboard/mouse behavior for tablets and desktop-like windows: focus order, shortcuts, hover, text selection and drag/drop only where supported.
- Ensure all touch controls meet at least 48dp targets and retain equivalent accessible labels, focus order and actions in every material mode.

### P1 — Android TV is a separate interaction contract

- Audit every screen and action with a five-way D-pad: home, address entry, suggestions, menus, tabs, downloads, bookmarks, settings, permission dialogs, error pages and back navigation.
- Verify that focus is always visible, never lost behind a WebView, and moves in a predictable two-dimensional order.
- Verify the TV keyboard/IME, remote Back behavior, pointer remotes, overscan-safe margins and 10-foot text sizes on an actual Android TV device.
- Prepare dedicated, accurate 16:9 TV screenshots and check current Play TV quality criteria before claiming Play Store TV readiness.
- Android TV support does not imply support for Samsung Tizen or LG webOS; those require separate platform clients.

### P1 — Performance policy needs measured calibration

- Current RAM tiers and Gecko session limits are conservative starting values, not benchmark-derived optima. Measure memory, startup, tab switching, scrolling, battery and frame-time on representative constrained/balanced/high-tier devices.
- Test thermal and battery-saver transitions while pages are loading and while the user is interacting; verify hysteresis/debounce so material modes do not flicker.
- Verify JankStats sampling lifecycle, thresholds, rolling window, recovery behavior and that sampling adds negligible overhead.
- Distinguish app process low-memory callbacks from global device memory pressure; test recovery after the app returns to foreground.
- Add a reproducible performance benchmark suite and a local diagnostic screen/export that excludes browsing history, URLs, page content and other sensitive browsing data by default.
- Do not equate low RAM or thermal pressure with permission to weaken privacy/security, block essential browser work, or silently discard tabs.

### P1 — Glass and accessibility quality

- Keep actual blur disabled until a concrete renderer is measured on Android 10/11 and newer devices. A library's existence is not proof that it works safely over GeckoView's rendering surface.
- If a blur renderer is introduced, benchmark GPU/frame time, memory, battery, scrolling and surface composition; feature-gate it and preserve tinted/matte fallback.
- Keep glass only on high-priority browser chrome. Use opaque surfaces for reading-heavy screens, settings, downloads and high-contrast mode.
- Test light/dark mode against busy pages, video, images, high contrast, reduced transparency, reduced motion, large font/display scaling, TalkBack and switch access.
- Check WCAG contrast for text/icons over dynamic page backgrounds and ensure status is never communicated by color alone.
- Avoid hover-only controls and decorative motion that does not work for touch, keyboard or D-pad users.

### P1 — Browser essentials and user trust

- Tab management: verify grid/list, tab search, private/regular separation, recently closed restore, session restore, crash recovery and tab lifecycle under memory pressure. Tab groups/collections should be optional and never forced at tab creation.
- Address/search: verify URL-vs-query parsing, suggestions, clear/paste/submit, loading/progress, offline/error states, keyboard actions and origin visibility.
- Privacy: verify site permission review/revocation, clear browsing data, private-mode boundaries, secure connection indicators, HTTPS behavior and per-site exceptions.
- Ad blocking: verify the actual extension install/enable state, network request blocking on controlled test pages, update/offline/failure behavior, per-site pause/resume and private-mode opt-in. Do not describe tracking protection as equivalent to full ad blocking.
- Downloads: verify queued/downloading/paused/completed/failed/cancelled states, duplicate names, storage denial, process death, notification behavior and opening/sharing files.
- Settings: avoid controls that are decorative or disconnected from persisted state; verify that every visible toggle changes real behavior and survives process restart.

### P2 — Internationalization and polish

- Expand beyond English and Arabic in staged, professionally reviewed locale packs. Do not label the app "all languages" until all visible strings, plurals, date/number formats and error messages are localized.
- Test mixed RTL/LTR content, URLs, numbers, punctuation, selection handles, toolbar ordering, accessibility reading order and TV focus order in Arabic.
- Verify font fallback for scripts not included in the app bundle and large text scaling without clipping.
- Run locale parity and placeholder checks in CI for every added language.

## Suggested acceptance matrix

| Target | Minimum acceptance |
|---|---|
| Android 10, constrained RAM | Stable startup, no live blur, reduced speculative work, essential tabs/downloads/permissions functional |
| Modern Android phone | Smooth scroll and tab transitions, user-selectable top/bottom address bar, adaptive glass only when verified |
| Tablet / split-screen | Correct responsive layout, optional side rail, keyboard/mouse parity, no clipped content |
| Foldable | Correct posture/hinge-aware layout, no controls hidden by hinge, continuity across fold/unfold |
| Android TV | Full D-pad navigation, visible focus, TV keyboard, safe margins, readable 10-foot UI |
| Accessibility | TalkBack, large fonts, high contrast, reduced transparency/motion, non-color status cues |
| RTL | Arabic layout and mixed-direction content correct across all key flows |

## Research references

- Android window blur guidance: https://source.android.com/docs/core/display/window-blurs
- Android adaptive TV guidance: https://developer.android.com/develop/adaptive-apps/guides/tv/build-adaptive-apps-for-tv
- Android TV quality criteria: https://developer.android.google.cn/develop/adaptive-apps/quality-guidelines/tv-app-quality
- Apple Materials / Liquid Glass: https://developer.apple.com/design/human-interface-guidelines/materials
- Apple Liquid Glass adoption and accessibility: https://developer.apple.com/documentation/TechnologyOverviews/adopting-liquid-glass
- Chrome Android address-bar placement: https://blog.google/products-and-platforms/products/chrome/address-bar-position-change/
- Firefox Android tab groups: https://support.mozilla.org/en-US/kb/tab-groups-firefox-android
- Firefox Android tab management: https://support.mozilla.org/en-US/kb/manage-tabs-firefox-android
- Uiverse glass components: https://uiverse.io/elements?search=glass

## Current status

- Source audit completed for the adaptive policy, probe, runtime policy, MainActivity rendering path, TV helpers and targeted tests.
- Explicit bottom preference regression corrected in source and covered by a unit test.
- Robolectric SDK compatibility pin applied to the two failing test classes.
- **CI confirmation pending.** Latest observed run before these corrections failed because 2 of 185 tests failed in Robolectric setup; build and artifact verification steps were skipped.
- No physical-device certification is claimed.
