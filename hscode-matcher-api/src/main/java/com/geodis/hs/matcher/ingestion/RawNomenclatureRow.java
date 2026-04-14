package com.geodis.hs.matcher.ingestion;

import com.geodis.hs.matcher.domain.Language;

/** One raw row from EU nomenclature export (XLSX or CSV). */
public record RawNomenclatureRow(
        String tenDigitGoodsCode,
        int hierarchyPosition,
        Language language,
        String description) {

    public RawNomenclatureRow {
        if (hierarchyPosition != 2
                && hierarchyPosition != 4
                && hierarchyPosition != 6
                && hierarchyPosition != 8
                && hierarchyPosition != 10) {
            throw new IllegalArgumentException("hierarchyPosition must be 2,4,6,8,10: " + hierarchyPosition);
        }
    }
}
