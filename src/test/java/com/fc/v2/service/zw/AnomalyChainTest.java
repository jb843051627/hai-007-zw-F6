package com.fc.v2.service.zw;

import java.math.BigDecimal;
import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 异常链纯领域口径单测：档位处置、超额计算、GMT+8 归日、步次/环节名目。
 *
 * @author fuce
 * @date 2026-10-03
 */
public class AnomalyChainTest {

    private static BigDecimal v(String s) {
        return new BigDecimal(s);
    }

    @Test
    public void gradeDrivesActions() {
        assertFalse(AnomalyChain.needRetest(WaterGrade.WATCH));
        assertTrue(AnomalyChain.needRetest(WaterGrade.OVER));
        assertTrue(AnomalyChain.needRetest(WaterGrade.SEVERE));
        assertFalse(AnomalyChain.needStop(WaterGrade.OVER));
        assertTrue(AnomalyChain.needStop(WaterGrade.SEVERE));
    }

    @Test
    public void overAmountUsesCrossedThreshold() {
        // 严重档：0.71 越 th3 0.50，超 0.21
        BigDecimal[] over = AnomalyChain.overAmount(v("0.71"), WaterGrade.SEVERE,
                v("0.20"), v("0.30"), v("0.50"));
        assertEquals(0, over[0].compareTo(v("0.50")));
        assertEquals(0, over[1].compareTo(v("0.21")));

        // 超标档：0.35 越 th2 0.30
        over = AnomalyChain.overAmount(v("0.35"), WaterGrade.OVER,
                v("0.20"), v("0.30"), v("0.50"));
        assertEquals(0, over[0].compareTo(v("0.30")));
        assertEquals(0, over[1].compareTo(v("0.05")));

        // 关注档界值空缺（th1=null，如旧线严重档留空的反向情形）：给不出超额
        assertNull(AnomalyChain.overAmount(v("0.9"), WaterGrade.WATCH, null, null, null));

        // 严重档 th3 留空（空档不设）：钉在在设的最高档 th2
        over = AnomalyChain.overAmount(v("0.9"), WaterGrade.SEVERE, v("0.3"), v("0.5"), null);
        assertEquals(0, over[0].compareTo(v("0.5")));

        // 合格档不算超额
        assertNull(AnomalyChain.overAmount(v("0.1"), WaterGrade.NORMAL,
                v("0.2"), v("0.3"), v("0.5")));
    }

    @Test
    public void judgeTextMentionsLineThresholdsAndExcess() {
        String text = AnomalyChain.judgeText("CL-SW-2026", date(2026, 0, 1, 9, 0),
                "水源水", v("0.71"), WaterGrade.SEVERE, v("0.20"), v("0.30"), v("0.50"));
        assertTrue(text.contains("CL-SW-2026"));
        assertTrue(text.contains("严重超标"));
        assertTrue(text.contains("0.50"));
        assertTrue(text.contains("0.21"));
        assertTrue(text.contains("停水"));
    }

    @Test
    public void truncateToDayUsesGmtPlus8() {
        // GMT+8 的 2026-09-18 00:30 == UTC 的 2026-09-17 16:30：归日必须落在 18 日
        Calendar c = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        c.set(2026, Calendar.SEPTEMBER, 17, 16, 30, 0);
        c.set(Calendar.MILLISECOND, 0);
        Date day = AnomalyChain.truncateToDay(c.getTime());
        Calendar out = Calendar.getInstance(TimeZone.getTimeZone("GMT+8"));
        out.setTime(day);
        assertEquals(2026, out.get(Calendar.YEAR));
        assertEquals(Calendar.SEPTEMBER, out.get(Calendar.MONTH));
        assertEquals(18, out.get(Calendar.DAY_OF_MONTH));
        assertEquals(0, out.get(Calendar.HOUR_OF_DAY));
        assertEquals("20260918", AnomalyChain.dayKey(c.getTime()));
        assertEquals(2026, AnomalyChain.yearOf(c.getTime()));
    }

    @Test
    public void followPeriodAdvanceIsNaturalDays() {
        Date base = date(2026, 10, 3, 10, 0);
        Calendar c = Calendar.getInstance(TimeZone.getTimeZone("GMT+8"));
        c.setTime(AnomalyChain.plusDays(base, 2));
        assertEquals(5, c.get(Calendar.DAY_OF_MONTH));
        assertEquals(10, c.get(Calendar.HOUR_OF_DAY));
    }

    @Test
    public void namesAreStable() {
        assertEquals("采样复测", AnomalyChain.nodeName(AnomalyChain.NODE_SAMPLE));
        assertEquals("主管封卷", AnomalyChain.nodeName(AnomalyChain.NODE_CHIEF_SEAL));
        assertEquals("已反映供水户", AnomalyChain.stepName(AnomalyChain.STEP_REFLECTED));
        assertEquals("漏盯", AnomalyChain.followStatusText(AnomalyChain.FOLLOW_MISSED));
        assertEquals("否决", AnomalyChain.conclusionText(AnomalyChain.CON_FAIL));
    }

    private static Date date(int y, int m0, int d, int hh, int mm) {
        Calendar c = Calendar.getInstance(TimeZone.getTimeZone("GMT+8"));
        c.set(y, m0, d, hh, mm, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTime();
    }
}
