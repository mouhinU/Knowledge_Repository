package com.mouhin.knowledge.repository.infrastructure.extraction;

import com.mouhin.knowledge.repository.domain.model.valueobject.ExtractionResult;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xssf.usermodel.XSSFCell;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

/**
 * Excel 文档提取服务（XLSX / XLS）
 *
 * <p>使用 Apache POI 按工作表提取，每个工作表一个 section。
 *
 * @author mouhinU
 * @date 2026-09-22 16:21:33
 */
@Service
@Slf4j
public class XlsxExtractionService {

    /**
     * 从 Excel 文件提取文本
     *
     * @param filePath Excel 文件路径
     * @return 提取结果
     * @throws IOException 读取失败
     */
    public ExtractionResult extract(Path filePath) throws IOException {
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
