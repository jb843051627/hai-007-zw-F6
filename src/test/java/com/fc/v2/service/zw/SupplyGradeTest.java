package com.fc.v2.service.zw;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

/**
 * 保障强度层级折算口径单测：压着上界往高算——
 * 等于一档上限算二档、等于二档算三档、超出三档上限仍压在三档；缺阈值/缺规模判不出。
 */
public class SupplyGradeTest {

    private static final BigDecimal T1 = new BigDecimal("30");
    private static final BigDecimal T2 = new BigDecimal("80");
    private static final BigDecimal T3 = new BigDecimal("200");

    @Test
    public void belowFirstCeilingIsGrade1() {
        assertEquals(SupplyGrade.GRADE_1, SupplyGrade.grade(T1, T2, T3, new BigDecimal("10")));
        assertEquals(SupplyGrade.GRADE_1, SupplyGrade.grade(T1, T2, T3, new BigDecimal("29.99")));
    }

    @Test
    public void exactlyOnCeilingPushesUpOneGrade() {
        // 站里口径：规模恰好等于一档上限的卡算二档，不留在一档
        assertEquals(SupplyGrade.GRADE_2, SupplyGrade.grade(T1, T2, T3, T1));
        assertEquals(SupplyGrade.GRADE_3, SupplyGrade.grade(T1, T2, T3, T2));
    }

    @Test
    public void beyondThirdCeilingIsStillGrade3() {
        // 超出三档上限的不另开第四档，一律算三档
        assertEquals(SupplyGrade.GRADE_3, SupplyGrade.grade(T1, T2, T3, T3));
        assertEquals(SupplyGrade.GRADE_3, SupplyGrade.grade(T1, T2, T3, new BigDecimal("999")));
    }

    @Test
    public void missingThresholdsOrQtyCannotBeGraded() {
        assertEquals(SupplyGrade.NONE, SupplyGrade.grade(null, T2, T3, T1));
        assertEquals(SupplyGrade.NONE, SupplyGrade.grade(T1, null, T3, T1));
        assertEquals(SupplyGrade.NONE, SupplyGrade.grade(T1, T2, null, T1));
        assertEquals(SupplyGrade.NONE, SupplyGrade.grade(T1, T2, T3, null));
    }
}
