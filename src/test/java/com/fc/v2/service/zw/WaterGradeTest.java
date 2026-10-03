package com.fc.v2.service.zw;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.Date;

import org.junit.jupiter.api.Test;

/**
 * 水质分档裁决内核：界值相同算高档、空界值下推不硬拔、并列取代号后者、
 * 交接日半开窗口、读数合法量域——这些口径全公司只此一处。
 */
public class WaterGradeTest {

    private static BigDecimal q(String s) {
        return new BigDecimal(s);
    }

    private static Date ts(String s) throws Exception {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse(s);
    }

    // ---------- 定级：等于界值算高档 ----------

    @Test
    public void equalThresholdTakesHigherGrade() {
        // 新线 0.20/0.40/0.60：恰等于关注上限即关注档，不得留在合格
        assertEquals(WaterGrade.NORMAL, WaterGrade.level(q("0.19"), q("0.20"), q("0.40"), q("0.60")));
        assertEquals(WaterGrade.WATCH, WaterGrade.level(q("0.20"), q("0.20"), q("0.40"), q("0.60")));
        assertEquals(WaterGrade.OVER, WaterGrade.level(q("0.40"), q("0.20"), q("0.40"), q("0.60")));
        assertEquals(WaterGrade.SEVERE, WaterGrade.level(q("0.60"), q("0.20"), q("0.40"), q("0.60")));
        // 越过上一档但未及下一档
        assertEquals(WaterGrade.OVER, WaterGrade.level(q("0.59"), q("0.20"), q("0.40"), q("0.60")));
    }

    @Test
    public void lastYearModerateCeilingFillsWatchNotNormal() {
        // 旧事重演：出厂水余氯读数恰值上年中度(关注)上限 0.30，旧线 0.30/0.50/空 —— 必须落关注档
        assertEquals(WaterGrade.WATCH, WaterGrade.level(q("0.30"), q("0.30"), q("0.50"), null));
    }

    // ---------- 空界值：空即不设此档，随低档继续下推，不报错、不拔高 ----------

    @Test
    public void missingSevereThresholdNeverGradesSevere() {
        // 早期入册严重超标一格留空：再大的合法读数也只钉在超标档
        assertEquals(WaterGrade.NORMAL, WaterGrade.level(q("0.10"), q("0.30"), q("0.50"), null));
        assertEquals(WaterGrade.WATCH, WaterGrade.level(q("0.31"), q("0.30"), q("0.50"), null));
        assertEquals(WaterGrade.OVER, WaterGrade.level(q("0.50"), q("0.30"), q("0.50"), null));
        assertEquals(WaterGrade.OVER, WaterGrade.level(q("99.99"), q("0.30"), q("0.50"), null));
    }

    @Test
    public void missingMiddleThresholdPushesStraightThrough() {
        // th2 空：关注之后直接看严重档，中间不断档
        assertEquals(WaterGrade.WATCH, WaterGrade.level(q("0.25"), q("0.20"), null, q("0.60")));
        assertEquals(WaterGrade.SEVERE, WaterGrade.level(q("0.60"), q("0.20"), null, q("0.60")));
    }

    @Test
    public void onlyOneThresholdSetStillBounded() {
        // 只设关注档：越过即钉在关注档，不给超标/严重
        assertEquals(WaterGrade.NORMAL, WaterGrade.level(q("0.10"), q("0.30"), null, null));
        assertEquals(WaterGrade.WATCH, WaterGrade.level(q("1.00"), q("0.30"), null, null));
        // 三档全空：给不出档位，不硬判
        assertEquals(WaterGrade.NONE, WaterGrade.level(q("0.50"), null, null, null));
    }

    // ---------- 合理量域：负数 / 空值 / 超大怪值不参评 ----------

    @Test
    public void outOfRangeReadingsDoNotGrade() {
        assertFalse(WaterGrade.validInput(null));
        assertFalse(WaterGrade.validInput(q("-0.01")));
        assertTrue(WaterGrade.validInput(q("0")));
        assertTrue(WaterGrade.validInput(q("100")));
        assertFalse(WaterGrade.validInput(q("100.01")));
        assertEquals(WaterGrade.NONE, WaterGrade.level(q("-1"), q("0.30"), q("0.50"), null));
        assertEquals(WaterGrade.NONE, WaterGrade.level(null, q("0.30"), q("0.50"), null));
        assertEquals(WaterGrade.NONE, WaterGrade.level(q("999"), q("0.30"), q("0.50"), null));
    }

    // ---------- 交接日：[起,止) 半开，旧线管到进程、新线从结局起算 ----------

    @Test
    public void handoverDayHasSingleCaliber() throws Exception {
        Date oldStart = ts("2024-01-01 00:00:00");
        Date handover = ts("2026-01-01 00:00:00");
        // 旧线管到进程：交接前最后一刻仍在窗口
        assertTrue(WaterGrade.effective(ts("2025-12-31 23:59:59"), oldStart, handover));
        // 恰交接日 00:00:00 旧线即让位
        assertFalse(WaterGrade.effective(ts("2026-01-01 00:00:00"), oldStart, handover));
        // 新线从结局起算：交接日零点即生效
        assertTrue(WaterGrade.effective(ts("2026-01-01 00:00:00"), handover, null));
        // 启用之日含
        assertTrue(WaterGrade.effective(ts("2024-01-01 00:00:00"), oldStart, handover));
        assertFalse(WaterGrade.effective(ts("2023-12-31 23:59:59"), oldStart, handover));
        // 时刻缺失不可判
        assertFalse(WaterGrade.effective(null, oldStart, handover));
    }

    // ---------- 并线：顺位高者胜；顺位相同代号序列排后者胜（四季一致） ----------

    @Test
    public void priorityThenCodeDescTieBreakIsStable() {
        assertTrue(WaterGrade.precedes(20, "CL-A", 10, "CL-Z"));
        assertFalse(WaterGrade.precedes(10, "CL-Z", 20, "CL-A"));
        // 同分：代号序列排后者胜
        assertTrue(WaterGrade.precedes(10, "CL-FW-2026", 10, "CL-FW-2024"));
        assertFalse(WaterGrade.precedes(10, "CL-FW-2024", 10, "CL-FW-2026"));
        // null 顺位按 0 兜底，null 代号按空串，不许抛异常
        assertTrue(WaterGrade.precedes(1, null, 0, "CL-A"));
        assertFalse(WaterGrade.precedes(0, null, 0, "CL-A"));
    }

    // ---------- 回值：三种结局分明 ----------

    @Test
    public void verdictCarriesCodeAndEffDateAndStaysBlankWhenNoRule() throws Exception {
        Date eff = ts("2026-01-01 00:00:00");
        WaterVerdict g = WaterVerdict.graded(WaterGrade.WATCH, "CL-FW-2026", eff);
        assertTrue(g.isGraded());
        assertEquals(WaterVerdict.Kind.GRADED, g.getKind());
        assertEquals(WaterGrade.WATCH, g.getGrade());
        assertEquals("CL-FW-2026", g.getRuleCode());
        assertEquals(eff, g.getRuleEffDate());
        assertEquals("关注", g.gradeText());

        WaterVerdict no = WaterVerdict.noRule(WaterGrade.TYPE_FINISHED, ts("2025-06-01 10:00:00"));
        assertEquals(WaterVerdict.Kind.NO_RULE, no.getKind());
        assertFalse(no.isGraded());
        assertEquals("无可用标准", no.gradeText());
        assertNull(no.getRuleCode());
        assertNull(no.getRuleEffDate());
        assertTrue(no.getMessage().contains("无可用标准"));

        WaterVerdict bad = WaterVerdict.invalid(q("-0.5"));
        assertEquals(WaterVerdict.Kind.INVALID, bad.getKind());
        assertNull(bad.getRuleCode());
        assertTrue(bad.getMessage().contains("退回化验员重录"));
    }

    @Test
    public void actionByGradeFollowsLabProtocol() {
        assertEquals("立即上报复核", WaterGrade.actionText(WaterGrade.SEVERE));
        assertEquals("安排复测", WaterGrade.actionText(WaterGrade.OVER));
        assertEquals("并入下次检测", WaterGrade.actionText(WaterGrade.WATCH));
        assertEquals("", WaterGrade.actionText(WaterGrade.NORMAL));
    }
}
