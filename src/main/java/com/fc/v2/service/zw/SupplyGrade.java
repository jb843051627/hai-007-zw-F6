package com.fc.v2.service.zw;

import java.math.BigDecimal;

import com.fc.v2.model.auto.TZwSource;

/**
 * 档案卡供水规模折保障强度层级的全站唯一裁决内核（纯领域、无 Spring、无库——可直接单测）。
 *
 * <p>层级只认底档上钉着的三道上限：一档 {@code th1}、二档 {@code th2}、三档 {@code th3}（万吨/日）。
 * 站里只有一句口径——<b>压着上界往高算</b>：
 * <ul>
 *   <li>规模 &lt; 一档上限，算一档；</li>
 *   <li>规模恰好等于一档上限，算二档（等于二档上限同理算三档）；</li>
 *   <li>规模超出三档上限的，不另开第四档，一律压在三档。</li>
 * </ul>
 *
 * <p>三道上限缺一道、或规模本身没数，都折不出层级，给 {@link #NONE}——
 * 绝不拿 null 去比，也绝不拍脑袋补一档。
 *
 * @author fuce
 * @date 2026-10-04
 */
public final class SupplyGrade {

    /** 折不出（上限没维护齐 / 规模没数） */
    public static final int NONE = 0;
    /** 一档 */
    public static final int GRADE_1 = 1;
    /** 二档 */
    public static final int GRADE_2 = 2;
    /** 三档（再大也压在这一档） */
    public static final int GRADE_3 = 3;

    private SupplyGrade() {
    }

    /** 按底档三道上限折层级 */
    public static int grade(TZwSource source, BigDecimal qty) {
        if (source == null) {
            return NONE;
        }
        return grade(source.getTh1Max(), source.getTh2Max(), source.getTh3Max(), qty);
    }

    /**
     * 按三道上限折层级。压着上界往高算：等于上界即入上一档；越过三档上限仍是三档。
     */
    public static int grade(BigDecimal th1, BigDecimal th2, BigDecimal th3, BigDecimal qty) {
        if (qty == null || th1 == null || th2 == null || th3 == null) {
            return NONE;
        }
        if (qty.compareTo(th1) < 0) {
            return GRADE_1;
        }
        if (qty.compareTo(th2) < 0) {
            return GRADE_2;
        }
        return GRADE_3;
    }
}
