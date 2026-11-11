# BrowseX UI replacement and source adoption

## Direction

BrowseX is moving to one coherent Compose-based browser experience. The visual reference and reusable browser-chrome patterns are adapted from **Kingfisher Browser**:

- Upstream: https://github.com/nazmul-1117/kingfisher-browser
- License: MIT
- Upstream stack: Kotlin, Jetpack Compose, GeckoView
- BrowseX keeps its own package identity, GeckoView runtime, tab/session controllers, Room database, privacy controls, permissions, downloads, localization, accessibility policies, and account/sync contracts.

The MIT notice and attribution are retained in THIRD_PARTY_NOTICES.md.

## Replacement rule

Replace presentation layers, not browser domain behavior. Existing feature screens are wired to BrowseX callbacks and repositories; deleting their controllers or storage would silently remove real features. Each screen is therefore migrated onto the shared BrowseX Compose design system before obsolete presentation code is removed.

## Screen inventory and migration target

| Existing surface | Target treatment |
| --- | --- |
| Browser chrome / address bar | Floating glass address dock with progress, suggestions, site identity, and safe navigation actions |
| Home page | BrowseX-branded start page, responsive shortcuts, search, discovery, light/dark and reduced-motion support |
| Tab switcher | Responsive cards/grid, clear private-tab distinction, close/restore actions |
| Browser menu | Grouped actions with clear icons, labels, and touch targets |
| Settings | One section hierarchy, shared controls, accessible descriptions and RTL |
| Bookmarks and folders | Shared list/grid surfaces, folder navigation, import/export and reorder |
| History / Recall | Search-first lists, readable metadata, empty/error/loading states |
| Downloads | Explicit queued/running/complete/failed/cancelled states and open/share actions |
| Privacy center | Protection status, per-site controls, clear-data flow and permissions |
| Site information / permissions | Origin identity, permission state, allow/block/reset actions |
| Saved sessions | Session cards, restore, restore alongside, rename, import and delete |
| Reader mode | Distraction-free reading typography and controls |
| About / legal / notices | BrowseX identity and complete third-party attributions |

## Shared visual rules

- Brand: BrowseX; the launcher, home page, native labels and private-mode chrome must not show the former Wren identity.
- Geometry: BrowseXDesignSystem owns shared Material 3 shape sizes and touch-target constants.
- Color: BrowserPalette remains the single source for light/dark and user-selected accent colors.
- Layout: preserve adaptive behavior for phones, tablets, foldables, landscape and TV/keyboard navigation.
- Accessibility: honor RTL, contrast, reduced transparency, reduced motion, scalable text and descriptive control labels.
- Performance: glass effects must be optional and disabled by the existing reduced-transparency/performance policies.

## Completion gates

1. All user-facing pages use the shared BrowseX design system.
2. No page has placeholder or inert actions.
3. No old brand name, logo, or launcher banner remains in user-visible resources.
4. Existing callbacks, storage, browser engine and privacy behavior remain connected.
5. CI validates resources, locale parity, Kotlin compilation, unit tests, lint and release packaging.
6. Device testing confirms phone/tablet/RTL behavior before calling the redesign complete.

This document is a migration contract, not a claim that every screen has already been redesigned.
