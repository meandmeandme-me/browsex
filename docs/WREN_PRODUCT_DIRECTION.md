# Wren product direction

Wren is an original Android browser with a blue-wing identity. It is informed by proven browser patterns, not a copy of another browser and not derived from a Figma file.

## Product principles

1. **Fast to the first useful action.** The start page should make search, address entry, favorite sites, recent activity, and a new tab obvious. Keep the address/search field central and suggestions useful without overwhelming the user.
2. **Content first, controls when needed.** Use a compact, reachable browser dock; let the webpage own the screen. Keep back/forward, reload, tabs, bookmarks, share, and page actions close to the address field.
3. **Tabs should stay understandable.** Provide a clear tab switcher, private tabs, recently closed tabs, saved sessions, and eventually named tab groups. Restore gracefully after process death and memory pressure.
4. **Privacy must be visible and actionable.** Explain site permissions, tracking/ad blocking, private browsing, secure connections, and stored data in plain language. Provide per-site controls and a concise privacy status rather than vague shield decoration.
5. **Security should be proactive.** Provide a safety review for saved credentials, risky permissions, notification grants, outdated protection components, and unsafe browsing signals where reliable data is available. Never claim a site is safe without a trustworthy signal.
6. **Personalization without clutter.** Support light/dark themes, accessible contrast, reduced transparency, adjustable text size, language selection, and true RTL layout.
7. **Works across device classes.** Adapt layout to compact phones, large phones, tablets, foldables, keyboards, and TV without device-model allowlists. Respect memory and CPU pressure on entry-level devices.
8. **Account integrations are optional and provider-neutral.** Design a sync boundary so Google, Microsoft, or a Wren account can be added later. Do not couple local browsing data to one identity provider.

## What to learn from established browsers

| Browser | Useful pattern | Wren implementation direction |
| --- | --- | --- |
| Chrome | Fast omnibox, familiar tab workflows, password autofill, safety check, granular site permissions | Keep address/search as the primary action; strengthen permission review and credential safety; make tab actions predictable |
| Safari | Compact content-first toolbar, reader experience, tab groups/profiles, privacy report, start-page customization | Keep the page visually dominant; make reader mode and privacy reporting easy to reach; add named groups and start-page controls |
| Firefox | Tracking protection, extension ecosystem, reader, cross-device sync with explicit data controls | Preserve GeckoView and uBlock integration; keep protection understandable; make future sync opt-in and category-selective |

## Current codebase foundations

The repository already contains GeckoView browser sessions, adaptive device policies, tab lifecycle and persistence, recently closed tabs, saved sessions with import/export, bookmarks and folders, history, downloads, site permission storage, website notifications, reader mode, password vault/autofill, privacy controls, and uBlock-related integration. Preserve these foundations; do not rebuild them as disconnected screens.

## Delivery sequence

### Now: brand and browser shell
- Use the name **Wren** in the visible app identity.
- Use a distinctive blue wing mark, blue-led palette, calm glass surfaces, and clear typography.
- Refine the start page, address/search field, bottom navigation dock, and tab switcher as one coherent mobile browser experience.
- Keep all actions connected to the existing browser controllers and repositories.

### Next: everyday browsing quality
- Complete tab groups and a customizable start page using existing bookmarks/history/session data.
- Improve downloads, reader, page translation entry points, find-in-page, share, and desktop-site controls.
- Add a focused safety review that reports only verifiable findings.

### Later: continuity and extensibility
- Add opt-in sync with per-category controls and end-to-end encryption design review before syncing credentials.
- Add provider-neutral sign-in adapters for Google and Microsoft without making either provider mandatory.
- Add extension management only within GeckoView's supported extension APIs and permission model.

## Non-negotiable quality gates

- No Figma dependency or Figma-derived UI.
- No copying competitor assets, source code, or trademarked visual identity.
- Every visible control must invoke a real action or be clearly disabled.
- Private browsing data must not leak into normal history, suggestions, or autofill.
- RTL, localization, screen readers, large text, and minimum touch targets are first-class.
- Validate on CI and a representative range of real Android devices before claiming device compatibility.

## Research references

- Google Chrome Safety Check and permission review: https://support.google.com/chrome/answer/10468685
- Google Chrome security and privacy overview: https://safety.google/products/chrome/
- Apple Safari privacy, profiles, tab groups, and customization: https://www.apple.com/safari/
- Apple Safari start-page and privacy report controls: https://support.apple.com/en-gb/guide/iphone/iphb3100d149/27/ios/27
- Mozilla Firefox feature set: https://www.firefox.com/en-US/features/all/
- Mozilla Firefox Sync and user-controlled sync categories: https://support.mozilla.org/en-US/kb/sync
