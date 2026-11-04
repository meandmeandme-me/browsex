# BrowseX localization workflow

## Current support statement

The reviewed Android UI resource packs are English (default) and Arabic. The app accepts BCP 47 language tags internally, but accepting a tag is not the same as shipping a reviewed translation. Unsupported locales currently fall back to Android's default resources; do not advertise every language as fully translated.

## Adding a locale

1. Create a resource directory using Android's locale qualifier, for example `values-fr`, `values-es`, or `values-b+zh+Hans`.
2. Translate every user-visible key from `values/strings.xml`; preserve resource names and format placeholders exactly.
3. Add the locale tag to `res/xml/locales_config.xml` so Android's per-app language settings can expose it.
4. Verify plural categories, date/number formatting, text expansion, font fallback and accessibility labels with native speakers.
5. Test the full browser chrome, settings, downloads, permission prompts, dialogs, notifications, local home page and errors—not only the main screen.
6. Run the locale validator locally and require it in CI. The validator checks resource parity, plural quantities, formatting placeholders and locale-config registration.

## RTL and mixed-direction content

RTL is a layout direction, not a string translation. Every locale must be tested with mixed-script examples such as Arabic text containing Latin hostnames, email addresses, numbers, punctuation and file names. Use Android layout direction APIs for native UI; use the local home page's validated language/direction configuration for HTML. Never reverse URL characters manually.

## Translation quality

- Keep product names, URLs, code, file extensions and protocol identifiers unchanged.
- Prefer concise action labels and explain security consequences in complete sentences.
- Do not use machine translation as the final review for permission, privacy, account, sync or credential flows.
- Keep placeholders and plural quantities intact; use locale-aware Android formatting rather than concatenating numbers into translated sentences.
- A locale is considered supported only after the entire UI inventory and accessibility pass are complete.

## Future expansion

Prioritize locales using user demand and verified contributor/reviewer availability. The architecture should support any valid BCP 47 tag, but locale selection should list only reviewed packs plus the System option. Website language preference and app-interface language remain separate controls.
