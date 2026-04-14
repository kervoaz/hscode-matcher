package com.geodis.hs.matcher.domain;

import java.util.Objects;

/**
 * Single row from the HS nomenclature.
 *
 * <p>{@code code} is stored normalized as digits only (2, 4, 6, 8, or 10 digits, e.g. {@code
 * "010121"} or {@code "2204298610"}). Display formatting such as {@code 0101.21.00} is a presentation
 * concern.
 *
 * <p>{@code level} is {@code 2} for chapter, {@code 4} for heading, {@code 6} for HS subheading,
 * {@code 8} for CN subdivision, {@code 10} for the full CN code line.
 *
 * @param parentCode parent HS segment in the same normalized form, or {@code null} for chapters
 */
public record HsEntry(
        String code,
        int level,
        String description,
        Language language,
        String parentCode) {

    public HsEntry {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(description, "description");
        Objects.requireNonNull(language, "language");
    }
}
