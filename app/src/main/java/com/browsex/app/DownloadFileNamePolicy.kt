package com.browsex.app

/**
 * Produces one safe display name for both persisted metadata and filesystem output.
 * Keeps Unicode names while removing path separators, controls and bidi overrides.
 */
internal object DownloadFileNamePolicy {
    private const val MAX_CODE_POINTS = 140

    private val unsafeBidiControls = setOf(0x061C, 0x200E, 0x200F) +
        (0x202A..0x202E) + (0x2066..0x2069)

    fun sanitize(candidate: String?): String {
        val cleaned = candidate.orEmpty()
            .filterNot { it.isISOControl() || it.code in unsafeBidiControls }
            .replace(Regex("[\\\\/:*?\"<>|]"), "_")
            .trim()
            .trim('.')
            .takeCodePoints(MAX_CODE_POINTS)
            .trim()
            .trim('.')
        return cleaned.ifBlank { "download" }
    }

    /** Truncates by Unicode code point so a supplementary character is never split. */
    private fun String.takeCodePoints(limit: Int): String {
        val endIndex = offsetByCodePoints(0, minOf(limit, codePointCount(0, length)))
        return substring(0, endIndex)
    }
}
