package com.browsex.app

import java.text.Normalizer
import java.util.Locale

/** Locale-independent Unicode normalization for case-insensitive browser-library search. */
internal object BrowserTextNormalizer {
    fun normalize(value: String): String =
        Normalizer.normalize(value, Normalizer.Form.NFKC)
            .lowercase(Locale.ROOT)
            .trim()
}
