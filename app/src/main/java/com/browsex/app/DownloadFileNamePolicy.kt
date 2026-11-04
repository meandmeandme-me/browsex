package com.browsex.app

/**
 * Produces one safe display name for both persisted metadata and filesystem output.
 * Keeps Unicode names while removing path separators, controls and bidi overrides.
 */
internal object DownloadFileNamePolicy {
    private val unsafeBidiControls = setOf(0x061C, 0x200E, 0x200F) +
        (0x202A..0x202E) + (0x2066..0x2069)

    fun sanitize(candidate: String?): String {
        val cleaned = candidate.orEmpty()
            .filterNot { it.isISOControl() || it.code in unsafeBidiControls }
            .replace(Regex("[\\\\/:*?\"<>|]"), "_")
            .trim()
            .trim('.')
            .take(140)
            .trim()
            .trim('.')
        return cleaned.ifBlank { "download" }
    }
}
