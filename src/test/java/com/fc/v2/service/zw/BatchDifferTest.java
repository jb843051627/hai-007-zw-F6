package com.fc.v2.service.zw;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * 本期/上期同批次口径差异：批次归堆、行次对齐、点名到原始行；无差异不出声。
 */
public class BatchDifferTest {

    private static BatchDiffer.Line l(String batch, int row, String raw) {
        return new BatchDiffer.Line(batch, row, raw);
    }

    @Test
    public void sameBatchSameRowsIsSilent() {
        List<BatchDiffer.Line> cur = Arrays.asList(
                l("B1", 1, "a1"), l("B1", 2, "a2"));
        List<BatchDiffer.Line> prev = Arrays.asList(
                l("B1", 1, "a1"), l("B1", 2, "a2"));
        assertTrue(BatchDiffer.diff(cur, prev).isEmpty());
    }

    @Test
    public void newAndMissingBatchesAreClassified() {
        List<BatchDiffer.BatchDiff> d = BatchDiffer.diff(
                Arrays.asList(l("B1", 1, "a"), l("B2", 1, "b")),
                Collections.singletonList(l("B1", 1, "a")));
        // 按批次号升序：B1 无差异不出现，只剩 B2 本期新到
        assertEquals(1, d.size());
        assertEquals("B2", d.get(0).batchNo);
        assertEquals(BatchDiffer.Kind.ONLY_CURRENT, d.get(0).kind);

        List<BatchDiffer.BatchDiff> d2 = BatchDiffer.diff(
                Collections.singletonList(l("B1", 1, "a")),
                Arrays.asList(l("B1", 1, "a"), l("B9", 1, "z")));
        assertEquals(1, d2.size());
        assertEquals(BatchDiffer.Kind.ONLY_PREVIOUS, d2.get(0).kind);
    }

    @Test
    public void changedBatchPointsAtRowsAndBothRawLines() {
        List<BatchDiffer.BatchDiff> d = BatchDiffer.diff(
                Arrays.asList(l("B1", 1, "a1"), l("B1", 2, "a2-CHANGED"), l("B1", 3, "a3")),
                Arrays.asList(l("B1", 1, "a1"), l("B1", 2, "a2"), l("B1", 4, "a4")));
        assertEquals(1, d.size());
        assertEquals(BatchDiffer.Kind.CHANGED, d.get(0).kind);
        // 第2行内容对不上、第3行本期有上期缺、第4行上期有本期缺
        assertEquals(3, d.get(0).mismatches.size());
        assertEquals(2, d.get(0).mismatches.get(0).rowNo);
        assertEquals("a2-CHANGED", d.get(0).mismatches.get(0).currentRaw);
        assertEquals("a2", d.get(0).mismatches.get(0).previousRaw);
        assertEquals(3, d.get(0).mismatches.get(1).rowNo);
        assertEquals("a3", d.get(0).mismatches.get(1).currentRaw);
        assertEquals("", d.get(0).mismatches.get(1).previousRaw);
        assertEquals(4, d.get(0).mismatches.get(2).rowNo);
        assertEquals("", d.get(0).mismatches.get(2).currentRaw);
        assertEquals("a4", d.get(0).mismatches.get(2).previousRaw);
    }

    @Test
    public void resultIsDeterministicAcrossRuns() {
        List<BatchDiffer.Line> cur = Arrays.asList(l("B2", 1, "x"), l("B1", 1, "y"));
        List<BatchDiffer.Line> prev = Collections.singletonList(l("B3", 1, "z"));
        List<BatchDiffer.BatchDiff> first = BatchDiffer.diff(cur, prev);
        List<BatchDiffer.BatchDiff> second = BatchDiffer.diff(cur, prev);
        assertEquals(first.size(), second.size());
        for (int i = 0; i < first.size(); i++) {
            assertEquals(first.get(i).batchNo, second.get(i).batchNo);
            assertEquals(first.get(i).kind, second.get(i).kind);
        }
        // 批次号升序，不是插入序
        assertEquals("B1", first.get(0).batchNo);
        assertEquals("B2", first.get(1).batchNo);
        assertEquals("B3", first.get(2).batchNo);
    }
}
