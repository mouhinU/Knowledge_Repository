package com.mouhin.knowledge.repository.infrastructure.extractor;

import com.mouhin.knowledge.repository.domain.gateway.ContentExtractor;
import com.mouhin.knowledge.repository.domain.model.valueobject.ExtractionCandidate;
import com.mouhin.knowledge.repository.domain.model.valueobject.ExtractionResult;
import com.mouhin.knowledge.repository.domain.model.valueobject.ExtractionStrategyEnum;
import com.mouhin.knowledge.repository.infrastructure.extraction.ExtractionSupport;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xssf.usermodel.XSSFCell;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

/**
 * Excel 解析策略（XLSX / XLS）。
 *
 * <p>使用 Apache POI 按工作表提取，每个工作表一个 section。
 *
 * @author mouhinU
 * @date 2026-09-23
 */
@Component
@Slf4j
public class XlsxExtractionStrategy implements ContentExtractor {

    /** 本策略命中的 MIME 类型。 */
    private static final Set<String> SUPPORTED_MIME_TYPES =
            Set.of(
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    "application/vnd.ms-excel");

    @Override
    public String name() {
        return ExtractionStrategyEnum.XLSX.name();
    }

    @Override
    public int priority() {
        return 30;
    }

    @Override
    public boolean supports(ExtractionCandidate candidate) {
        return candidate != null && SUPPORTED_MIME_TYPES.contains(candidate.mimeType());
    }

    @Override
    public ExtractionResult extract(ExtractionCandidate candidate) throws IOException {
        Path filePath = candidate.filePath();
        try (InputStream is = Files.newInputStream(filePath);
                XSSFWorkbook workbook = new XSSFWorkbook(is)) {

            List<String> sheetTexts = new ArrayList<>();
            int totalSheets = workbook.getNumberOfSheets();

            for (int sheetIdx = 0; sheetIdx < totalSheets; sheetIdx++) {
                XSSFSheet sheet = workbook.getSheetAt(sheetIdx);
                sheetTexts.add(buildSheetText(sheet));
            }

            if (sheetTexts.isEmpty()) {
                sheetTexts.add("");
            }

            log.info("Excel extracted: {} sheets", totalSheets);
            return new ExtractionResult(
                    sheetTexts,
                    totalSheets,
                    false,
                    ExtractionSupport.calculateChecksum(filePath),
                    "xlsx");
        }
    }

    private String buildSheetText(XSSFSheet sheet) {
        StringBuilder sheetContent = new StringBuilder();
        sheetContent.append("[Sheet: ").append(sheet.getSheetName()).append("]\n");

        for (int rowIdx = 0; rowIdx <= sheet.getLastRowNum(); rowIdx++) {
            XSSFRow row = sheet.getRow(rowIdx);
            if (row == null) {
                continue;
            }
            String rowText = buildRowText(row);
            if (!rowText.isBlank()) {
                sheetContent.append(rowText).append("\n");
            }
        }
        return sheetContent.toString().trim();
    }

    private String buildRowText(XSSFRow row) {
        List<String> cells = new ArrayList<>();
        for (int colIdx = 0; colIdx < row.getLastCellNum(); colIdx++) {
            XSSFCell cell = row.getCell(colIdx);
            cells.add(cell != null ? getCellValueAsString(cell) : "");
        }
        return String.join("\t", cells);
    }

    private String getCellValueAsString(XSSFCell cell) {
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue();
            case NUMERIC -> {
                double val = cell.getNumericCellValue();
                yield val == Math.floor(val) ? String.valueOf((long) val) : String.valueOf(val);
            }
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case FORMULA -> cell.getCellFormula();
            case BLANK -> "";
            default -> "";
        };
    }
}
