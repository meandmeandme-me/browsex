# Wren adaptive-device quality and release QA matrix

Updated: 2026-11-08
Branch: `wren-design-prep`

This matrix turns Android adaptive-app quality guidance into repeatable Wren acceptance criteria. CI passing proves compilation, unit tests, lint and packaged APK outputs only; it does not certify physical devices.

## Required configuration matrix

| Target | Window / device profile | Required checks | Current evidence |
|---|---|---|---|
| Compact phone | 360–430dp wide; portrait | Bottom search, thumb reach, keyboard/IME resize, safe insets, one-hand use | Policy and CI tests; physical-device QA pending |
| Low-memory phone | Android 10, 2GB class (TECNO SPARK 5 Air target) | Startup, 1/5/10 tabs, low-memory release/recovery, download and video, battery saver, ABI install | Constrained policy; physical-device QA pending |
| Compact landscape | About 420×430dp | No clipped address field, short-height controls, IME avoidance | Policy unit test; physical-device QA pending |
| Foldable open | 841×701dp | Window resize, continuity across posture change, tablet layout | WindowManager posture observer; hinge-bound pane layout still pending |
| Foldable tabletop | Half-open horizontal hinge | Web content in upper area, browser chrome below hinge, no focus loss | Posture policy test; emulator/physical QA pending |
| Foldable book | Half-open vertical hinge | Side chrome, content readable without hinge overlap, correct RTL side | Posture policy test; exact hinge safe-area handling pending |
| 8-inch tablet | 1024×640dp | Expanded layout, multi-column tabs, split screen, keyboard and pointer | Responsive policy; device QA pending |
| 10.5-inch tablet | 1280×800dp | Side rail, readable spacing, multi-window resizing | Responsive policy; device QA pending |
| Chromebook / desktop | 1600×900dp, resizable window | Ctrl shortcuts, Tab navigation, mouse hover/right-click, trackpad scroll, drag/drop where available, external monitor | Keyboard shortcuts and tab-card context menu added; device QA pending |
| Android TV | 1280×720 and 1920×1080 | Leanback launch, 320×180px localized banner, every screen navigable by D-pad, focus visible, safe area | Manifest/banner and initial focus path; physical remote QA pending |

## Accessibility acceptance

- Test Android font scaling at maximum 200% on API 34+; do not infer support from using SP alone.
- Test TalkBack on browser chrome, tabs, Settings, Downloads, Saved Pages, dialogs and site-permission controls.
- Keep interactive targets at least 48×48dp unless a documented platform-specific exception applies.
- Verify contrast and visible keyboard/D-pad focus in light, dark, matte and high-contrast modes.
- Verify reduced-transparency selects truly opaque controls and reduced-motion suppresses non-essential animation.
- Check Arabic RTL, mixed URL/email text, punctuation, keyboard navigation order and bidirectional labels.
- Run Accessibility Scanner / automated view checks, then manual TalkBack and Switch Access tests.

## Browser workflow regression

Test these on each relevant form factor: open/search URL, tab creation/switch/close, private tab, restore saved session, bookmarks/folders/import/export, history, file upload/download/cancel/share, camera/microphone/geolocation permissions, website notifications, uBlock install/pause/resume, fullscreen video, WebRTC, popups, reader mode, find-in-page, and process-crash recovery.

## Release gates

1. CI: locale validation, unit tests, lint, APK output checks and checksum verification.
2. Emulator matrix: compact phone, landscape, tablet, foldable tabletop/book, resizable desktop and TV with keyboard/D-pad enabled.
3. Physical devices: Android 10 low-memory phone, at least one recent tablet/Chromebook, one foldable if supported, and one Android TV/Google TV device.
4. Accessibility: TalkBack, 200% font, contrast, reduced transparency/motion.
5. Distribution: release signing, TV launcher/banner review, ABI coverage, install/upgrade/uninstall and Gecko runtime compatibility.

**Do not mark a form factor as certified because the APK compiles or appears in a launcher. Record device model, Android version, window size, input device, test result and known issue for each physical run.**

## Reference guidance

- Android adaptive app quality tiers and compatibility matrix: https://developer.android.com/develop/adaptive-apps/quality-guidelines/adaptive-app-quality
- Android large-screen input compatibility: https://developer.android.com/develop/ui/views/touch-and-input/input-compatibility-on-large-screens
- Android TV app setup and assets: https://developer.android.com/training/tv/get-started/create
- Android TV publishing checklist: https://developer.android.com/training/tv/publishing/checklist
- Android accessibility testing / TalkBack: https://developer.android.com/guide/topics/ui/accessibility/testing
- Android 14+ font scaling to 200%: https://developer.android.com/about/versions/14/features#accessibility
