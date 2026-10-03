package com.fc.v2.service;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

import com.fc.v2.model.auto.TZwLimRule;
import com.fc.v2.service.zw.WaterVerdict;

/**
 * 水质指标限值分档判定线 Service接口。
 *
 * <p>读数落定哪一档，全站只认一个评价出口：{@link #evaluate(BigDecimal, Date, String)}。
 * 取哪条线、并线听谁的、界值空缺怎么处置，都由该出口当场定夺；页面不预填结论、不先亮答案，
 * 写进检测结果的线代号与生效日也以它的回值 {@link WaterVerdict} 为准。
 *
 * <p>顺着线代号翻旧线的老方法 {@link #evaluate(String, BigDecimal, Date)} 维持原样；
 * 历史复核需要把让位行也调出来，就在它底下再加一层同名的 {@link #evaluate(String, BigDecimal, Date, boolean)}。
 *
 * @author fuce
 * @date 2026-09-14
 */
public interface ITZwLimRuleService {

    /** 按主键查询规则 */
    TZwLimRule selectTZwLimRuleById(Long id);

    /**
     * 唯一评价出口：用检测发生那天（不是今天）该样本类别的现行线给读数定级。
     * 同类别多条并行使权时顺位高者胜，顺位相同代号序列排后者胜。
     * 读数不合法（空/负/超大怪值）回 INVALID 退回重录；当日该类别无可用线回 NO_RULE，
     * 明确写「无可用标准」，不拿临近日期凑合、不硬给结论。
     */
    WaterVerdict evaluate(BigDecimal input, Date at, String sampleType);

    /**
     * 历史复核新取法（老方法底下加的同名一层）：凭检测结果里当时写入的编号调那条线，
     * 按当时界值重评——让位行只在此处调得出，复核结论与原结论各留各的编号上下文，
     * 不许用今天的标准改算昨天的记录。{@code forReview=true} 时已让位行同样入列。
     */
    WaterVerdict evaluate(String ruleCode, BigDecimal input, Date at, boolean forReview);

    /**
     * 凭代号调规则条文本身（下钻页把原始行、规则条文、复核结论并排看用）：
     * 取 at 时刻该编号在窗口内的那条；{@code includeRetired=true} 时让位行也调得出。
     * 查无返回 null。口径与 {@link #evaluate(String, BigDecimal, Date, boolean)} 的定位完全一致。
     */
    TZwLimRule ruleOf(String ruleCode, Date at, boolean includeRetired);

    /**
     * 老方法维持原样：按 ruleCode 在 at 时刻现行生效的版本判档（1..4）。
     * 无生效版本 / 输入越界 / 参数缺失一律返回 0。
     */
    int evaluate(String ruleCode, BigDecimal input, Date at);

    /** 多规则求值：at 时刻全部类别可用规则中优先级最高者的档位；无可用规则返回 0 */
    int evaluateTop(BigDecimal input, Date at);

    /** at 时刻全部类别可用规则（顺位降序、代号降序） */
    List<TZwLimRule> listAvailable(Date at);

    /** at 时刻指定样本类别的可用规则（顺位降序、代号降序） */
    List<TZwLimRule> listAvailable(Date at, String sampleType);

    /** 单条规则在 at 时刻是否现行可用（批量导入/报表等旁路复用，口径须与定位/列表一致） */
    boolean usable(Long id, Date at);

    /** at 时刻可用规则条数（列表页角标 / 看板） */
    int countAvailable(Date at);
}
