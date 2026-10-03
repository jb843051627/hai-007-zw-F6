package com.fc.v2.service.impl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.mapper.auto.TZwLimRuleMapper;
import com.fc.v2.model.auto.TZwLimRule;
import com.fc.v2.service.ITZwLimRuleService;
import com.fc.v2.service.zw.WaterGrade;
import com.fc.v2.service.zw.WaterVerdict;

/**
 * 水质指标限值分档判定线 Service业务层处理。
 *
 * <p><b>一个评价出口</b>：读数落定哪一档只出自 {@link #evaluate(java.math.BigDecimal, Date, String)}。
 * 取哪条线（检测发生那天、该样本类别、现行在窗口内）、并线听谁的（顺位降序、并列代号序列排后者）、
 * 界值空缺怎么处置（空档不设、钉在最高在设档），全由该出口当场定夺，定级内核一律走
 * {@link WaterGrade}，页面与旁路不得自行判档。
 *
 * <p>"某条判定线在 at 时刻现行可用"全站只有 {@link #effective} 一把尺：
 * del_flag=0、情形为现行（status=0）、类别相符、at 落在启用区间 [eff_start, eff_end)
 * （启用之日含、交棒之日不含）。列表、选线、旁路可用性、角标全部过这同一谓词，禁止各写各的。
 * 让位行只在历史复核 {@link #evaluate(String, java.math.BigDecimal, Date, boolean)} 里放行。
 *
 * <p>区间比较一律在内存里按墙钟毫秒做，不把 java.util.Date 绑进 SQL：
 * JDBC 的 serverTimezone 与本机时区不对称时，"切换当日"这类边界会整体错档（同域限值线已踩过）。
 *
 * @author fuce
 * @date 2026-09-14
 */
@Service
public class TZwLimRuleServiceImpl implements ITZwLimRuleService {

    /** 判定线情形 0现行 1已让位 */
    private static final int STATUS_CURRENT = 0;

    /** 顺位降序；并列按 ruleCode 序列排后者胜（确定性，四季一致不可今秋选前明春选后） */
    private static final Comparator<TZwLimRule> PRIORITY_THEN_CODE_DESC = new Comparator<TZwLimRule>() {
        @Override
        public int compare(TZwLimRule a, TZwLimRule b) {
            int pa = a.getPriority() == null ? 0 : a.getPriority().intValue();
            int pb = b.getPriority() == null ? 0 : b.getPriority().intValue();
            if (pa != pb) {
                return pb - pa;
            }
            String ca = a.getRuleCode() == null ? "" : a.getRuleCode();
            String cb = b.getRuleCode() == null ? "" : b.getRuleCode();
            return cb.compareTo(ca);
        }
    };

    @javax.annotation.Resource
    private TZwLimRuleMapper zwLimRuleMapper;

    @Override
    public TZwLimRule selectTZwLimRuleById(Long id) {
        return this.zwLimRuleMapper.selectById(id);
    }

    @Override
    public WaterVerdict evaluate(java.math.BigDecimal input, Date at, String sampleType) {
        // 第一关：读数本身不合法（空值/负数/超大怪值）——不参与定级，退回化验员重录
        if (!WaterGrade.validInput(input)) {
            return WaterVerdict.invalid(input);
        }
        List<TZwLimRule> avail = listAvailable(at, sampleType);
        if (avail.isEmpty()) {
            // 查不到可用标准就明写无可用标准，不许拿临近日期凑合、不许硬给结论
            return WaterVerdict.noRule(sampleType, at);
        }
        // 列表首位即"顺位最高、并列代号排后"的那条；类别在 listAvailable 已过滤
        return grade(avail.get(0), input, at, sampleType);
    }

    @Override
    public WaterVerdict evaluate(String ruleCode, java.math.BigDecimal input, Date at, boolean forReview) {
        if (ruleCode == null || ruleCode.trim().isEmpty()) {
            return WaterVerdict.noRule(null, at);
        }
        if (!WaterGrade.validInput(input)) {
            return WaterVerdict.invalid(input);
        }
        // 历史复核只认编号：凭当时写入的编号调那条线；forReview 时让位行一并放行
        TZwLimRule rule = findRule(ruleCode, at, forReview);
        if (rule == null) {
            return WaterVerdict.noRule(ruleCode, at);
        }
        return grade(rule, input, at, rule.getSampleType());
    }

    @Override
    public TZwLimRule ruleOf(String ruleCode, Date at, boolean includeRetired) {
        if (ruleCode == null || ruleCode.trim().isEmpty() || at == null) {
            return null;
        }
        // 下钻并排展示用：定位口径与历史复核 evaluate(...) 同一个 findRule，不许两处翻出不同的一条
        return findRule(ruleCode, at, includeRetired);
    }

    @Override
    public int evaluate(String ruleCode, java.math.BigDecimal input, Date at) {
        // 老方法维持原样（按代号翻线、只认现行版本、判不了给 0）；定级口径与唯一出口同走 WaterGrade
        WaterVerdict v = evaluate(ruleCode, input, at, false);
        return v.isGraded() ? v.getGrade() : WaterGrade.NONE;
    }

    @Override
    public int evaluateTop(java.math.BigDecimal input, Date at) {
        // 缺参/越界口径与唯一出口完全一致，不许一个挡一个不挡
        if (!WaterGrade.validInput(input) || at == null) {
            return WaterGrade.NONE;
        }
        List<TZwLimRule> avail = listAvailable(at);
        if (avail.isEmpty()) {
            return WaterGrade.NONE;
        }
        return WaterGrade.level(input, avail.get(0).getTh1Max(),
                avail.get(0).getTh2Max(), avail.get(0).getTh3Max());
    }

    @Override
    public List<TZwLimRule> listAvailable(Date at) {
        return listAvailable(at, null);
    }

    @Override
    public List<TZwLimRule> listAvailable(Date at, String sampleType) {
        if (at == null) {
            return Collections.emptyList();
        }
        QueryWrapper<TZwLimRule> qw = new QueryWrapper<TZwLimRule>().eq("del_flag", 0);
        if (sampleType != null && !sampleType.trim().isEmpty()) {
            qw.eq("sample_type", sampleType.trim());
        }
        List<TZwLimRule> all = this.zwLimRuleMapper.selectList(qw);
        List<TZwLimRule> avail = new ArrayList<TZwLimRule>();
        for (TZwLimRule r : all) {
            if (effective(r, at)) {
                avail.add(r);
            }
        }
        // 列表顺序就是"谁先说话"的顺序，唯一出口直接取首位，与台账同口径
        Collections.sort(avail, PRIORITY_THEN_CODE_DESC);
        return avail;
    }

    @Override
    public boolean usable(Long id, Date at) {
        if (id == null || at == null) {
            return false;
        }
        // 与列表/选线同谓词：已删除、已让位、时段外、类别不符都不可用
        return effective(this.zwLimRuleMapper.selectById(id), at);
    }

    @Override
    public int countAvailable(Date at) {
        return listAvailable(at).size();
    }

    /**
     * 唯一的定级落锤：界值空缺由 {@link WaterGrade#level} 按"空档不设、钉在最高在设档"处置；
     * 三界值全空＝这条线给不出档位，按无可用标准回（不硬给结论）。
     * 写进检测结果的线代号与生效日一律取本回值，调用方不得自行拼装。
     */
    private static WaterVerdict grade(TZwLimRule rule, java.math.BigDecimal input, Date at, String sampleType) {
        int g = WaterGrade.level(input, rule.getTh1Max(), rule.getTh2Max(), rule.getTh3Max());
        if (g == WaterGrade.NONE) {
            return WaterVerdict.noRule(sampleType, at);
        }
        return WaterVerdict.graded(g, rule.getRuleCode(), rule.getEffStart());
    }

    /**
     * 唯一的"现行可用"谓词：未删除、情形为现行、at 落在 [eff_start, eff_end)。
     * 起止皆空表示长期有效；只空一头表示单侧开放。类别不参与此处（按号查线时类别随线走）。
     */
    private static boolean effective(TZwLimRule r, Date at) {
        if (r == null || at == null) {
            return false;
        }
        if (r.getDelFlag() != null && r.getDelFlag() != 0) {
            return false;
        }
        if (r.getStatus() == null || r.getStatus() != STATUS_CURRENT) {
            return false;
        }
        return WaterGrade.effective(at, r.getEffStart(), r.getEffEnd());
    }

    /**
     * 按代号在 at 时刻定位线。includeRetired=true（历史复核）时让位行也放行，
     * 其余路径一律只认现行行。同号版本理论上不该时段重叠；万一重叠，启用之日晚的视为新版本
     * （确定性：再并列取主键大的）。
     */
    private TZwLimRule findRule(String ruleCode, Date at, boolean includeRetired) {
        if (at == null) {
            return null;
        }
        List<TZwLimRule> hit = this.zwLimRuleMapper.selectList(new QueryWrapper<TZwLimRule>()
                .eq("del_flag", 0)
                .eq("rule_code", ruleCode));
        TZwLimRule picked = null;
        for (TZwLimRule r : hit) {
            boolean inWindow = WaterGrade.effective(at, r.getEffStart(), r.getEffEnd());
            boolean current = r.getStatus() != null && r.getStatus() == STATUS_CURRENT;
            if (!inWindow || (!current && !includeRetired)) {
                continue;
            }
            if (picked == null
                    || (r.getEffStart() != null && (picked.getEffStart() == null
                        || r.getEffStart().after(picked.getEffStart())))
                    || (r.getEffStart() != null && picked.getEffStart() != null
                        && r.getEffStart().getTime() == picked.getEffStart().getTime()
                        && r.getId() > picked.getId())) {
                picked = r;
            }
        }
        return picked;
    }
}
