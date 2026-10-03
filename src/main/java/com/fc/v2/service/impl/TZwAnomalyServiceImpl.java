package com.fc.v2.service.impl;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.fc.v2.mapper.auto.TZwAnomReportMapper;
import com.fc.v2.mapper.auto.TZwAnomalyMapper;
import com.fc.v2.mapper.auto.TZwImpRowMapper;
import com.fc.v2.mapper.auto.TZwReportActionMapper;
import com.fc.v2.model.auto.TZwAnomReport;
import com.fc.v2.model.auto.TZwAnomaly;
import com.fc.v2.model.auto.TZwImpRow;
import com.fc.v2.model.auto.TZwLimRule;
import com.fc.v2.model.auto.TZwReportAction;
import com.fc.v2.service.ITZwAnomalyService;
import com.fc.v2.service.ITZwLimRuleService;
import com.fc.v2.service.zw.AnomalyChain;
import com.fc.v2.service.zw.AnomalyView;
import com.fc.v2.service.zw.WaterGrade;
import com.fc.v2.service.zw.WaterVerdict;

/**
 * 水质异常判定登记 Service业务层处理。
 *
 * <p><b>登记是系统自动带出来的</b>：检测行落账的同一事务（{@link #syncOnImport}，
 * 由导入服务在 {@code importRows} 末尾同事务调用）里，凡进了正式账、判为关注档及以上的行，
 * 各带出一条登记，所依标准线代号/生效日取数与定级同一个唯一出口，三档界值与线名当场快照。
 * 同一条检测行只登记一次（uk_zw_anom_row 兜底）；未核实批次重导时旧版行置删，
 * 其未上报登记随版置删留痕，已上报的一根手指不碰。
 *
 * <p><b>更正只改本条</b>：上一条说超标判错了，不必到群里找人核对——以原始行当前读数重走
 * 唯一评价出口，把本条快照整体改写并记一轮更正。报过、反映到供水户、结案归档三种登记
 * 条件更新 0 行被拒，页面上也没有这些按钮。
 *
 * @author fuce
 * @date 2026-10-03
 */
@Service
public class TZwAnomalyServiceImpl implements ITZwAnomalyService {

    private static final int ROW_LEDGER = 1;

    @javax.annotation.Resource
    private TZwAnomalyMapper zwAnomalyMapper;
    @javax.annotation.Resource
    private TZwImpRowMapper zwImpRowMapper;
    @javax.annotation.Resource
    private TZwAnomReportMapper zwAnomReportMapper;
    @javax.annotation.Resource
    private TZwReportActionMapper zwReportActionMapper;
    @javax.annotation.Resource
    private ITZwLimRuleService zwLimRuleService;

    @Override
    public TZwAnomaly selectById(Long id) {
        return this.zwAnomalyMapper.selectById(id);
    }

    @Override
    public List<TZwAnomaly> selectList(Wrapper<TZwAnomaly> queryWrapper) {
        return this.zwAnomalyMapper.selectList(queryWrapper);
    }

    @Override
    public TZwAnomaly findByImpRow(Long impRowId) {
        if (impRowId == null) {
            return null;
        }
        return this.zwAnomalyMapper.selectOne(new QueryWrapper<TZwAnomaly>()
                .eq("imp_row_id", impRowId).eq("del_flag", 0));
    }

    /**
     * 与导入同一事务：旧版行（同批次 del_flag=1）名下未上报的登记随版置删；
     * 新版正式账行判为关注及以上的，缺登记则补一条。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void syncOnImport(String batchNo) {
        if (batchNo == null || batchNo.trim().isEmpty()) {
            return;
        }
        // 1) 旧版行随版留痕：只动未上报的登记，已上报的旧账照旧可查
        List<TZwImpRow> stale = this.zwImpRowMapper.selectList(new QueryWrapper<TZwImpRow>()
                .eq("batch_no", batchNo).eq("del_flag", 1));
        for (TZwImpRow r : stale) {
            this.zwAnomalyMapper.update(null, new UpdateWrapper<TZwAnomaly>()
                    .set("del_flag", 1)
                    .eq("imp_row_id", r.getId())
                    .eq("reported", 0)
                    .eq("del_flag", 0));
        }
        // 2) 新入正式账且关注档及以上的行自动登记（整批拦在库外的 status=0 不登记）
        List<TZwImpRow> rows = this.zwImpRowMapper.selectList(new QueryWrapper<TZwImpRow>()
                .eq("batch_no", batchNo)
                .eq("del_flag", 0)
                .eq("status", ROW_LEDGER)
                .ge("grade_level", WaterGrade.WATCH)
                .isNotNull("grade_level"));
        for (TZwImpRow r : rows) {
            TZwAnomaly exist = this.zwAnomalyMapper.selectOne(new QueryWrapper<TZwAnomaly>()
                    .eq("imp_row_id", r.getId()));
            // 同一条检测行只登记一次：已存在（含历史置删行）不再插，uk_zw_anom_row 亦兜底
            if (exist != null) {
                continue;
            }
            register(r);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TZwAnomaly correct(Long id, String note, String operator, List<String> rejectMsg) {
        TZwAnomaly a = this.zwAnomalyMapper.selectById(id);
        if (a == null || int1(a.getDelFlag())) {
            return reject(rejectMsg, "登记不存在");
        }
        if (int1(a.getArchived()) || int1(a.getClosed())) {
            return reject(rejectMsg, "已结案归档的判定动不了");
        }
        if (int1(a.getReflected())) {
            return reject(rejectMsg, "已反映到供水户名下，判定不可更正");
        }
        if (int1(a.getReported())) {
            return reject(rejectMsg, "已上报的台账只读，判定不可更正");
        }
        TZwImpRow row = this.zwImpRowMapper.selectById(a.getImpRowId());
        if (row == null || row.getTestAt() == null || row.getQty() == null) {
            return reject(rejectMsg, "当日原始检测记录缺读数或检测时间，无法重新判定");
        }
        // 重判只走唯一评价出口：用原始行当前读数/时间/类别取检测当天现行线，不碰人工传参
        WaterVerdict v = this.zwLimRuleService.evaluate(row.getQty(), row.getTestAt(), row.getSampleType());
        Date now = new Date();
        int times = (a.getCorrectTimes() == null ? 0 : a.getCorrectTimes().intValue()) + 1;

        if (!v.isGraded() || v.getGrade() < WaterGrade.WATCH) {
            // 重算后降到合格档/无可用标准：本条不再构成异常，置删留痕（同样的只读守卫）
            int n = this.zwAnomalyMapper.update(null, new UpdateWrapper<TZwAnomaly>()
                    .set("del_flag", 1)
                    .set("correct_times", times)
                    .set("last_correct_by", operator)
                    .set("last_correct_time", now)
                    .set("remark", mergedNote(a.getRemark(), note))
                    .eq("id", id)
                    .eq("reported", 0).eq("reflected", 0).eq("closed", 0).eq("archived", 0)
                    .eq("del_flag", 0));
            if (n == 0) {
                return reject(rejectMsg, "登记已被并发改动，请刷新后重试");
            }
            return this.zwAnomalyMapper.selectById(id);
        }

        TZwLimRule rule = this.zwLimRuleService.ruleOf(v.getRuleCode(), row.getTestAt(), false);
        BigDecimal th1 = rule == null ? null : rule.getTh1Max();
        BigDecimal th2 = rule == null ? null : rule.getTh2Max();
        BigDecimal th3 = rule == null ? null : rule.getTh3Max();
        BigDecimal[] over = AnomalyChain.overAmount(row.getQty(), v.getGrade(), th1, th2, th3);
        String judge = AnomalyChain.judgeText(v.getRuleCode(), v.getRuleEffDate(), row.getSampleType(),
                row.getQty(), v.getGrade(), th1, th2, th3);

        UpdateWrapper<TZwAnomaly> uw = new UpdateWrapper<TZwAnomaly>()
                .set("qty", row.getQty())
                .set("grade_level", v.getGrade())
                .set("rule_code", v.getRuleCode())
                .set("rule_eff_date", v.getRuleEffDate())
                .set("rule_name", rule == null ? null : rule.getRuleName())
                .set("th1_max", th1).set("th2_max", th2).set("th3_max", th3)
                .set("over_amt", over == null ? null : over[1])
                .set("base_th", over == null ? null : over[0])
                .set("need_retest", AnomalyChain.needRetest(v.getGrade()) ? 1 : 0)
                .set("need_stop", AnomalyChain.needStop(v.getGrade()) ? 1 : 0)
                .set("judge_text", judge)
                .set("correct_times", times)
                .set("last_correct_by", operator)
                .set("last_correct_time", now)
                .set("remark", mergedNote(a.getRemark(), note))
                .eq("id", id)
                // 三重只读守卫：报过/反映/结案归档任一处已变，0 行整笔不收
                .eq("reported", 0).eq("reflected", 0).eq("closed", 0).eq("archived", 0)
                .eq("del_flag", 0);
        int n = this.zwAnomalyMapper.update(null, uw);
        if (n == 0) {
            return reject(rejectMsg, "登记已被并发改动（已上报/已反映/已归档），更正未生效");
        }
        return this.zwAnomalyMapper.selectById(id);
    }

    @Override
    public AnomalyView buildView(Long id) {
        TZwAnomaly a = this.zwAnomalyMapper.selectById(id);
        if (a == null) {
            return null;
        }
        TZwImpRow row = this.zwImpRowMapper.selectById(a.getImpRowId());
        // 让位线也调得出，与导入下钻同一个 ruleOf 口径
        TZwLimRule rule = a.getRuleCode() == null ? null
                : this.zwLimRuleService.ruleOf(a.getRuleCode(), a.getTestAt(), true);
        TZwAnomReport report = a.getReportId() == null ? null
                : this.zwAnomReportMapper.selectById(a.getReportId());
        List<TZwReportAction> actions = new ArrayList<TZwReportAction>();
        if (report != null) {
            actions = this.zwReportActionMapper.selectList(new QueryWrapper<TZwReportAction>()
                    .eq("report_id", report.getId())
                    .eq("del_flag", 0)
                    .orderByAsc("seq_no"));
        }
        return new AnomalyView(a, row, rule, report, actions);
    }

    // ------------------------------------------------------------------

    /** 按检测行当场快照登记一条（定级字段沿用行内唯一出口回值，界值与线名当场取线快照） */
    private void register(TZwImpRow r) {
        Integer grade = r.getGradeLevel();
        if (grade == null || grade.intValue() < WaterGrade.WATCH) {
            return;
        }
        TZwLimRule rule = r.getRuleCode() == null ? null
                : this.zwLimRuleService.ruleOf(r.getRuleCode(), r.getTestAt(), true);
        BigDecimal th1 = rule == null ? null : rule.getTh1Max();
        BigDecimal th2 = rule == null ? null : rule.getTh2Max();
        BigDecimal th3 = rule == null ? null : rule.getTh3Max();
        BigDecimal[] over = AnomalyChain.overAmount(r.getQty(), grade.intValue(), th1, th2, th3);

        TZwAnomaly a = new TZwAnomaly();
        String baseNo = r.getSiteNo() + "-" + AnomalyChain.dayKey(r.getTestAt()) + "-" + r.getItemCode();
        // 同点位同日同指标若已另有登记（一天两条检测行），登记号追加序号；判重只在上报层卡
        Integer sameNo = this.zwAnomalyMapper.selectCount(new QueryWrapper<TZwAnomaly>()
                .eq("anom_no", baseNo).eq("del_flag", 0));
        a.setAnomNo(sameNo != null && sameNo.intValue() > 0 ? baseNo + "-" + (sameNo.intValue() + 1) : baseNo);
        a.setImpRowId(r.getId());
        a.setBatchNo(r.getBatchNo());
        a.setSiteNo(r.getSiteNo());
        a.setItemCode(r.getItemCode());
        a.setSampleType(r.getSampleType());
        a.setTestDay(AnomalyChain.truncateToDay(r.getTestAt()));
        a.setTestAt(r.getTestAt());
        a.setQty(r.getQty());
        a.setGradeLevel(grade);
        a.setRuleCode(r.getRuleCode());
        a.setRuleEffDate(r.getRuleEffDate());
        a.setRuleName(rule == null ? null : rule.getRuleName());
        a.setTh1Max(th1);
        a.setTh2Max(th2);
        a.setTh3Max(th3);
        a.setOverAmt(over == null ? null : over[1]);
        a.setBaseTh(over == null ? null : over[0]);
        a.setNeedRetest(AnomalyChain.needRetest(grade.intValue()) ? 1 : 0);
        a.setNeedStop(AnomalyChain.needStop(grade.intValue()) ? 1 : 0);
        a.setJudgeText(AnomalyChain.judgeText(r.getRuleCode(), r.getRuleEffDate(),
                r.getSampleType(), r.getQty(), grade.intValue(), th1, th2, th3));
        a.setReported(0);
        a.setReflected(0);
        a.setClosed(0);
        a.setArchived(0);
        a.setCorrectTimes(0);
        a.setDelFlag(0);
        this.zwAnomalyMapper.insert(a);
    }

    private static String mergedNote(String oldNote, String note) {
        String n = note == null ? "" : note.trim();
        if (n.isEmpty()) {
            return oldNote;
        }
        String o = oldNote == null ? "" : oldNote;
        String merged = o + (o.isEmpty() ? "" : "；") + "更正说明：" + n;
        return merged.length() > 500 ? merged.substring(0, 500) : merged;
    }

    private static boolean int1(Integer v) {
        return v != null && v.intValue() == 1;
    }

    private static TZwAnomaly reject(List<String> rejectMsg, String msg) {
        if (rejectMsg != null) {
            rejectMsg.clear();
            rejectMsg.add(msg);
        }
        return null;
    }
}
