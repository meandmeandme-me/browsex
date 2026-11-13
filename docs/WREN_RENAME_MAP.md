# Wren identity rename map

This map records the new identity and where it is applied. Retired product spellings are intentionally omitted so the repository can keep a zero-match identity check.

| Area | Wren identity / destination |
| --- | --- |
| App name and launcher | `Wren` in English and Arabic `app_name`; adaptive and TV launcher assets use Wren artwork and labels. |
| User-facing surfaces | Start page, browser chrome, About/legal, notifications, private-mode labels, accessibility descriptions, settings, downloads, reader, bookmarks, and Arabic strings use Wren wording. |
| Kotlin packages | `com.wren.app` and `com.wren.app.storage`; source and tests live under matching `com/wren/app` directories. |
| App and build identity | Gradle namespace and application ID are both `com.wren.app`; project and workflow are named Wren. |
| Kotlin symbols and files | Product-prefixed application, browser chrome, database, notification, settings, and feature screens use `Wren*` names. Generic browser-domain types retain their descriptive names. |
| Room | Database class is `WrenDatabase`; exported schema is `app/schemas/com.wren.app.storage.WrenDatabase/2.json`; `wren-browser.db` remains the database file name and schema validation stays enabled. |
| DataStore | Settings file is `wren.settings`. |
| Credentials | Android Keystore and encrypted credential aliases use Wren-scoped identifiers. Credential encryption, authenticated encryption, origin checks, and unreadable-data protections remain enabled. |
| Notifications | Channel and action identifiers use Wren identity; the website notification ID remains a stable numeric value. |
| FileProvider | Authority is derived from `${applicationId}.files`; XML paths resource is `wren_file_paths`. |
| Autofill | Autofill service resource is `wren_autofill_service`; service label and description identify Wren. |
| Gradle and artifacts | Build output and uploaded APK artifacts use Wren names. Signing configuration reads only the Wren environment variables below. |
| CI secrets to configure | `WREN_DEBUG_KEYSTORE_BASE64`, `WREN_DEBUG_STORE_PASSWORD`, `WREN_DEBUG_KEY_ALIAS`, `WREN_DEBUG_KEY_PASSWORD`, `WREN_RELEASE_KEYSTORE_BASE64`, `WREN_RELEASE_STORE_PASSWORD`, `WREN_RELEASE_KEY_ALIAS`, `WREN_RELEASE_KEY_PASSWORD`. Release signing remains optional as a complete set; partial configuration fails. |
| Documentation and scripts | Build, database, UI, identity, and contribution documents and Python checks use Wren naming. Kingfisher's MIT license and copyright notice remain intact. |
| Repository hosting | The GitHub repository slug is controlled by GitHub and cannot be changed by a local source rename. The owner must rename the repository to `wren` (or the chosen Wren slug) and update any external links, branch protections, Actions secrets, badges, and integrations. |

## Data continuity

The new application ID creates a separate Android app data directory. There is no production account or user dataset to migrate. Existing development data stays under the former installed package and is not silently deleted. Database schema exports and Room validation remain in place; no destructive migration fallback is introduced. A package-ID change also means the old installed app and the Wren build are separate installs until manually removed.

## Repository-hosting follow-up

After the source branch is reviewed, rename the GitHub repository from its current slug to the selected Wren slug. GitHub redirects standard repository URLs, but update workflow references, badges, release links, and integrations that store the old slug. Configure the eight Wren signing secrets listed above in repository Actions settings; secret values are not committed.
