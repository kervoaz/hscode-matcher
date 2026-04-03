package com.geodis.hs.matcher.ingestion.export;

import com.geodis.hs.matcher.ingestion.xlsx.XlsxNomenclatureExporter;
import java.nio.file.Path;

/**
 * CLI: convert EU {@code Nomenclature*.xlsx} to UTF-8 CSV for runtime ingest.
 *
 * <pre>
 *   java -cp ... com.geodis.hs.matcher.ingestion.export.ExportNomenclatureCsv NomenclatureEN.XLSX nomenclature-en.csv
 * </pre>
 *
 * Or: {@code mvn exec:java -Dexec.mainClass=... -Dexec.args="..."}
 */
public final class ExportNomenclatureCsv {

    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            System.err.println("Usage: ExportNomenclatureCsv <input.xlsx> <output.csv>");
            System.exit(1);
        }
        Path in = Path.of(args[0]);
        Path out = Path.of(args[1]);
        XlsxNomenclatureExporter.exportToCsv(in, out);
        System.out.println("Wrote " + out.toAbsolutePath());
    }
}
