package com.fc.v2.service.zw;

import java.math.BigDecimal;
import java.util.Date;

/**
 * 水质读数分档的全站唯一裁决内核（纯领域、无 Spring、无库——可直接单测）。
 *
 * <p>定级只认三个界值：{@code th1}=关注档上限、{@code th2}=超标档上限、{@code th3}=严重超标档上限。
 * <ul>
 *   <li>读数值与界值相同算高档（恰为 th1 入关注档，恰为 th3 入严重超标档）；</li>
 *   <li>界值空＝该档不设，不算断档、不报错：读数越过在设的界值后钉在最高那个在设档上，
 *       例如 th3 留空时，再大的读数也只到超标档，绝不硬给严重超标；</li>
 *   <li>三个界值全空＝这条线给不出档位，{@link #NONE}。</li>
 * </ul>
 *
 * <p>选线两套顺序也是四季一致的死规矩：顺位（priority）数值大的先说；顺位分不出，
 * 按代号序列排后的一条胜出，不许今秋选前、明春选后。
 *
 * <p>生效窗口一律半开区间 {@code [effStart, effEnd)}：启用之日含、交棒之日不含——
 * 新旧两线交接那天全厂只有一套口径，旧线管到进程、新线从结局起算。
 *
 * @author fuce
 * @date 2026-09-30
 */
public final class WaterGrade {

    /** 判不了 / 不参评（无在设界值） */
    public static final int NONE = 0;
    /** 合格档 */
    public static final int NORMAL = 1;
    /** 关注档：并入下次检测 */
    public static final int WATCH = 2;
    /** 超标档：安排复测 */
    public static final int OVER = 3;
    /** 严重超标档：立即上报复核 */
    public static final int SEVERE = 4;

    /** 适用样本类别：三类各管各的，选线只在同类别内并行 */
    public static final String TYPE_SOURCE = "水源水";
    public static final String TYPE_FINISHED = "出厂水";
    public static final String TYPE_TAP = "管网末梢";

    /** 读数合理量域：负数、空值、超此上限的怪值一律不参评，退回化验员重录 */
    public static final BigDecimal INPUT_MIN = BigDecimal.ZERO;
    public static final BigDecimal INPUT_MAX = new BigDecimal("100");

    private WaterGrade() {
    }

    /** 读数是否落在合理量域（空值/负数/超大怪值都不参评） */
    public static boolean validInput(BigDecimal input) {
        return input != null
                && input.compareTo(INPUT_MIN) >= 0
                && input.compareTo(INPUT_MAX) <= 0;
    }

    /**
     * 按三个界值定级。空界值即该档不设：越过在设界值后钉在最高在设档，不向空档拔升；
     * 等于界值取高档；三界值全空（或输入不合理）返回 {@link #NONE}。
     */
    public static int level(BigDecimal input, BigDecimal th1, BigDecimal th2, BigDecimal th3) {
        if (!validInput(input)) {
            return NONE;
        }
        if (th1 == null && th2 == null && th3 == null) {
            return NONE;
        }
        int grade = NORMAL;
        if (th1 != null && input.compareTo(th1) >= 0) {
            grade = WATCH;
        }
        if (th2 != null && input.compareTo(th2) >= 0) {
            grade = OVER;
        }
        if (th3 != null && input.compareTo(th3) >= 0) {
            grade = SEVERE;
        }
        return grade;
    }

    /** at 是否落在半开生效窗口 [effStart, effEnd)；起止皆空为长期有效，只空一头为单侧开放 */
    public static boolean effective(Date at, Date effStart, Date effEnd) {
        if (at == null) {
            return false;
        }
        long t = at.getTime();
        if (effStart != null && t < effStart.getTime()) {
            return false;
        }
        // 交棒之日不含：恰在交棒日 00:00:00 即让位
        if (effEnd != null && t >= effEnd.getTime()) {
            return false;
        }
        return true;
    }

    /**
     * 两条并行使权的线谁先说：顺位数值大者胜；顺位相同按代号序列排后者胜。
     * @return true 表示 a 压过 b
     */
    public static boolean precedes(int priorityA, String codeA, int priorityB, String codeB) {
        if (priorityA != priorityB) {
            return priorityA > priorityB;
        }
        return nullSafe(codeA).compareTo(nullSafe(codeB)) > 0;
    }

    /** 档位序号转档位名目（{@link #NONE} 给「不参评」） */
    public static String gradeText(int grade) {
        switch (grade) {
            case NORMAL:
                return "合格";
            case WATCH:
                return "关注";
            case OVER:
                return "超标";
            case SEVERE:
                return "严重超标";
            default:
                return "不参评";
        }
    }

    /** 等级对应的处置口径 */
    public static String actionText(int grade) {
        switch (grade) {
            case SEVERE:
                return "立即上报复核";
            case OVER:
                return "安排复测";
            case WATCH:
                return "并入下次检测";
            default:
                return "";
        }
    }

    private static String nullSafe(String s) {
        return s == null ? "" : s;
    }
}
