package com.zou.hs.matcher.ingestion;

import com.zou.hs.matcher.domain.Language;

/** One raw row from EU nomenclature export (XLSX or CSV), before folding CN subdivisions into HS6. */
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
