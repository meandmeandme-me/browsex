package com.browsex.app

import java.text.Normalizer
import java.util.Locale

/**
 * Locale-independent tab search. NFKD normalization makes visually equivalent
 * text searchable and removing combining marks lets Arabic queries match text
 * whether or not the page title contains harakat.
 */
internal object TabSearchPolicy {
    private val combiningMarks = Regex("\\p{M}+")
    private val repeatedWhitespace = Regex("\\s+")

    fun normalize(value: String): String = Normalizer
        .normalize(value, Normalizer.Form.NFKD)
        .lowercase(Locale.ROOT)
        .replace('\u0640'.toString(), "")
        .replace(combiningMarks, "")
        .replace(repeatedWhitespace, " ")
        .trim()

    fun matches(title: String, url: String, query: String): Boolean =
        matchesNormalized(title, url, normalize(query))

    /** Reuses a normalized query while filtering a complete tab collection. */
    fun matchesNormalized(title: String, url: String, normalizedQuery: String): Boolean {
        if (normalizedQuery.isEmpty()) return true
        return normalize(title).contains(normalizedQuery) || normalize(url).contains(normalizedQuery)
    }
}
