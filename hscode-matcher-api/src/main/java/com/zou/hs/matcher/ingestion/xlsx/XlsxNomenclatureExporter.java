package com.zou.hs.matcher.ingestion.xlsx;

import com.zou.hs.matcher.domain.Language;
import com.zou.hs.matcher.ingestion.GoodsCodes;
import com.zou.hs.matcher.ingestion.RawNomenclatureRow;
import com.zou.hs.matcher.ingestion.csv.NomenclatureCsvFormat;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/**
 * Reads EU CIRCABC {@code Nomenclature*.XLSX} and writes a compact UTF-8 CSV for runtime ingest.
 *
 * <p>Not intended for use on the request path; run as a CLI or build task ({@link
 * com.zou.hs.matcher.ingestion.export.ExportNomenclatureCsv}).
 */
public final class XlsxNomenclatureExporter {

    private static final DataFormatter FORMATTER = new DataFormatter();

    private XlsxNomenclatureExporter() {}

    public static List<RawNomenclatureRow> readRows(Path xlsxPath) throws IOException {
        List<RawNomenclatureRow> rows = new ArrayList<>();
        try (XSSFWorkbook workbook = new XSSFWorkbook(Files.newInputStream(xlsxPath))) {
            Sheet sheet = workbook.getSheetAt(0);
            int last = sheet.getLastRowNum();
            for (int i = 1; i <= last; i++) {
                Row row = sheet.getRow(i);
                if (row == null) {
                    continue;
                }
                String goodsCell = cellString(row.getCell(0));
                String ten = GoodsCodes.tryTenDigitCore(goodsCell);
                if (ten == null) {
                    continue;
                }
                String langCell = cellString(row.getCell(3));
                if (langCell.isBlank()) {
                    continue;
                }
                Language language;
                try {
                    language = Language.valueOf(langCell.trim().toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException e) {
                    continue;
                }
                Cell hierCell = row.getCell(4);
                int hier = readHierarchyPosition(hierCell);
                if (hier != 2 && hier != 4 && hier != 6 && hier != 8 && hier != 10) {
                    continue;
                }
                String description = cellString(row.getCell(6));
                rows.add(new RawNomenclatureRow(ten, hier, language, description));
            }
        }
        return rows;
    }

    public static void exportToCsv(Path xlsxPath, Path csvPath) throws IOException {
        List<RawNomenclatureRow> rows = readRows(xlsxPath);
        Files.createDirectories(csvPath.getParent() == null ? Path.of(".") : csvPath.getParent());
        try (Writer w = new BufferedWriter(new OutputStreamWriter(Files.newOutputStream(csvPath), StandardCharsets.UTF_8));
                CSVPrinter printer = new CSVPrinter(w, CSVFormat.DEFAULT)) {
            printer.printRecord(
                    NomenclatureCsvFormat.GOODS_CODE,
                    NomenclatureCsvFormat.HIER_POS,
                    NomenclatureCsvFormat.LANGUAGE,
                    NomenclatureCsvFormat.DESCRIPTION);
            for (RawNomenclatureRow r : rows) {
                printer.printRecord(
                        r.tenDigitGoodsCode(),
                        r.hierarchyPosition(),
                        r.language().name(),
                        r.description());
            }
        }
    }

    private static String cellString(Cell cell) {
        if (cell == null) {
            return "";
        }
        return FORMATTER.formatCellValue(cell);
    }

    private static int readHierarchyPosition(Cell cell) {
        if (cell == null) {
            return -1;
        }
        try {
            return switch (cell.getCellType()) {
                case NUMERIC -> (int) cell.getNumericCellValue();
                case STRING -> (int) Double.parseDouble(cell.getStringCellValue().trim());
                default -> (int) Double.parseDouble(FORMATTER.formatCellValue(cell).trim());
            };
        } catch (RuntimeException e) {
            return -1;
        }
    }
}
