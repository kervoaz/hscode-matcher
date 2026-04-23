package com.geodis.hs.matcher.api;

import java.util.Locale;

/** Normalizes product descriptions so bulk runs can reuse classify results for identical text. */
public final class BulkChapterDescriptionDedup {

    private BulkChapterDescriptionDedup() {}

    public static String normalizedDescriptionKey(String description) {
        if (description == null) {
            return "";
        }
        return description.trim().toLowerCase(Locale.ROOT);
    }
}
