# BrowseX — Uizard screen and interaction map

> Product source: `docs/WORLD_BROWSER_PRODUCT_REQUIREMENTS.md`. This is the target screen map for the next UI direction. The existing Figma blueprint is retained as historical reference; Uizard must not invent working controls for features that are not implemented.

## Design system direction

- Premium Wren browser chrome inspired by familiar Chrome/Safari interaction patterns; use selective translucent glass surfaces and preserve text contrast. The phone default places search/address at the bottom.
- One spacing, radius, elevation, typography and color-token system. System / Light / Dark themes.
- Phone-first controls with thumb reach, then adaptive layouts for large phones, landscape, foldables and tablets.
- RTL and LTR are first-class. Test Arabic RTL with mixed Latin URL, numbers, email and punctuation examples.
- Touch targets should meet Android accessibility guidance; every icon-only action needs a meaningful accessible label.
- Progressive disclosure: everyday browsing is simple; advanced controls appear in tab/library/site panels.
- Do not show a functional toggle for ad blocking, cloud sync, sign-in, translation, password manager or extensions until each feature is actually implemented.

## Global navigation model

Primary browser surface:
- Bottom-anchored address/search field on phones, inside the Wren glass dock; the dock also contains core navigation actions. Adapt its placement and density for landscape/tablets while preserving thumb reach on phones.
- Tab switcher with visible count.
- New tab.
- Back / forward / reload.
- Bookmark action.
- Overflow menu.
- Browser content viewport.

Library destinations:
- Bookmarks and folders.
- History.
- Downloads.
- Reading list / collections (future).
- Saved sessions.
- Recall / universal search.

Settings destinations:
- General and search engine.
- Appearance and language.
- Privacy and security.
- Site permissions and notifications.
- Home page.
- Account and sync (future; clearly marked unavailable until real implementation).
- About and diagnostics.

## Screen inventory

### 1. Browser / active tab

Purpose: everyday browsing.

Components:
- Address/search field with URL/search detection, clear and submit actions.
- Security/site identity affordance for HTTP(S) pages.
- Loading progress and stop/reload state.
- Back / forward state.
- Bookmark state.
- Tab count and menu.
- Page content viewport and fullscreen behavior.

States:
- New tab / home.
- Loading.
- Loaded.
- Offline / network failure.
- Invalid or blocked URL.
- Unsupported external scheme.
- Content process crashed with retry.
- Fullscreen media.
- Private tab with persistent private indicator and distinct chrome treatment.

### 2. Home / new tab

Purpose: quick entry and useful continuation.

Sections:
- Search/address entry.
- Editable quick shortcuts.
- Optional discovery card.
- Recently visited (only if user enables it).
- Recent or saved sessions entry.
- Theme-aware background and layout.

Interactions:
- Add, edit, remove and move shortcuts earlier/later with visible boundary states.
- Toggle sections and reset defaults.
- Grid/list shortcut layout.
- Empty state with a clear add-shortcut action.

### 3. Tab workspace

Purpose: switch and organize open pages.

Components:
- Search tabs by title or URL.
- Cards show title, domain, selected state and private marker.
- New tab and close-tab actions.
- Save current regular tabs as a named session.
- Browse saved sessions.
- Restore recently closed regular tabs (private tabs never appear here).

States:
- 0 / 1 / many tabs.
- 20-tab limit reached.
- Crashed tab and retry.
- Private tabs separated from regular tabs.
- Restored but not yet loaded background tabs.

Interactions:
- Open a tab.
- Close a tab.
- Open a saved session within the remaining tab capacity.
- Save / rename by saving over an existing session name / delete a saved session.
- Never include private tabs in saved sessions or durable restore.

### 4. Private browsing

Purpose: make private mode unmistakable.

Components:
- Distinct private theme and clear label.
- Explanation of what BrowseX does not save.
- Close private tabs action.
- Private tab counter.

Rules:
- No private URLs in history, normal restore, saved sessions, Recall, cloud sync or analytics.
- Closing private tabs destroys their private Gecko sessions.
- Do not imply anonymity from websites, network operators or employers.

### 5. Site information and permissions

Purpose: explain and control a site's access.

Components:
- Origin and connection/security details.
- Permission rows: location, camera, microphone, notifications, persistent storage where supported.
- Each permission has Ask / Allow / Block where the engine permits it.
- Clear site data action with clear scope warning.
- Reset supported site permissions.

Flows:
- Website asks -> Allow / Block / Not now.
- Android runtime permission -> system prompt -> site decision remains separate.
- Android denial -> explain how to open system settings.
- Site changed during prompt -> reject stale prompt rather than applying to the new origin.
- Persistent-storage grants may not be revocable through GeckoView; communicate the engine limitation.

### 6. Bookmarks and folders

Purpose: organize saved pages.

Components:
- Search by title, domain and URL.
- Folder chips/list.
- Create, rename and delete folders.
- Bookmark rows with page title, domain and URL.
- Move to folder.
- Move earlier / later.
- Import and export actions in Settings or library overflow.

States:
- Empty library.
- Empty folder.
- No search matches.
- Import validation error.
- Duplicate conflict preview with valid/skipped counts.
- Explicit merge versus destructive replace confirmation.
- Import complete / export complete.

Data rules:
- Stable IDs; never use URL hash as the permanent primary key for new entries.
- Legacy URL-only migration to Unfiled.
- Import is validated before writes and bounded by file size, item count and safe URL scheme.
- Folder ID/name collisions must be remapped deterministically; never move imported items into an unrelated local folder.
- Export contains bookmarks and folders only, not history, cookies, passwords or sessions.

### 7. History

Purpose: find and reopen visited pages.

Components:
- Search by page title, domain and URL.
- Group by date (future visual improvement).
- Remove one item.
- Clear history with confirmation.
- Clear browsing data remains a separate privacy-center operation.

States:
- Empty history.
- No matches.
- Legacy migrated history with unknown timestamp.
- History disabled/cleared.

Rules:
- Private browsing never appears.
- History sync, if ever added, requires separate opt-in.

### 8. Downloads

Purpose: track and manage file transfers.

Components:
- File title and filename.
- Status: waiting, downloading, paused, complete, failed, unknown.
- Progress, downloaded/total size, timestamp.
- Open completed file.
- Cancel active transfer.
- Remove stale record.
- Clear failure reason and retry only when safe.

Implementation status and constraints:
- Gecko external responses are now written from the received response stream without replaying the URL, preserving the original POST/authenticated response body as delivered by Gecko.
- The current writer records final success/failure but does not yet provide live progress, cancellation, resumable transfer or process-death continuation.
- Do not display a false “resume” action for transfers that cannot be resumed safely.

### 9. BrowseX Recall / quick find

Purpose: one search surface for current work.

Search targets:
- Regular open tabs.
- Bookmarks.
- Recent history.
- Saved sessions (future integration).

Rules:
- Private tabs and private history are excluded.
- Show result type and title/domain.
- Empty and no-match states.

### 10. Reader mode

Purpose: distraction-reduced reading.

Components:
- Article title and source.
- Extracted content.
- Text size and theme controls (future enhancement).
- Return to original page.

States:
- Extracting.
- Extraction failed.
- No readable content.
- Ready.

### 11. Privacy center

Purpose: understandable data controls.

Options:
- Cookies.
- DOM/site storage.
- Cache.
- Authentication session data.
- Site permissions where supported.
- BrowseX history (separate local history store).

Flows:
- Choose categories.
- Explain sign-out impact and scope.
- Confirm.
- Progress state.
- Success / failure.
- Restore regular tabs after clearing.
- Private tabs are not restored.

### 12. Settings

Sections:
- Search engine.
- Theme.
- Language.
- Website notifications.
- Android notification permission.
- Android app permissions.
- Bookmarks and history.
- Import / export.
- Home page.
- Privacy center.
- Account / sync (future, disabled until implemented).
- About.

### 13. Account and sync (future only)

Do not design as an active flow until implementation starts.

When ready, include:
- Optional sign-in with Google, Microsoft personal/work-school, Apple and OIDC providers.
- Provider-neutral account identity.
- Separate sync consent per data category.
- Device list and revoke-device action.
- Pause sync.
- Keep local data / remove local data choices on sign-out.
- Cloud data export and deletion.
- Encryption and recovery explanation.
- Clear signed-out, signing-in, offline, expired-session and sync-conflict states.

### 14. Error and recovery states

Reusable patterns for:
- Offline / timeout.
- DNS failure.
- Certificate/security warning.
- Unsupported download.
- Storage full.
- Permission denied.
- File unavailable.
- Session crash.
- Import file too large or invalid.
- Sync unavailable (future).
- App cannot open selected file.

## Core interaction flows

1. First launch → home → search/address → load page → save bookmark → reopen from library.
2. Open tabs → search → switch → close → save named session → restore saved session.
3. Open private tab → browse → close private tabs → verify private state is not restored.
4. Website requests location/camera/microphone → explain → Android permission → site permission → manage later in Site Info.
5. Download file → confirm → progress → complete/fail → open/cancel/remove.
6. Import backup → validate → preview merge/replace → commit → success/error.
7. Privacy center → select categories → explain scope → confirm → clear → restore regular tabs.
8. Settings → language/theme → apply consistently to browser chrome, local home and native feature screens.

## Uizard deliverables

Create:
- Phone portrait: all primary screens.
- Large phone / landscape: browser, tab workspace, settings, downloads and library.
- Tablet: multi-column tab workspace, persistent navigation/panel option and wider settings/library.
- Arabic RTL and English LTR variants for key screens.
- Component library: address field, tab card, bookmark row, folder chip, status row, permission card, empty/error state, dialog and bottom sheet.
- Clickable prototype for the eight core flows above.
- Annotated states and design tokens, not only polished static screens.
