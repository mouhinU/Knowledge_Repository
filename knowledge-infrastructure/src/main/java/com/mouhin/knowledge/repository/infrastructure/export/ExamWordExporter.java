package com.mouhin.knowledge.repository.infrastructure.export;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.OutputStream;

/**
 * 试卷 Word 导出工具
 * <p>
 * 将 Markdown 格式的试卷内容转换为 Word 文档。
 * 解析 Markdown 标题、粗体、列表等格式，映射为 Word 样式。
 * </p>
 *
 * @author Knowledge-Repository
 * @date 2026-09-13
 */
@Component
public class ExamWordExporter {

    private static final Logger logger = LoggerFactory.getLogger(ExamWordExporter.class);

    /**
     * 将 Markdown 试卷内容导出为 Word 文档
     *
     * @param markdown     Markdown 格式的试卷内容
     * @param outputStream 输出流
     * @throws IOException 写入失败
     */
    public void export(String markdown, OutputStream outputStream) throws IOException {
        try (XWPFDocument document = new XWPFDocument()) {
            String[] lines = markdown.split("\n");

            for (String line : lines) {
                String trimmed = line.trim();
                if (trimmed.isEmpty()) {
                    continue;
                }

                // 跳过分隔线
                if (trimmed.equals("---") || trimmed.equals("***")) {
                    continue;
                }

                // 标题
                if (trimmed.startsWith("# ")) {
                    addHeading(document, trimmed.substring(2).trim(), 1);
                } else if (trimmed.startsWith("## ")) {
                    addHeading(document, trimmed.substring(3).trim(), 2);
                } else if (trimmed.startsWith("### ")) {
                    addHeading(document, trimmed.substring(4).trim(), 3);
                }
                // 列表项
                else if (trimmed.startsWith("- ") || trimmed.startsWith("* ")) {
                    addListItem(document, trimmed.substring(2).trim());
                }
                // 普通段落
                else {
                    addBodyParagraph(document, trimmed);
                }
            }

            document.write(outputStream);
            logger.info("试卷 Word 文档导出完成");
        }
    }

    /**
     * 添加标题
     */
    private void addHeading(XWPFDocument document, String text, int level) {
        XWPFParagraph paragraph = document.createParagraph();
        paragraph.setSpacingBefore(level == 1 ? 240 : 160);
        paragraph.setSpacingAfter(80);

        String cleanText = text.replaceAll("\\*\\*", "").replaceAll("\\*", "");

        XWPFRun run = paragraph.createRun();
        run.setText(cleanText);
        run.setBold(true);

        switch (level) {
            case 1:
                run.setFontSize(18);
                break;
            case 2:
                run.setFontSize(15);
                break;
            default:
                run.setFontSize(13);
                break;
        }
    }

    /**
     * 添加列表项
     */
    private void addListItem(XWPFDocument document, String text) {
        XWPFParagraph paragraph = document.createParagraph();
        paragraph.setIndentationLeft(480);
        paragraph.setSpacingAfter(40);
        addFormattedText(paragraph, text, "  \u2022  ");
    }

    /**
     * 添加普通段落
     */
    private void addBodyParagraph(XWPFDocument document, String text) {
        XWPFParagraph paragraph = document.createParagraph();
        paragraph.setSpacingAfter(60);
        addFormattedText(paragraph, text, "");
    }

    /**
     * 添加带格式的文本（处理 **bold** 标记）
     */
    private void addFormattedText(XWPFParagraph paragraph, String text, String prefix) {
        if (prefix != null && !prefix.isEmpty()) {
            XWPFRun prefixRun = paragraph.createRun();
            prefixRun.setText(prefix);
            prefixRun.setFontSize(11);
        }

        String remaining = text;
        while (!remaining.isEmpty()) {
            int boldStart = remaining.indexOf("**");
            if (boldStart < 0) {
                XWPFRun run = paragraph.createRun();
                run.setText(remaining);
                run.setFontSize(11);
                break;
            }

            if (boldStart > 0) {
                XWPFRun run = paragraph.createRun();
                run.setText(remaining.substring(0, boldStart));
                run.setFontSize(11);
            }

            remaining = remaining.substring(boldStart + 2);
            int boldEnd = remaining.indexOf("**");
            if (boldEnd < 0) {
                XWPFRun run = paragraph.createRun();
                run.setText("**" + remaining);
                run.setFontSize(11);
                break;
            }

            XWPFRun boldRun = paragraph.createRun();
            boldRun.setText(remaining.substring(0, boldEnd));
            boldRun.setBold(true);
            boldRun.setFontSize(11);

            remaining = remaining.substring(boldEnd + 2);
        }
    }
}
