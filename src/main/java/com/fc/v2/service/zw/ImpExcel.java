package com.fc.v2.service.zw;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * 化验室检测读数 Excel 的唯一读写口（xlsx/xls 都认，导出统一 xlsx）。
 *
 * <p><b>读</b>：第一张表第一行是列名，按列名定位（列序换了不要紧，列名缺了整份打回）；
 * 每格取「人眼看到的文本」（{@link DataFormatter}），日期格过 {@link ImpSampleTime} 统一尺
 * 转成标准串再喂校验。原始行 {@code rawLine} 按原列序逐格用「 | 」拼接，下钻看到的与文件里一致。
 *
 * <p><b>写</b>：列序、排序（调用方按批次号+行次排好）、时间样式、旗标词面全部钉死；
 * xlsx 按 OOXML 规范本就是 Unicode 存储，中文不乱码，不另设 CSV 旁路。同一批行、同一个导出时刻
 * 连导出两次，落盘字节逐字节相同（文档属性时间取传入的导出时刻，zip 条目时间钉成常量）。
 *
 * @author fuce
 * @date 2026-10-02
 */
public final class ImpExcel {

    // ---------- 模板列名（导入按名认列；顺序即 rawLine 拼接与导出列序） ----------
    public static final String COL_BATCH = "采样批次";
    public static final String COL_SITE = "点位编号";
    public static final String COL_ITEM = "读数明细";
    public static final String COL_QTY = "检测读数";
    public static final String COL_SAMPLE_TYPE = "样本类别";
    public static final String COL_TEST_AT = "检测时间";
    public static final String COL_RULE = "规则代号";
    public static final String COL_VALID = "是否有效";
    public static final String COL_REVIEWED = "是否已复核";

    /** 导入模板必须齐的九列（齐一个认一个，缺一列整份打回，不许猜列） */
    public static final List<String> REQUIRED_HEADERS = Collections.unmodifiableList(Arrays.asList(
            COL_BATCH, COL_SITE, COL_ITEM, COL_QTY, COL_SAMPLE_TYPE,
            COL_TEST_AT, COL_RULE, COL_VALID, COL_REVIEWED));

    /** 导出明细表头（报表照这一张明细表生成，逐项落到一行） */
    public static final List<String> EXPORT_HEADERS = Collections.unmodifiableList(Arrays.asList(
            "采样批次", "原表行次", "点位编号", "读数明细", "检测读数", "样本类别", "检测时间",
            "申报规则代号", "定级规则代号", "规则生效日", "评价等级",
            "是否有效", "是否已复核", "原始行", "校验结论", "校验说明",
            "异常确认人", "异常确认时间", "核实结论", "核实原因", "核实人", "核实时间",
            "归档标识", "归档时间"));

    private ImpExcel() {
    }

    /** 读表结果：文件级错误（空表/缺列）在 {@code error} 里点名，此时 rows 为空，不当成任何一批 */
    public static class ParsedFile {
        public String error;
        public List<ImpRowInspector.Input> rows = new ArrayList<ImpRowInspector.Input>();
    }

    /**
     * 读入整份文件：第一行认列名，其后逐行抠文本；全空白行跳过。
     * 每格文本保留原样，原始行按物理列序拼接。
     */
    public static ParsedFile read(InputStream in) throws IOException {
        ParsedFile result = new ParsedFile();
        Workbook wb;
        try {
            wb = WorkbookFactory.create(in);
        } catch (Exception e) {
            result.error = "文件不是可识别的 Excel（xlsx/xls）：" + e.getMessage();
            return result;
        }
        try {
            Sheet sheet = wb.getNumberOfSheets() == 0 ? null : wb.getSheetAt(0);
            if (sheet == null || sheet.getPhysicalNumberOfRows() == 0) {
                result.error = "Excel 第一张表为空，没有列名行";
                return result;
            }
            Row headerRow = sheet.getRow(sheet.getFirstRowNum());
            DataFormatter fmt = new DataFormatter();
            Map<String, Integer> idx = new LinkedHashMap<String, Integer>();
            List<String> physicalHeaders = new ArrayList<String>();
            if (headerRow != null) {
                // 从 0 列起逐列取名，与下面数据行的物理下标对齐（有前置空列也不错位）
                for (int c = 0; c < headerRow.getLastCellNum(); c++) {
                    physicalHeaders.add(cellText(headerRow.getCell(c), fmt).trim());
                }
            }
            for (String h : REQUIRED_HEADERS) {
                int at = physicalHeaders.indexOf(h);
                if (at < 0) {
                    result.error = "列名缺少「" + h + "」，模板九列须齐备（实际列名："
                            + physicalHeaders.toString() + "）";
                    return result;
                }
                idx.put(h, Integer.valueOf(at));
            }
            int first = sheet.getFirstRowNum() + 1;
            int last = sheet.getLastRowNum();
            for (int r = first; r <= last; r++) {
                Row row = sheet.getRow(r);
                if (row == null) {
                    continue;
                }
                List<String> physicalCells = new ArrayList<String>();
                // 原始行要逐格原样：数据行比表头多出的尾列也不能丢，按数据行自己的宽度取
                int headerCellCount = headerRow == null ? 0 : headerRow.getLastCellNum();
                int cellCount = Math.max(headerCellCount, row.getLastCellNum());
                for (int c = 0; c < cellCount; c++) {
                    physicalCells.add(cellText(row.getCell(c), fmt));
                }
                if (isAllBlank(physicalCells)) {
                    continue;
                }
                ImpRowInspector.Input input = new ImpRowInspector.Input();
                input.batchNo = at(physicalCells, idx, COL_BATCH);
                input.siteNo = at(physicalCells, idx, COL_SITE);
                input.itemCode = at(physicalCells, idx, COL_ITEM);
                input.qtyText = at(physicalCells, idx, COL_QTY);
                input.sampleType = at(physicalCells, idx, COL_SAMPLE_TYPE);
                input.testAtText = at(physicalCells, idx, COL_TEST_AT);
                input.ruleCode = at(physicalCells, idx, COL_RULE);
                input.validText = at(physicalCells, idx, COL_VALID);
                input.reviewedText = at(physicalCells, idx, COL_REVIEWED);
                // 原始行：按物理列序逐格原样拼接，归档/下钻/差异都只认这串
                StringBuilder raw = new StringBuilder();
                for (int c = 0; c < physicalCells.size(); c++) {
                    if (c > 0) {
                        raw.append(" | ");
                    }
                    raw.append(physicalCells.get(c).trim());
                }
                input.rawLine = raw.toString();
                result.rows.add(input);
            }
            if (result.rows.isEmpty()) {
                result.error = "列名行之下没有任何数据行";
            }
            return result;
        } finally {
            wb.close();
        }
    }

    /**
     * 按钉死的列序导出明细行。{@code rows} 已由调用方排定顺序，本方法不重排。
     * 文档属性时刻与 zip 条目时间全部钉成固定桩——同一批行连导两次落盘字节逐字节一致，
     * 与调用时刻无关（业务语义里的"统计日期/导出时间"只体现在文件名上）。
     */
    public static byte[] write(List<List<String>> rows) throws IOException {
        ByteArrayOutputStream raw = new ByteArrayOutputStream();
        XSSFWorkbook wb = new XSSFWorkbook();
        try {
            // 核内属性也钉固定桩：不许写当前时刻，否则两次导出字节有差
            java.util.Date peg = java.sql.Timestamp.valueOf("2026-01-01 00:00:00");
            wb.getProperties().getCoreProperties().setCreated(java.util.Optional.of(peg));
            wb.getProperties().getCoreProperties().setModified(java.util.Optional.of(peg));
            Sheet sheet = wb.createSheet("检测数据明细");
            Row head = sheet.createRow(0);
            for (int c = 0; c < EXPORT_HEADERS.size(); c++) {
                head.createCell(c).setCellValue(EXPORT_HEADERS.get(c));
            }
            int r = 1;
            for (List<String> line : rows) {
                Row row = sheet.createRow(r++);
                for (int c = 0; c < EXPORT_HEADERS.size(); c++) {
                    String v = c < line.size() && line.get(c) != null ? line.get(c) : "";
                    row.createCell(c).setCellValue(v);
                }
            }
            wb.write(raw);
        } finally {
            wb.close();
        }
        // 重打 zip 包：条目时间钉常量、顺序不变、内容不变——两次导出文件字节级一致
        return normalizeZip(raw.toByteArray());
    }

    /** 重写 xlsx(zip)：每个条目时间钉为固定常量，其余原样搬回 */
    private static byte[] normalizeZip(byte[] xlsxBytes) throws IOException {
        ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(xlsxBytes));
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ZipOutputStream zos = new ZipOutputStream(out);
        ZipEntry e;
        byte[] buf = new byte[4096];
        while ((e = zis.getNextEntry()) != null) {
            ZipEntry ne = new ZipEntry(e.getName());
            // 2026-10-02 00:00:00 固定桩：同内容任何时刻重导，zip 头无差异
            ne.setTime(java.sql.Timestamp.valueOf("2026-01-01 00:00:00").getTime());
            zos.putNextEntry(ne);
            int n;
            while ((n = zis.read(buf)) > 0) {
                zos.write(buf, 0, n);
            }
            zos.closeEntry();
            zis.closeEntry();
        }
        zos.close();
        return out.toByteArray();
    }

    private static String at(List<String> cells, Map<String, Integer> idx, String header) {
        Integer i = idx.get(header);
        if (i == null || i.intValue() >= cells.size()) {
            return "";
        }
        return cells.get(i.intValue()).trim();
    }

    /** 取单元格的「人眼文本」：日期格先取 Date 再过统一时间尺，其余交 DataFormatter */
    private static String cellText(Cell cell, DataFormatter fmt) {
        if (cell == null) {
            return "";
        }
        CellType type = cell.getCellType();
        if (type == CellType.FORMULA) {
            type = cell.getCachedFormulaResultType();
        }
        if (type == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
            java.util.Date d = cell.getDateCellValue();
            return ImpSampleTime.format(d);
        }
        String t = fmt.formatCellValue(cell);
        return t == null ? "" : t;
    }

    private static boolean isAllBlank(List<String> cells) {
        for (String c : cells) {
            if (c != null && !c.trim().isEmpty()) {
                return false;
            }
        }
        return true;
    }
}
