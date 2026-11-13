# Wren browser experience

## Visual direction

Wren should feel quiet, quick, and familiar: Chrome's direct navigation and Safari's restrained floating controls, expressed through the Wren bird mark and teal identity. The live web page is the main canvas. Browser controls sit lightly above it and recede while reading.

## Color and surfaces

- Use a soft neutral canvas (`#F5F8F9`) for Wren-owned start, tab, and settings surfaces; let websites keep their own colors.
- Use deep ink (`#16242C`) for primary text and muted slate (`#52636B`) for supporting text.
- Reserve Wren teal (`#0E7C86`) for actions and selected states. Avoid broad teal fills, saturated gradients, and decorative color blocks.
- Keep the address/navigation dock at the bottom by default. Use a translucent light or dark surface, a fine edge, and a soft shadow; keep the address field legible and controls easy to reach.
- Apply transparency only to browser controls and menus over web content. Keep reading content and cards opaque enough to remain legible.
- When the user enables reduced transparency or high contrast, use opaque surfaces and stronger outlines.

The Android shell uses Compose over GeckoView. Android Compose's regular blur modifier blurs the control itself, not the live page behind it; do not describe that as backdrop blur. The current dock therefore uses translucency, edge definition, and elevation. A future true backdrop blur must be API-aware, measured on real devices, and retain the opaque accessibility fallback.

## Interaction

- The address field accepts web addresses and search terms and submits them to the active GeckoView session.
- Back and forward use the active GeckoSession history; reload, home, tab switcher, and page actions stay reachable from the bottom dock.
- Keep the dock to a small set of frequent actions. Put secondary actions in a grouped page menu.
- Preserve the current page while opening menus, tab overview, site information, and settings.
- Use native page content for websites; Wren's start page is the only authored HTML content.

## Shared behavior and accessibility

- Keep phone, tablet, foldable, landscape, and keyboard/TV layouts adaptive.
- Preserve Arabic locale parity and right-to-left direction without reversing URL text.
- Maintain descriptive accessibility labels, minimum 48 dp touch targets, scalable text, reduced motion, reduced transparency, and high contrast.
- Private browsing, site permission decisions, notification controls, credential origin checks, credential encryption, Room schema validation, and migration safeguards are product behavior and must remain connected to the same controllers and policies.
- Keep launcher name, app label, About/legal text, private-mode labels, notifications, and accessibility strings under the Wren identity.

## Review checklist

1. Confirm the start page and open-page dock remain visually quiet at phone and tablet sizes.
2. Open an actual website and check address submission, back, forward, reload, tabs, and page menu.
3. Check light, dark, Arabic/RTL, reduced transparency, high contrast, and large text.
4. Confirm web content remains readable behind the translucent controls and the reduced-transparency mode stays opaque.
5. Run locale, unit, lint, identity, database policy, and Room schema checks in an Android-capable environment.
