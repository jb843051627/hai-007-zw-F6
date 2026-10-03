package com.fc.v2.service.zw;

import java.math.BigDecimal;
import java.util.Date;

/**
 * 一次水质评价的裁决回值——全公司只认这一个出口吐出来的东西。
 *
 * <p>三种结局必须分得清，页面不许预填、不许先亮答案：
 * <ul>
 *   <li>{@link Kind#GRADED}：定级成功，{@code grade/ruleCode/ruleEffDate} 三件套齐全，
 *       写入检测结果的线代号与生效日以本回值为准，写过不再改；</li>
 *   <li>{@link Kind#INVALID}：读数不在合理量域（负数、空值、超大怪值），不参与定级，退回化验员重录；</li>
 *   <li>{@link Kind#NO_RULE}：检测发生那天该类别查不到可用标准，明确写「无可用标准」，
 *       不许拿临近日期的凑合，更不许硬给结论（grade/ruleCode/ruleEffDate 一律不给）。</li>
 * </ul>
 *
 * @author fuce
 * @date 2026-09-30
 */
public class WaterVerdict {

    public enum Kind {
        /** 已定级（等级 + 所依标准代号 + 该标准启用之日） */
        GRADED,
        /** 读数越界/空值，退回重录 */
        INVALID,
        /** 检测当日无可用标准 */
        NO_RULE
    }

    private final Kind kind;
    private final int grade;
    private final String ruleCode;
    private final Date ruleEffDate;
    private final String message;

    private WaterVerdict(Kind kind, int grade, String ruleCode, Date ruleEffDate, String message) {
        this.kind = kind;
        this.grade = grade;
        this.ruleCode = ruleCode;
        this.ruleEffDate = ruleEffDate;
        this.message = message;
    }

    /** 定级成功：等级连同所依标准代号、该标准生效那天一并回出，写进检测结果后不再改 */
    public static WaterVerdict graded(int grade, String ruleCode, Date ruleEffDate) {
        return new WaterVerdict(Kind.GRADED, grade, ruleCode, ruleEffDate,
                WaterGrade.gradeText(grade) + "（依 " + ruleCode + "，"
                        + (ruleEffDate == null ? "长期有效" : new java.text.SimpleDateFormat("yyyy-MM-dd").format(ruleEffDate))
                        + " 起生效）");
    }

    /** 读数不合法：不参与定级，退回化验员重录 */
    public static WaterVerdict invalid(BigDecimal input) {
        return new WaterVerdict(Kind.INVALID, WaterGrade.NONE, null, null,
                "读数" + (input == null ? "为空" : "「" + input.toPlainString() + "」")
                        + "不在合理区间，不参与定级，退回化验员重录");
    }

    /** 检测当日无可用标准：只标「无可用标准」，不得硬给结论 */
    public static WaterVerdict noRule(String sampleType, Date at) {
        String day = at == null ? "" : new java.text.SimpleDateFormat("yyyy-MM-dd").format(at);
        return new WaterVerdict(Kind.NO_RULE, WaterGrade.NONE, null, null,
                "无可用标准（" + (sampleType == null ? "" : sampleType) + "，" + day + "）");
    }

    public Kind getKind() {
        return kind;
    }

    public int getGrade() {
        return grade;
    }

    public String getRuleCode() {
        return ruleCode;
    }

    public Date getRuleEffDate() {
        return ruleEffDate;
    }

    public String getMessage() {
        return message;
    }

    public boolean isGraded() {
        return kind == Kind.GRADED;
    }

    /** 档位名目（未定级时为「无可用标准」或「不参评」） */
    public String gradeText() {
        if (kind == Kind.NO_RULE) {
            return "无可用标准";
        }
        return WaterGrade.gradeText(grade);
    }
}
