package com.geodis.hs.matcher.ingestion;

import java.util.Optional;

/**
 * Parses EU TARIC/CN "Goods code" cells (10-digit base plus optional suffix such as {@code " 80"}).
 *
 * <p>Hierarchy for HS v1 uses the leading digits of that 10-digit code: chapter (2), heading (4),
 * subheading (6). Rows with hierarchy position 8 or 10 are national/CN subdivisions; their text is
 * folded into the 6-digit HS bucket for search.
 */
public final class GoodsCodes {

    private GoodsCodes() {}

    /**
     * Extracts the first 10 digits from the goods-code cell (first whitespace-separated token).
     *
     * @return {@code null} if fewer than 10 digits are present (notes, blanks, malformed rows)
     */
    public static String tryTenDigitCore(String goodsCodeCell) {
        if (goodsCodeCell == null) {
            return null;
        }
        String trimmed = goodsCodeCell.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        int sp = 0;
        while (sp < trimmed.length() && !Character.isWhitespace(trimmed.charAt(sp))) {
            sp++;
        }
        String firstToken = trimmed.substring(0, sp);
        StringBuilder digits = new StringBuilder(12);
        for (int i = 0; i < firstToken.length(); i++) {
            char c = firstToken.charAt(i);
            if (c >= '0' && c <= '9') {
                digits.append(c);
            }
        }
        if (digits.length() < 10) {
            return null;
        }
        return digits.substring(0, 10);
    }

    public static String requireTenDigitCore(String goodsCodeCell) {
        String ten = tryTenDigitCore(goodsCodeCell);
        if (ten == null) {
            throw new NomenclatureIngestException("Invalid goods code cell (need 10 digits): " + goodsCodeCell);
        }
        return ten;
    }

    /** HS-style key: 2, 4, or 6 digits derived from the 10-digit goods code and hierarchy position. */
    public static String hsKey(int hierarchyPosition, String tenDigitGoodsCode) {
        return switch (hierarchyPosition) {
            case 2 -> tenDigitGoodsCode.substring(0, 2);
            case 4 -> tenDigitGoodsCode.substring(0, 4);
            case 6 -> tenDigitGoodsCode.substring(0, 6);
            case 8, 10 -> tenDigitGoodsCode.substring(0, 6);
            default -> throw new IllegalArgumentException("Unsupported hierarchy position: " + hierarchyPosition);
        };
    }

    public static int levelFromKeyLength(String hsKey) {
        return switch (hsKey.length()) {
            case 2 -> 2;
            case 4 -> 4;
            case 6 -> 6;
            default -> throw new IllegalArgumentException("HS key must be 2, 4, or 6 digits, got: " + hsKey.length());
        };
    }

    public static String parentKey(String hsKey, int level) {
        return switch (level) {
            case 2 -> null;
            case 4 -> hsKey.substring(0, 2);
            case 6 -> hsKey.substring(0, 4);
            default -> throw new IllegalArgumentException("level must be 2, 4, or 6");
        };
    }

    public static boolean isValidHsKey(String code) {
        return code != null && code.matches("\\d{2}|\\d{4}|\\d{6}");
    }

    /**
     * Normalizes user or URL input to an HS registry key (2, 4, or 6 digits). Strips all
     * non-digits (dots, spaces, etc.).
     *
     * @return empty if there are no digits or the digit count is not valid for HS v1
     */
    public static Optional<String> normalizeLookupCode(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        StringBuilder digits = new StringBuilder(raw.length());
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c >= '0' && c <= '9') {
                digits.append(c);
            }
        }
        String s = digits.toString();
        if (!isValidHsKey(s)) {
            return Optional.empty();
        }
        return Optional.of(s);
    }
}
