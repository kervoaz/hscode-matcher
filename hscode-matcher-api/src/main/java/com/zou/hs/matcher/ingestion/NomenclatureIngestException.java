package com.zou.hs.matcher.ingestion;

/** Thrown when nomenclature rows fail validation or integrity checks. */
public class NomenclatureIngestException extends RuntimeException {

    public NomenclatureIngestException(String message) {
        super(message);
    }

    public NomenclatureIngestException(String message, Throwable cause) {
        super(message, cause);
    }
}
