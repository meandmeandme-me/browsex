# ADR-002: Keep GeckoView provisionally; make engine choice revisitable

- Status: Accepted, subject to capability review
- Date: 2026-11-04

## Context

Firefox for Android demonstrates mature GeckoView integration and reusable browser components. Cromite demonstrates privacy-oriented Chromium customization, but Chromium-specific patches are not portable to GeckoView. WebLibre demonstrates container and tree-tab product ideas, but those UX concepts require verified engine isolation guarantees before BrowseX can promise them.

The user has explicitly allowed replacing major parts of the current project. Therefore GeckoView is not a permanent constraint.

## Decision

Keep GeckoView as the current engine while extracting an app-owned browser domain and adapter boundary. Do not couple feature/domain rules to GeckoSession or GeckoView types. Translate engine callbacks into BrowseX-owned events and models.

## Re-evaluate the engine when

- Required extension APIs or extension distribution cannot be supported safely.
- Required container/cookie isolation cannot be guaranteed by supported APIs.
- Security update cadence or maintenance support becomes unacceptable.
- Accessibility, media, downloads or web compatibility gaps cannot be resolved within the adapter.
- Another engine materially improves compatibility/performance after measured comparison.

## Required comparison

Evaluate GeckoView, Android System WebView and any viable Chromium-based alternative against the same fixtures:

- Web compatibility and standards tests.
- Security update cadence and vulnerability response.
- Extension support and permission model.
- Cookie/storage isolation and private-mode guarantees.
- Android API/ABI coverage, APK size and native memory use.
- Startup, tab switching, background memory and low-end device behavior.
- Accessibility, media, downloads and process-crash recovery.
- License, maintenance ownership and long-term dependency risk.

Do not switch engines based on branding or feature checklists alone. Any switch requires a proof-of-concept, compatibility report, data/session migration plan and device benchmarks.
