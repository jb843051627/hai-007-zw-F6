package com.fc.v2.service.zw;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.fc.v2.model.auto.TZwImpRow;
import com.fc.v2.model.auto.TZwStopSign;

/**
 * 停供签核链口径用例：关口应到枚数、顺着台账点算、角色落名口、理由与近月数据对照。
 */
public class StopChainTest {

    private static Date ts(String s) throws Exception {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse(s);
    }

    /** 顺着台账造一条"动作"落印喂给计数口径 */
    private static TZwStopSign line(String action) {
        TZwStopSign s = new TZwStopSign();
        s.setAction(action);
        return s;
    }

    private static TZwImpRow row(String day, int grade) throws Exception {
        TZwImpRow r = new TZwImpRow();
        r.setSiteNo("ZS00");
        r.setItemCode("CL");
        r.setQty(new BigDecimal("0.62"));
        r.setSampleType("水源水");
        r.setTestAt(ts(day));
        r.setGradeLevel(grade);
        return r;
    }

    @Test
    public void dispatchGateNeedsTwoNamedSignersOthersOne() {
        assertEquals(1, StopChain.needCount(StopChain.NODE_BUSINESS));
        assertEquals(1, StopChain.needCount(StopChain.NODE_WATER));
        assertEquals(2, StopChain.needCount(StopChain.NODE_DISPATCH));
        assertEquals(1, StopChain.needCount(StopChain.NODE_CHIEF));
        assertEquals(Arrays.asList(StopChain.ROLE_DISP_QTY, StopChain.ROLE_DISP_NET),
                StopChain.rolesOf(StopChain.NODE_DISPATCH));
    }

    @Test
    public void twoSignsSameMinuteAreStillTwoRecords() {
        // 即使同一分钟签完，仍是两笔，绝不并成一笔
        List<TZwStopSign> two = Arrays.asList(line(StopChain.ACT_AGREE), line(StopChain.ACT_AGREE));
        assertEquals(2, StopChain.countAgree(two));
        assertTrue(StopChain.gateFull(StopChain.NODE_DISPATCH, two));
    }

    @Test
    public void onlyOneSignerKeepsBillStuckAtDispatchGate() {
        // 只到一位，本口不算满——到不了主管那道口
        List<TZwStopSign> one = new ArrayList<TZwStopSign>();
        one.add(line(StopChain.ACT_AGREE));
        assertEquals(1, StopChain.countAgree(one));
        assertFalse(StopChain.gateFull(StopChain.NODE_DISPATCH, one));
    }

    @Test
    public void nonAgreeRecordsAreNotCounted() {
        // 不同意/撤回/打回笔一律不计入"已落同意名"，代签当没签
        List<TZwStopSign> mixed = Arrays.asList(
                line(StopChain.ACT_AGREE),
                line(StopChain.ACT_DISAGREE),
                line(StopChain.ACT_SENDBACK),
                line(StopChain.ACT_WITHDRAW));
        assertEquals(1, StopChain.countAgree(mixed));
        assertFalse(StopChain.gateFull(StopChain.NODE_DISPATCH, mixed));
    }

    @Test
    public void signerRolesBelongToDispatchGateOnly() {
        // 水量/管网两枚名只在调度评估口作数，不许拿到别的关口替签
        assertTrue(StopChain.roleAllowedAt(StopChain.NODE_DISPATCH, StopChain.ROLE_DISP_QTY));
        assertTrue(StopChain.roleAllowedAt(StopChain.NODE_DISPATCH, StopChain.ROLE_DISP_NET));
        assertFalse(StopChain.roleAllowedAt(StopChain.NODE_WATER, StopChain.ROLE_DISP_QTY));
        assertFalse(StopChain.roleAllowedAt(StopChain.NODE_DISPATCH, StopChain.ROLE_DISP_CHIEF));
    }

    @Test
    public void onlyThreeReasonCodesAreAccepted() {
        assertTrue(StopChain.validReason(StopChain.REASON_DEPLETED));
        assertTrue(StopChain.validReason(StopChain.REASON_OVER_LIMIT));
        assertTrue(StopChain.validReason(StopChain.REASON_REPAIR));
        assertFalse(StopChain.validReason("水源干涸"));
        assertFalse(StopChain.validReason(""));
    }

    @Test
    public void overLimitReasonRequiresConsecutiveOverLimitMonths() throws Exception {
        // 7月、8月相邻两月都超标：连续超标理由成立
        List<TZwImpRow> consecutive = Arrays.asList(
                row("2026-07-18 09:00:00", 4),
                row("2026-08-18 09:00:00", 3));
        assertTrue(StopChain.reasonMatchesData(StopChain.REASON_OVER_LIMIT, consecutive));

        // 只有一个月超标：写"连续超标"属理由写错，不通过
        List<TZwImpRow> single = Arrays.asList(
                row("2026-07-18 09:00:00", 3),
                row("2026-08-18 09:00:00", 2));
        assertFalse(StopChain.reasonMatchesData(StopChain.REASON_OVER_LIMIT, single));
    }

    @Test
    public void depletedReasonWithConsecutiveOverLimitDataIsMismatch() throws Exception {
        // 明明连续超标却写枯竭/检修：理由与数据对不上，不通过
        List<TZwImpRow> consecutive = Arrays.asList(
                row("2026-08-18 09:00:00", 3),
                row("2026-09-18 09:00:00", 4));
        assertFalse(StopChain.reasonMatchesData(StopChain.REASON_DEPLETED, consecutive));
        assertFalse(StopChain.reasonMatchesData(StopChain.REASON_REPAIR, consecutive));

        // 数据不构成连续超标反证时，枯竭理由予以通过
        List<TZwImpRow> clean = Arrays.asList(
                row("2026-08-18 09:00:00", 1),
                row("2026-09-18 09:00:00", 1));
        assertTrue(StopChain.reasonMatchesData(StopChain.REASON_DEPLETED, clean));
    }

    @Test
    public void monthsWithGapAreNotConsecutive() throws Exception {
        // 6月与8月中间隔着7月，不算连续
        List<TZwImpRow> gapped = Arrays.asList(
                row("2026-06-18 09:00:00", 3),
                row("2026-08-18 09:00:00", 3));
        assertFalse(StopChain.hasConsecutiveOverLimitMonths(gapped));
    }

    @Test
    public void decemberToJanuaryAcrossYearIsConsecutive() throws Exception {
        List<TZwImpRow> crossYear = Arrays.asList(
                row("2025-12-20 09:00:00", 3),
                row("2026-01-20 09:00:00", 4));
        assertTrue(StopChain.hasConsecutiveOverLimitMonths(crossYear));
    }
}
