package com.fc.v2.service.zw;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

/**
 * Excel 读写口径：列名认列、原始行逐格原样保留、中文不乱码、同数据两次导出字节一致。
 */
public class ImpExcelTest {

    /** 造一份九列齐备、含中文与斜杠时间的 xlsx，列序故意与要求列序不同（按列名认列） */
    private static byte[] sampleXlsx(boolean swapOrder, boolean missingHeader) throws Exception {
        XSSFWorkbook wb = new XSSFWorkbook();
        XSSFSheet sheet = wb.createSheet("当月");
        List<String> headers = new ArrayList<String>(ImpExcel.REQUIRED_HEADERS);
        if (swapOrder) {
            // 把「点位编号」换到「采样批次」前面，验证按列名定位而非按下标
            String a = headers.get(0);
            headers.set(0, headers.get(1));
            headers.set(1, a);
        }
        if (missingHeader) {
            headers.remove("规则代号");
        }
        XSSFRow head = sheet.createRow(0);
        for (int c = 0; c < headers.size(); c++) {
            head.createCell(c).setCellValue(headers.get(c));
        }
        XSSFRow r = sheet.createRow(1);
        for (int c = 0; c < headers.size(); c++) {
            String h = headers.get(c);
            r.createCell(c).setCellValue(sampleValue(h));
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        wb.write(out);
        wb.close();
        return out.toByteArray();
    }

    private static String sampleValue(String header) {
        if (ImpExcel.COL_BATCH.equals(header)) {
            return "B202609";
        }
        if (ImpExcel.COL_SITE.equals(header)) {
            return "ZS00城北水源地";
        }
        if (ImpExcel.COL_ITEM.equals(header)) {
            return "余氯CL";
        }
        if (ImpExcel.COL_QTY.equals(header)) {
            return "0.71";
        }
        if (ImpExcel.COL_SAMPLE_TYPE.equals(header)) {
            return "水源水";
        }
        if (ImpExcel.COL_TEST_AT.equals(header)) {
            return "2026/9/18 9:00";
        }
        if (ImpExcel.COL_RULE.equals(header)) {
            return "CL-SW-2026";
        }
        if (ImpExcel.COL_VALID.equals(header)) {
            return "有效";
        }
        if (ImpExcel.COL_REVIEWED.equals(header)) {
            return "未复核";
        }
        return "";
    }

    @Test
    public void readsByNameAndKeepsRawLine() throws Exception {
        ImpExcel.ParsedFile pf = ImpExcel.read(new ByteArrayInputStream(sampleXlsx(true, false)));
        assertNull(pf.error, pf.error);
        assertEquals(1, pf.rows.size());
        ImpRowInspector.Input in = pf.rows.get(0);
        // 列序换过仍按名取对
        assertEquals("B202609", in.batchNo);
        assertEquals("ZS00城北水源地", in.siteNo);
        assertEquals("余氯CL", in.itemCode);
        assertEquals("0.71", in.qtyText);
        assertEquals("水源水", in.sampleType);
        assertEquals("2026/9/18 9:00", in.testAtText);
        assertEquals("CL-SW-2026", in.ruleCode);
        // 原始行保留每格原文（中文原样，不出现乱码），按物理列序拼
        assertNotNull(in.rawLine);
        assertTrue(in.rawLine.contains("ZS00城北水源地"), in.rawLine);
        assertTrue(in.rawLine.contains("2026/9/18 9:00"), in.rawLine);
        // 统一尺解析斜杠时间不掉链子
        assertEquals("2026-09-18 09:00:00", ImpSampleTime.format(ImpSampleTime.parse(in.testAtText)));
    }

    @Test
    public void missingHeaderIsFileError() throws Exception {
        ImpExcel.ParsedFile pf = ImpExcel.read(new ByteArrayInputStream(sampleXlsx(false, true)));
        assertNotNull(pf.error);
        assertTrue(pf.error.contains("规则代号"), pf.error);
        assertTrue(pf.rows.isEmpty());
    }

    @Test
    public void garbageFileIsFileErrorNotException() throws Exception {
        ImpExcel.ParsedFile pf = ImpExcel.read(new ByteArrayInputStream("这不是 Excel".getBytes("UTF-8")));
        assertNotNull(pf.error);
        assertTrue(pf.rows.isEmpty());
    }

    @Test
    public void twoExportsOfSameRowsAreByteIdentical() throws Exception {
        List<List<String>> rows = Arrays.asList(
                cellRow("B202609", "ZS00城北水源地", "0.71", "水源水", "严重超标"),
                cellRow("B202609", "ZS01城南水厂", "0.20", "出厂水", "关注"));
        byte[] first = ImpExcel.write(rows);
        byte[] second = ImpExcel.write(rows);
        // 同一批行、同一导出时间（调用时刻不参与）连导两次：字节级一致
        assertArrayEquals(first, second);
        // 导出文件本身能被 POI 原样读回，中文不乱码（导出表头是 24 列台账格式，不按导入模板九列校验）
        assertTrue(exportContains(first, "ZS00城北水源地"), "导出读回须含中文点位");
        assertTrue(exportContains(first, "严重超标"), "导出读回须含中文等级");
    }

    @Test
    public void exportHeadersCarryEveryLedgerField() {
        // 导出与复核逐项落到一行：24 列须齐备
        List<String> h = ImpExcel.EXPORT_HEADERS;
        for (String required : new String[]{"原始行", "校验结论", "校验说明", "核实结论",
                "核实原因", "核实人", "核实时间", "归档标识"}) {
            assertTrue(h.contains(required), "导出缺少列：" + required);
        }
    }

    /** 读回导出文件，确认某串中文出现在任意数据格中 */
    private static boolean exportContains(byte[] xlsx, String text) throws Exception {
        XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(xlsx));
        try {
            XSSFSheet sheet = wb.getSheetAt(0);
            for (int r = 1; r <= sheet.getLastRowNum(); r++) {
                XSSFRow row = sheet.getRow(r);
                if (row == null) {
                    continue;
                }
                for (int c = 0; c < row.getLastCellNum(); c++) {
                    if (text.equals(row.getCell(c).getStringCellValue())) {
                        return true;
                    }
                }
            }
            return false;
        } finally {
            wb.close();
        }
    }

    private static List<String> cellRow(String batch, String site, String qty, String type, String grade) {
        // 按 EXPORT_HEADERS 24 列造一行，关心的几列给值，其余空串
        List<String> line = new ArrayList<String>();
        for (int i = 0; i < ImpExcel.EXPORT_HEADERS.size(); i++) {
            line.add("");
        }
        line.set(0, batch);
        line.set(2, site);
        line.set(4, qty);
        line.set(5, type);
        line.set(6, "2026-09-18 09:00:00");
        line.set(10, grade);
        return line;
    }
}
