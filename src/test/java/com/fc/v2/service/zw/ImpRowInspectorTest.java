package com.fc.v2.service.zw;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.Date;

import org.junit.jupiter.api.Test;

/**
 * 逐行校验口径：读数（水位）、地点、采样时间、申报规则代号四类硬错当场点名，
 * 全格合格才带定级三件套放回。外部依赖（底档/规则出口）用内存桩。
 */
public class ImpRowInspectorTest {

    private static final String RULE_CODE = "CL-SW-2026";

    /** 底档里只有 ZS00（退役与否不在此层区分，编号存在即对得上） */
    private final ImpRowInspector.SiteDirectory sites = new ImpRowInspector.SiteDirectory() {
        @Override
        public boolean exists(String siteNo) {
            return "ZS00".equals(siteNo);
        }
    };

    /** 检测当天现行线固定 CL-SW-2026；合法读数给超标档，喂进来的类别必须是水源水 */
    private final ImpRowInspector.RuleLookup rules = new ImpRowInspector.RuleLookup() {
        @Override
        public WaterVerdict evaluate(BigDecimal qty, Date testAt, String sampleType) {
            if (!"水源水".equals(sampleType) || testAt == null) {
                return WaterVerdict.noRule(sampleType, testAt);
            }
            return WaterVerdict.graded(WaterGrade.SEVERE, RULE_CODE,
                    new java.util.Date(1767225600000L /* 2026-01-01 */));
        }
    };

    private static ImpRowInspector.Input base() {
        ImpRowInspector.Input in = new ImpRowInspector.Input();
        in.batchNo = "B202609";
        in.siteNo = "ZS00";
        in.itemCode = "CL";
        in.qtyText = "0.71";
        in.sampleType = "水源水";
        in.testAtText = "2026-09-18 09:00:00";
        in.ruleCode = RULE_CODE;
        in.validText = "有效";
        in.reviewedText = "未复核";
        in.rawLine = "B202609 | ZS00 | CL | 0.71 | 水源水 | 2026-09-18 09:00:00 | CL-SW-2026 | 有效 | 未复核";
        return in;
    }

    @Test
    public void cleanRowPassesAndCarriesPinnedVerdict() {
        ImpRowInspector.Check c = ImpRowInspector.inspect(base(), sites, rules);
        assertFalse(c.hardError, c.message);
        assertEquals(0, new BigDecimal("0.71").compareTo(c.qty));
        assertEquals(1, c.validFlag);
        assertEquals(0, c.reviewedFlag);
        assertEquals(WaterGrade.SEVERE, c.verdict.getGrade());
        assertEquals(RULE_CODE, c.verdict.getRuleCode());
        assertEquals("格式合格", c.message);
    }

    @Test
    public void waterLevelMismatchBlocks() {
        for (String bad : new String[]{"", "  ", "abc", "-0.01", "100.01", "999"}) {
            ImpRowInspector.Input in = base();
            in.qtyText = bad;
            ImpRowInspector.Check c = ImpRowInspector.inspect(in, sites, rules);
            assertTrue(c.hardError, "读数「" + bad + "」必须是硬错");
            assertTrue(c.message.contains("水位"), c.message);
        }
    }

    @Test
    public void siteMismatchBlocks() {
        ImpRowInspector.Input empty = base();
        empty.siteNo = "  ";
        assertTrue(ImpRowInspector.inspect(empty, sites, rules).hardError);

        ImpRowInspector.Input unknown = base();
        unknown.siteNo = "ZS404";
        ImpRowInspector.Check c = ImpRowInspector.inspect(unknown, sites, rules);
        assertTrue(c.hardError);
        assertTrue(c.message.contains("查无此号"), c.message);
    }

    @Test
    public void sampleTimeMismatchBlocks() {
        ImpRowInspector.Input in = base();
        in.testAtText = "2026-09-31 25:00:00";
        ImpRowInspector.Check c = ImpRowInspector.inspect(in, sites, rules);
        assertTrue(c.hardError);
        assertTrue(c.message.contains("采样时间"), c.message);
    }

    @Test
    public void declaredRuleMismatchBlocks() {
        ImpRowInspector.Input wrong = base();
        wrong.ruleCode = "CL-SW-2024";
        ImpRowInspector.Check c = ImpRowInspector.inspect(wrong, sites, rules);
        assertTrue(c.hardError);
        assertTrue(c.message.contains("对不上"), c.message);

        ImpRowInspector.Input blank = base();
        blank.ruleCode = "";
        assertTrue(ImpRowInspector.inspect(blank, sites, rules).hardError);
    }

    @Test
    public void noRuleOnTestDayBlocks() {
        ImpRowInspector.Input in = base();
        in.sampleType = "管网末梢";
        in.ruleCode = RULE_CODE;
        ImpRowInspector.Check c = ImpRowInspector.inspect(in, sites, rules);
        assertTrue(c.hardError);
        assertTrue(c.message.contains("无可用"), c.message);
    }

    @Test
    public void flagsAreColumnSpecific() {
        // 「已复核」不是「是否有效」列的词面，不许串义当 1
        assertEquals(Integer.valueOf(1), ImpRowInspector.parseValidFlag("1"));
        assertEquals(Integer.valueOf(0), ImpRowInspector.parseValidFlag("无效"));
        assertEquals(null, ImpRowInspector.parseValidFlag("已复核"));
        assertEquals(Integer.valueOf(1), ImpRowInspector.parseReviewedFlag("已复核"));
        assertEquals(Integer.valueOf(0), ImpRowInspector.parseReviewedFlag("否"));
        assertEquals(null, ImpRowInspector.parseReviewedFlag("有效"));

        ImpRowInspector.Input in = base();
        in.validText = "已复核";
        assertTrue(ImpRowInspector.inspect(in, sites, rules).hardError);
    }

    @Test
    public void sampleTypeAndItemEnforced() {
        ImpRowInspector.Input badType = base();
        badType.sampleType = "海水";
        assertTrue(ImpRowInspector.inspect(badType, sites, rules).hardError);

        ImpRowInspector.Input noItem = base();
        noItem.itemCode = "";
        assertTrue(ImpRowInspector.inspect(noItem, sites, rules).hardError);
    }
}
