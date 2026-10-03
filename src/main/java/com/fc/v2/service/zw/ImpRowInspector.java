package com.fc.v2.service.zw;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;

/**
 * 一条导入行的逐行校验器（纯领域、无 Spring、无库——可直接单测）。
 *
 * <p>入口喂进来的是 Excel 逐格抠出的原始字符串 {@link Input}（连同原样拼接行），
 * 出口给一个 {@link Check}：要么全格合格（解析好的读数/时间/旗标+定级回值一并带出），
 * 要么一条硬错（点名哪一项对不上）。下列四项任一不过都是硬错，调用方必须整批拦下：
 * <ul>
 *   <li><b>水位（读数）</b>：空、非数、负数或超出 {@link WaterGrade#validInput} 合理量域；</li>
 *   <li><b>地点</b>：点位代号空，或底档里查无此号（退役点仍算对得上底档，由业务另行处置）；</li>
 *   <li><b>采样时间</b>：统一过 {@link ImpSampleTime} 这把尺，解析不掉即对不上；</li>
 *   <li><b>规则代号</b>：申报代号必须与检测当天该类别唯一定级出口选中的现行线逐字一致——
 *       当天无可用标准（NO_RULE）时申报代号无从对上，同样硬错，不许拿临期线凑合。</li>
 * </ul>
 * 样本类别必须落在三类枚举内，事项（读数明细项）不得空，有效/复核旗标只认规定词面。
 *
 * @author fuce
 * @date 2026-10-02
 */
public final class ImpRowInspector {

    /** 合格行校验结论（与 t_zw_imp_row.check_status 同码） */
    public static final int CHECK_OK = 1;
    /** 异常行校验结论（硬错） */
    public static final int CHECK_HARD = 2;

    /** 是否有效/已复核允许的词面（1/0、是/否 通用，中文名目按列各认各的，不串义） */
    private static final List<String> TOKENS_YES =
            Collections.unmodifiableList(Arrays.asList("1", "是", "Y", "TRUE"));
    private static final List<String> TOKENS_NO =
            Collections.unmodifiableList(Arrays.asList("0", "否", "N", "FALSE"));
    /** 「是否有效」列专认词面 */
    private static final List<String> VALID_YES_WORDS =
            Collections.unmodifiableList(Arrays.asList("有效"));
    private static final List<String> VALID_NO_WORDS =
            Collections.unmodifiableList(Arrays.asList("无效"));
    /** 「是否已复核」列专认词面 */
    private static final List<String> REVIEWED_YES_WORDS =
            Collections.unmodifiableList(Arrays.asList("已复核"));
    private static final List<String> REVIEWED_NO_WORDS =
            Collections.unmodifiableList(Arrays.asList("未复核"));

    private ImpRowInspector() {
    }

    /**
     * 外部依赖（测试直接给内存实现）：点位底档查号、检测当天定级出口。
     */
    public interface SiteDirectory {
        /** 底档里是否有这个代号（含已退役——退役是业务状态，不是编号对不上） */
        boolean exists(String siteNo);
    }

    public interface RuleLookup {
        /** 检测当天该类别的唯一定级出口（与批量手工录入同一个出口，不许旁路另算） */
        WaterVerdict evaluate(BigDecimal qty, Date testAt, String sampleType);
    }

    /** Excel 逐行原始输入（字符串原样，未做任何折算） */
    public static class Input {
        public String batchNo;
        public String siteNo;
        public String itemCode;
        public String qtyText;
        public String sampleType;
        public String testAtText;
        public String ruleCode;
        public String validText;
        public String reviewedText;
        public String rawLine;
    }

    /** 校验回值 */
    public static class Check {
        public final boolean hardError;
        public final String message;
        /** 合格时钉好的各字段（硬错时部分为 null） */
        public BigDecimal qty;
        public Date testAt;
        public int validFlag;
        public int reviewedFlag;
        public WaterVerdict verdict;

        private Check(boolean hardError, String message) {
            this.hardError = hardError;
            this.message = message;
        }

        public static Check ok(BigDecimal qty, Date testAt, int validFlag, int reviewedFlag, WaterVerdict v) {
            Check c = new Check(false, "格式合格");
            c.qty = qty;
            c.testAt = testAt;
            c.validFlag = validFlag;
            c.reviewedFlag = reviewedFlag;
            c.verdict = v;
            return c;
        }

        public static Check hard(String message) {
            return new Check(true, message);
        }
    }

    /**
     * 校验一行。顺序即点名顺序：读数→点位→类别→事项→时间→旗标→规则代号，
     * 任一硬错当场返回（一条行只点第一处对不上的，不堆叠，免得处理人不知道先改哪）。
     */
    public static Check inspect(Input in, SiteDirectory sites, RuleLookup rules) {
        // 1) 水位（读数）：空、非数、越合理量域都是对不上
        BigDecimal qty = parseQty(in.qtyText);
        if (qty == null) {
            return Check.hard("读数（水位）" + blank(in.qtyText) + "不是合法数值或超出合理量域 0~"
                    + WaterGrade.INPUT_MAX.toPlainString());
        }
        if (!WaterGrade.validInput(qty)) {
            return Check.hard("读数（水位）「" + in.qtyText.trim() + "」超出合理量域 0~"
                    + WaterGrade.INPUT_MAX.toPlainString());
        }
        // 2) 地点：空号或底档查无
        String siteNo = trim(in.siteNo);
        if (siteNo.isEmpty()) {
            return Check.hard("点位代号为空，地点对不上");
        }
        if (!sites.exists(siteNo)) {
            return Check.hard("点位代号「" + siteNo + "」在底档中查无此号，地点对不上");
        }
        // 3) 样本类别必须落在三类枚举
        String sampleType = trim(in.sampleType);
        if (!WaterGrade.TYPE_SOURCE.equals(sampleType)
                && !WaterGrade.TYPE_FINISHED.equals(sampleType)
                && !WaterGrade.TYPE_TAP.equals(sampleType)) {
            return Check.hard("样本类别「" + sampleType + "」不在 水源水/出厂水/管网末梢 三类之内");
        }
        // 4) 读数明细项（被校验的数据项代号）不得空
        if (trim(in.itemCode).isEmpty()) {
            return Check.hard("读数明细项（数据项代号）为空");
        }
        // 5) 采样时间：页面与接口同一把尺
        Date testAt = ImpSampleTime.parse(in.testAtText);
        if (testAt == null) {
            return Check.hard("采样时间" + blank(in.testAtText) + "解析不掉（认 "
                    + String.join("、", ImpSampleTime.acceptedPatterns()) + "）");
        }
        // 6) 有效/复核旗标词面
        Integer validFlag = parseValidFlag(in.validText);
        if (validFlag == null) {
            return Check.hard("是否有效「" + trim(in.validText) + "」词面不认（认 1/0、是/否、有效/无效）");
        }
        Integer reviewedFlag = parseReviewedFlag(in.reviewedText);
        if (reviewedFlag == null) {
            return Check.hard("是否已复核「" + trim(in.reviewedText) + "」词面不认（认 1/0、是/否、已复核/未复核）");
        }
        // 7) 规则代号：空号、当天无现行线、申报与现行线不一致，全部整批拦下
        String declared = trim(in.ruleCode);
        if (declared.isEmpty()) {
            return Check.hard("规则代号为空，无法与检测当天现行线核对");
        }
        WaterVerdict v = rules.evaluate(qty, testAt, sampleType);
        if (v.getKind() != WaterVerdict.Kind.GRADED) {
            return Check.hard("检测当天（" + ImpSampleTime.format(testAt) + "）" + sampleType
                    + "无可用现行线，申报代号「" + declared + "」对不上（" + v.getMessage() + "）");
        }
        if (!declared.equals(v.getRuleCode())) {
            return Check.hard("申报规则代号「" + declared + "」与检测当天现行线「"
                    + v.getRuleCode() + "」对不上");
        }
        return Check.ok(qty, testAt, validFlag.intValue(), reviewedFlag.intValue(), v);
    }

    /** 「是否有效」列词面映射：1/0、是/否、有效/无效；其余返回 null 记硬错 */
    public static Integer parseValidFlag(String text) {
        return parseFlag(text, VALID_YES_WORDS, VALID_NO_WORDS);
    }

    /** 「是否已复核」列词面映射：1/0、是/否、已复核/未复核；其余返回 null 记硬错 */
    public static Integer parseReviewedFlag(String text) {
        return parseFlag(text, REVIEWED_YES_WORDS, REVIEWED_NO_WORDS);
    }

    private static Integer parseFlag(String text, List<String> yesWords, List<String> noWords) {
        String t = trim(text).toUpperCase();
        if (t.isEmpty()) {
            return null;
        }
        if (TOKENS_YES.contains(t)) {
            return Integer.valueOf(1);
        }
        if (TOKENS_NO.contains(t)) {
            return Integer.valueOf(0);
        }
        if (yesWords.contains(t)) {
            return Integer.valueOf(1);
        }
        if (noWords.contains(t)) {
            return Integer.valueOf(0);
        }
        return null;
    }

    private static BigDecimal parseQty(String text) {
        String t = trim(text);
        if (t.isEmpty()) {
            return null;
        }
        try {
            return new BigDecimal(t);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String trim(String s) {
        return s == null ? "" : s.trim();
    }

    private static String blank(String s) {
        return trim(s).isEmpty() ? "为空" : "「" + s.trim() + "」";
    }
}
