# Wren Browser — Figma Design Brief

## Mission

Design a complete, production-quality Android browser experience for **Wren Browser**. This is a full product UI system, not a landing-page concept. Every visible control must have a defined purpose, state, destination, and interaction. Use the existing implementation and feature inventory below; do not invent fake working features.

## Design direction

- Premium Wren browser UI inspired by familiar Chrome and Safari usage patterns, with refined glass surfaces used selectively—not excessive blur or low-contrast text.
- Distinctive visual identity, polished spacing, typography, iconography, subtle motion, and clear hierarchy.
- Adaptive across compact phones, large phones, foldables, tablets, split-screen/connected displays and a dedicated Android TV/Google TV remote-first experience; support portrait and landscape.
- Light, dark, and system themes.
- Complete Arabic RTL and English LTR layouts. Mirror directional navigation where appropriate, but do not mirror brand marks, media controls, or universal icons.
- Accessible contrast, scalable text, screen-reader labels, large touch targets, keyboard/focus support, and reduced-motion alternatives.
- Design reusable components, variants, tokens, and responsive rules. Avoid one-off styling.



### Approved Wren phone navigation (2026-11-08)
- Place the address/search field in the lower glass dock by default on phones.
- Use a two-row dock: address/search plus site identity/reload; then back, forward, home, bookmark, tab count and overflow menu. Show extension actions only when the extension is truly available.
- Retain a quiet brand/site identity header at the top and let page content occupy the center.
- Use Safari-like material restraint and Chrome-like address/navigation conventions as interaction references; create original Wren components and artwork rather than copying proprietary assets or implementation.
- Provide a contrast-safe translucent fallback on Android versions without supported backdrop blur (minSdk 26).

## Required deliverable in Figma

Create a structured multi-page Figma file with:
1. Foundations: color tokens, typography, spacing, radii, elevation, blur, icon rules, grids, breakpoints, motion, accessibility.
2. Component library: buttons, icon buttons, address/search field, tab cards, bottom navigation, top bars, sheets, dialogs, menus, switches, checkboxes, radio rows, permission prompts, snackbars, progress, empty/error/loading states.
3. Complete screen designs and interactive prototype flows for every screen listed below.
4. Light and dark variants, plus Arabic RTL counterparts for every major screen.
5. Phone, tablet, foldable, connected-display and landscape adaptations; a separate Android TV/Google TV remote-first flow with D-pad focus. Tizen/webOS are separate future clients, not Android APK layouts.
6. A clickable prototype connecting every primary action and meaningful secondary action.
7. A component/state inventory documenting default, pressed, focused, disabled, loading, success, empty, and error states where relevant.

## Global browser shell

Design the browser chrome consistently:
- Home/start page using the Wren bird identity.
- Bottom-anchored address and search bar on phones, with URL/query detection, secure/insecure indicator, clear, paste, and loading/progress state. Keep clear/paste controls only where implemented.
- Back, forward, reload/stop, home, tabs count, menu, and primary action placement.
- Page loading, offline, network error, unsupported scheme, certificate/security warning, and page unavailable states.
- Context menus for links, images, selected text, and page actions.
- Bottom toolbar behavior and tablet/landscape placement.

## Screen inventory

### 1. Home
- Search/address entry.
- Customizable shortcut grid: add, edit, reorder, remove.
- Optional sections and clear empty states.
- Recent browsing/continue browsing surfaces only where data exists.
- Menu entry points for tabs, bookmarks, history, downloads, settings, privacy, and saved sessions.

### 2. Tabs workspace
- Visual tab grid/list, active tab, tab count and 20-tab capacity state.
- New tab, switch, close, close others, private tab, and restore recently closed.
- Saved sessions: create, rename, open, delete.
- Private mode visual distinction and clear privacy explanation.
- Empty state and memory/session recovery states.

### 3. Navigation and page actions
- Address/search suggestions and search-provider selection.
- Page menu and context menus.
- Find in page, share, copy link, open in new tab, bookmark, reader mode, reload/stop, desktop-site toggle only if implemented.
- Site information panel: origin, connection/security status, permissions and per-site controls.

### 4. Bookmarks and library
- Saved pages list, folders, create/rename/delete folder, move item, search, sort, empty state.
- History timeline, search, individual deletion, clear-history confirmation.
- Recall unified search across normal tabs, bookmarks, and history; private tabs must never appear.
- Import/export entry points only as clearly marked unavailable/planned if not implemented.

### 5. Downloads
- Active, completed, failed, cancelled sections or filters.
- File name, source, progress, size, status, and relevant actions.
- Pause/resume only if supported; otherwise do not draw active controls.
- Cancel, open, remove from list, delete file where supported.
- Permission denied, storage unavailable, duplicate filename, unsupported response, interrupted, and retry states.
- Explain limitations instead of implying unsupported resume behavior.

### 6. Privacy and security
- Privacy dashboard and clear browsing data.
- Selective clearing: history, cookies/site data, cache, permissions, authentication sessions.
- Per-site permissions: ask/allow/block, Android permission denied, permanently denied, not now, and revoke/change flows.
- Safe Browsing state and security warning.
- uBlock Origin setup, enable/disable, per-site exception, private-mode opt-in, unavailable/offline/error states.
- Never imply that privacy controls or ad blocking are active when disabled.

### 7. Website notifications and permissions
- Website notification request prompt: allow, block, not now.
- Android notification permission explanation and system-denied state.
- Site notification settings and notification inbox/empty state if inbox is supported.
- Camera, microphone, and location prompts with clear origin labels.
- Distinguish website consent from Android OS permission.

### 8. Reader mode
- Article reading view, typography controls, theme, spacing, exit, loading, and unavailable state.

### 9. Password manager
- Vault locked/unlocked, authentication prompt, saved logins list, search, details, copy/reveal with re-authentication, fill/select account, add/edit/delete only where implemented.
- Clear warning that Android Autofill integration is optional and fill-only.
- Never show real credentials in mockups.

### 10. Settings
Organize into clear groups:
- Appearance: theme, language, layout, home customization.
- Search: default search engine.
- Tabs and browsing.
- Privacy and security.
- Site permissions and notifications.
- Downloads.
- Password manager and Autofill.
- Accessibility.
- About, version, open-source notices, diagnostics.
- Future account/sync settings must be marked as planned/unavailable until implemented.

### 11. Accounts and sync — future-ready concept only
Create a separate clearly labeled concept flow, not a fake enabled feature:
- Account center, provider selection (Google/Microsoft), consent by data category, sync status, connected devices, revoke device, conflict resolution, export/delete account data.
- Show disabled/planned states until backend, provider verification, secure token storage, encryption, and sync are actually shipped.
- No password-vault cloud sync by default; require separate security design and explicit opt-in.



### Adaptive material and device capability contract
- Create variants for Glass Blur, Tinted Glass (no live blur), Matte, High Contrast and Performance Minimal. Keep geometry, spacing and content hierarchy consistent between variants.
- Show a capability matrix and fallback behavior; never design a warning pop-up when blur is unavailable.
- Device/window classification must be based on current window width/height, posture and input modalities—not device model names.
- TV layouts must expose visible focus for every action and complete primary tasks using only a D-pad/remote and optional keyboard.
- Provide system/user overrides for reduced motion, contrast and transparency.

## Interaction contract

For every button, icon button, menu item, card, row, switch, and field, document:
- Name and purpose.
- Tap/click result and destination.
- Enabled/disabled conditions.
- Confirmation needed for destructive actions.
- Loading, success, empty, offline, permission-denied, and failure feedback.
- Whether it is implemented now or future/planned.

No decorative dead buttons. No fabricated feature states. If an action is not implemented, show a disabled state with an honest explanation or omit it.

## Prototype journeys

Connect at least these end-to-end flows:
1. Home → search → loaded page → site info → permission decision.
2. Home → new tab → switch tabs → close tab → restore recently closed.
3. Home → bookmark → folder management → find in Recall.
4. Page → download → progress → complete/fail → open/remove.
5. Page → reader mode → adjust typography → exit.
6. Settings → theme/language → switch English LTR ↔ Arabic RTL.
7. Settings → privacy → clear selected data → confirmation → restored browsing state.
8. Password manager → authenticate → choose saved account → fill HTTPS form.
9. Private tab → browse → close private tab → verify no history/session residue.
10. Future account concept → provider consent → sync categories → device management (clearly prototype-only).

## Handoff requirements

- Use Auto Layout and reusable components with meaningful names.
- Keep layers and pages organized; avoid giant unstructured frames.
- Use variables/tokens for themes, spacing, typography, and colors.
- Include component properties and variants for all important states.
- Add annotations for responsive behavior and RTL rules.
- Include a screen-to-feature matrix and an interaction map.
- Flag missing product decisions instead of guessing.
- Deliver a concise implementation handoff: screen, component, state, action, data required, and current implementation status.

## Source of truth

The repository's current feature status is documented in README.md, ROADMAP.md, and QA_CHECKLIST.md. Treat implemented code as authoritative. If a requested control is not backed by an implemented capability, label it planned or omit it. Do not let visual completeness misrepresent engineering readiness.
