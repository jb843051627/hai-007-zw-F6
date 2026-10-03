package com.fc.v2.service.zw;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 本期与上期差异按<b>同一个批次口径</b>比：两期各以 {@code batch_no} 归堆，
 * 同批次内按原表行次 {@code row_no} 对齐，差异必须指出是哪几行、哪条原始行对不上。
 *
 * <p>纯领域、无库——喂进来的是已落明细台账的行（{@link Line} 只要批次号、行次与原始行原文），
 * 不另查报表：报表是照明细表生成的，差异也只从原始行 {@code rawLine} 点算。
 *
 * <p>三类差异分得清：
 * <ul>
 *   <li>{@link Kind#ONLY_CURRENT}：本期有、上期没有的批次（整批新到）；</li>
 *   <li>{@link Kind#ONLY_PREVIOUS}：上期有、本期没有的批次（本期缺席）；</li>
 *   <li>{@link Kind#CHANGED}：两期同批次，但逐行比对有对不上的行（点名行次与两边原文）。</li>
 * </ul>
 *
 * @author fuce
 * @date 2026-10-02
 */
public final class BatchDiffer {

    public enum Kind {
        /** 本期新到批次 */
        ONLY_CURRENT,
        /** 本期缺席批次 */
        ONLY_PREVIOUS,
        /** 同批次原始行有对不上 */
        CHANGED
    }

    /** 比对喂入行：明细台账的最小投影 */
    public static class Line {
        public final String batchNo;
        public final int rowNo;
        public final String rawLine;

        public Line(String batchNo, int rowNo, String rawLine) {
            this.batchNo = batchNo;
            this.rowNo = rowNo;
            this.rawLine = rawLine;
        }
    }

    /** 一对对不上的行（一边可能为 null＝那一期缺此行） */
    public static class RowMismatch {
        public final int rowNo;
        public final String currentRaw;
        public final String previousRaw;

        public RowMismatch(int rowNo, String currentRaw, String previousRaw) {
            this.rowNo = rowNo;
            this.currentRaw = currentRaw;
            this.previousRaw = previousRaw;
        }
    }

    /** 一个批次的差异结论 */
    public static class BatchDiff {
        public final String batchNo;
        public final Kind kind;
        public final List<RowMismatch> mismatches;

        BatchDiff(String batchNo, Kind kind, List<RowMismatch> mismatches) {
            this.batchNo = batchNo;
            this.kind = kind;
            this.mismatches = mismatches;
        }
    }

    private BatchDiffer() {
    }

    /**
     * 按批次口径比两期。返回顺序按批次号升序，确定性——同一批数据比两次结论逐字一致。
     * 没有差异（同批次且每行原文一致）的批次不出现在结果里。
     */
    public static List<BatchDiff> diff(List<Line> current, List<Line> previous) {
        Map<String, LinkedHashMap<Integer, String>> cur = group(current);
        Map<String, LinkedHashMap<Integer, String>> prev = group(previous);

        List<String> allKeys = new ArrayList<String>();
        for (String k : cur.keySet()) {
            if (!allKeys.contains(k)) {
                allKeys.add(k);
            }
        }
        for (String k : prev.keySet()) {
            if (!allKeys.contains(k)) {
                allKeys.add(k);
            }
        }
        Collections.sort(allKeys);

        List<BatchDiff> out = new ArrayList<BatchDiff>();
        for (String batchNo : allKeys) {
            LinkedHashMap<Integer, String> c = cur.get(batchNo);
            LinkedHashMap<Integer, String> p = prev.get(batchNo);
            if (c != null && p == null) {
                out.add(new BatchDiff(batchNo, Kind.ONLY_CURRENT, Collections.<RowMismatch>emptyList()));
                continue;
            }
            if (c == null && p != null) {
                out.add(new BatchDiff(batchNo, Kind.ONLY_PREVIOUS, Collections.<RowMismatch>emptyList()));
                continue;
            }
            List<RowMismatch> mismatches = new ArrayList<RowMismatch>();
            List<Integer> rowKeys = new ArrayList<Integer>();
            for (Integer rn : c.keySet()) {
                if (!rowKeys.contains(rn)) {
                    rowKeys.add(rn);
                }
            }
            for (Integer rn : p.keySet()) {
                if (!rowKeys.contains(rn)) {
                    rowKeys.add(rn);
                }
            }
            Collections.sort(rowKeys);
            for (Integer rn : rowKeys) {
                String cr = c.get(rn);
                String pr = p.get(rn);
                if (!eq(cr, pr)) {
                    // 缺行侧归一为空串（不是 null）：页面与导出里“那一期缺此行”有准话
                    mismatches.add(new RowMismatch(rn.intValue(), cr == null ? "" : cr, pr == null ? "" : pr));
                }
            }
            if (!mismatches.isEmpty()) {
                out.add(new BatchDiff(batchNo, Kind.CHANGED, mismatches));
            }
        }
        return out;
    }

    /** 按批次号归堆，批内按行次排好（同批次号多行时顺序固定） */
    private static Map<String, LinkedHashMap<Integer, String>> group(List<Line> lines) {
        Map<String, LinkedHashMap<Integer, String>> map =
                new LinkedHashMap<String, LinkedHashMap<Integer, String>>();
        if (lines == null) {
            return map;
        }
        for (Line l : lines) {
            if (l == null || l.batchNo == null) {
                continue;
            }
            LinkedHashMap<Integer, String> rows = map.get(l.batchNo);
            if (rows == null) {
                rows = new LinkedHashMap<Integer, String>();
                map.put(l.batchNo, rows);
            }
            rows.put(Integer.valueOf(l.rowNo), l.rawLine == null ? "" : l.rawLine);
        }
        return map;
    }

    private static boolean eq(String a, String b) {
        return a == null ? b == null : a.equals(b);
    }
}
