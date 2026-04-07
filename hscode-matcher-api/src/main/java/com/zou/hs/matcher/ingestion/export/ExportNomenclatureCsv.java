package com.zou.hs.matcher.ingestion.export;

import com.zou.hs.matcher.ingestion.xlsx.XlsxNomenclatureExporter;
import java.nio.file.Path;

/**
 * CLI: convert EU {@code Nomenclature*.xlsx} to UTF-8 CSV for runtime ingest.
 *
 * <pre>
 *   java -cp ... com.zou.hs.matcher.ingestion.export.ExportNomenclatureCsv NomenclatureEN.XLSX nomenclature-en.csv
 * </pre>
 *
 * <p>Maven (recommended on PowerShell, avoids space-splitting {@code -Dexec.args}):
 *
 * <pre>
 *   mvn -q exec:java -Dexec.in=../NomenclatureEN.XLSX -Dexec.out=../nomenclature-en.csv
 * </pre>
 *
 * Bash/PowerShell alternative: quote the whole property: {@code mvn exec:java
 * "-Dexec.args=../NomenclatureEN.XLSX ../nomenclature-en.csv"}
 */
public final class ExportNomenclatureCsv {

    public static void main(String[] args) throws Exception {
        if (args.length != 2
                || args[0] == null
                || args[0].isBlank()
                || args[1] == null
                || args[1].isBlank()) {
            System.err.println("Usage: ExportNomenclatureCsv <input.xlsx> <output.csv>");
            System.err.println(
                    "Maven: mvn -q exec:java -Dexec.in=<input.xlsx> -Dexec.out=<output.csv>");
            System.exit(1);
        }
        Path in = Path.of(args[0]);
        Path out = Path.of(args[1]);
        XlsxNomenclatureExporter.exportToCsv(in, out);
        System.out.println("Wrote " + out.toAbsolutePath());
    }
}
