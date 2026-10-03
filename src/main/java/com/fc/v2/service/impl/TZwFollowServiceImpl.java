package com.fc.v2.service.impl;

import java.util.Date;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.fc.v2.mapper.auto.TZwAnomReportMapper;
import com.fc.v2.mapper.auto.TZwFollowMapper;
import com.fc.v2.mapper.auto.TZwFollowTemplateMapper;
import com.fc.v2.model.auto.TZwAnomReport;
import com.fc.v2.model.auto.TZwFollow;
import com.fc.v2.model.auto.TZwFollowTemplate;
import com.fc.v2.service.ITZwFollowService;
import com.fc.v2.service.zw.AnomalyChain;

/**
 * 盯办 Service业务层处理。
 *
 * <p><b>只有系统能开、只能系统结</b>：开条的入口只有两处——处置步推进（{@link #openStep}）
 * 与前一条盯办了结后续开（{@link #finish}/{@link #runDue} 同事务）；结条只走条件更新
 * （id + status=0），任何已结条都改不动，后段流水倒改不了前段。每条都带上一次盯的时间，
 * 同一步次照 {@link TZwFollowTemplate} 的周期顺排（反映供水户 2 天、其余各算各的）。
 *
 * @author fuce
 * @date 2026-10-03
 */
@Service
public class TZwFollowServiceImpl implements ITZwFollowService {

    /** 模板情形 0现行 */
    private static final int TPL_CURRENT = 0;
    /** 上报单 0在办 1已结案 */
    private static final int REPORT_RUNNING = 0;

    @javax.annotation.Resource
    private TZwFollowMapper zwFollowMapper;
    @javax.annotation.Resource
    private TZwFollowTemplateMapper zwFollowTemplateMapper;
    @javax.annotation.Resource
    private TZwAnomReportMapper zwAnomReportMapper;

    @Override
    public TZwFollowTemplate templateOfStep(int stepNo) {
        return this.zwFollowTemplateMapper.selectOne(new QueryWrapper<TZwFollowTemplate>()
                .eq("step_no", stepNo)
                .eq("status", TPL_CURRENT)
                .eq("del_flag", 0)
                .last("limit 1"));
    }

    /**
     * 步次推进：旧步待盯条系统结成"步次推进"留痕（不记漏盯——人还没轮到盯），
     * 再照新步模板开第一条；无模板（如已结案）只结不开。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void openStep(Long reportId, int stepNo, Date now) {
        List<TZwFollow> pending = this.zwFollowMapper.selectList(new QueryWrapper<TZwFollow>()
                .eq("report_id", reportId)
                .eq("status", AnomalyChain.FOLLOW_WAIT)
                .eq("del_flag", 0));
        for (TZwFollow f : pending) {
            int n = this.zwFollowMapper.update(null, new UpdateWrapper<TZwFollow>()
                    .set("status", AnomalyChain.FOLLOW_DONE)
                    .set("result", "处置步推进至「" + AnomalyChain.stepName(stepNo)
                            + "」，本步盯办结束")
                    .set("finish_by", AnomalyChain.SYSTEM)
                    .set("finish_time", now)
                    .eq("id", f.getId())
                    .eq("status", AnomalyChain.FOLLOW_WAIT)
                    .eq("del_flag", 0));
            if (n == 0) {
                throw new IllegalStateException("盯办条已被并发了结：" + f.getId());
            }
        }
        TZwFollowTemplate tpl = templateOfStep(stepNo);
        if (tpl == null) {
            return;
        }
        openNext(reportId, stepNo, tpl, now);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TZwFollow finish(Long followId, boolean reached, String result, String operator,
                            List<String> rejectMsg) {
        String text = result == null ? "" : result.trim();
        if (followId == null || text.isEmpty()) {
            return reject(rejectMsg, "盯办结果要写明本次盯的情况（联系不上也写明）");
        }
        TZwFollow f = this.zwFollowMapper.selectById(followId);
        if (f == null || int1(f.getDelFlag())) {
            return reject(rejectMsg, "盯办记录不存在");
        }
        if (f.getStatus() == null || f.getStatus().intValue() != AnomalyChain.FOLLOW_WAIT) {
            return reject(rejectMsg, "这条已经了结，只追加台账不许改");
        }
        if (operator == null || !operator.equals(f.getOwnerLogin())) {
            // 系统按步次/环节开通权限：不是该这条的经办人，本口只读
            return reject(rejectMsg, "本条盯办派给「" + f.getOwnerLogin() + "」，不由你登记");
        }
        Date now = new Date();
        int newStatus = reached ? AnomalyChain.FOLLOW_DONE : AnomalyChain.FOLLOW_UNREACHABLE;
        int n = this.zwFollowMapper.update(null, new UpdateWrapper<TZwFollow>()
                .set("status", newStatus)
                .set("result", text)
                .set("finish_by", operator)
                .set("finish_time", now)
                .eq("id", followId)
                .eq("status", AnomalyChain.FOLLOW_WAIT)
                .eq("del_flag", 0));
        if (n == 0) {
            return reject(rejectMsg, "已被并发了结，请刷新查看");
        }
        // 同一步次原样续开下一条（哪怕联系不上——盯得紧的步次照样 2 天一盯）；
        // 单若已结案或已推进到别的步次，不再续。
        TZwAnomReport report = this.zwAnomReportMapper.selectById(f.getReportId());
        if (report != null && report.getStatus() != null
                && report.getStatus().intValue() == REPORT_RUNNING
                && report.getStepNo() != null
                && report.getStepNo().intValue() == f.getStepNo().intValue()) {
            TZwFollowTemplate tpl = templateOfStep(f.getStepNo().intValue());
            if (tpl != null) {
                openNext(f.getReportId(), f.getStepNo().intValue(), tpl, now);
            }
        }
        return this.zwFollowMapper.selectById(followId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int runDue(Date now) {
        Date at = now == null ? new Date() : now;
        List<TZwFollow> due = this.zwFollowMapper.selectList(new QueryWrapper<TZwFollow>()
                .eq("status", AnomalyChain.FOLLOW_WAIT)
                .le("due_at", at)
                .eq("del_flag", 0)
                .orderByAsc("due_at")
                .orderByAsc("id"));
        int handled = 0;
        for (TZwFollow f : due) {
            int n = this.zwFollowMapper.update(null, new UpdateWrapper<TZwFollow>()
                    .set("status", AnomalyChain.FOLLOW_MISSED)
                    .set("result", "到期未盯，系统自动记漏盯")
                    .set("finish_by", AnomalyChain.SYSTEM)
                    .set("finish_time", at)
                    .eq("id", f.getId())
                    .eq("status", AnomalyChain.FOLLOW_WAIT)
                    .eq("del_flag", 0));
            if (n == 0) {
                // 经办恰在跑批同一刻登记：人家赢了，不续开（他那笔已续）
                continue;
            }
            handled++;
            TZwAnomReport report = this.zwAnomReportMapper.selectById(f.getReportId());
            if (report != null && report.getStatus() != null
                    && report.getStatus().intValue() == REPORT_RUNNING
                    && report.getStepNo() != null
                    && report.getStepNo().intValue() == f.getStepNo().intValue()) {
                TZwFollowTemplate tpl = templateOfStep(f.getStepNo().intValue());
                if (tpl != null) {
                    openNext(f.getReportId(), f.getStepNo().intValue(), tpl, at);
                }
            }
        }
        return handled;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void closeAll(Long reportId, Date now) {
        List<TZwFollow> pending = this.zwFollowMapper.selectList(new QueryWrapper<TZwFollow>()
                .eq("report_id", reportId)
                .eq("status", AnomalyChain.FOLLOW_WAIT)
                .eq("del_flag", 0));
        for (TZwFollow f : pending) {
            int n = this.zwFollowMapper.update(null, new UpdateWrapper<TZwFollow>()
                    .set("status", AnomalyChain.FOLLOW_DONE)
                    .set("result", "上报已结案归档，盯办结束")
                    .set("finish_by", AnomalyChain.SYSTEM)
                    .set("finish_time", now)
                    .eq("id", f.getId())
                    .eq("status", AnomalyChain.FOLLOW_WAIT)
                    .eq("del_flag", 0));
            if (n == 0) {
                throw new IllegalStateException("盯办条已被并发了结：" + f.getId());
            }
        }
        // 只结不开：结案之后没有下一条
    }

    @Override
    public List<TZwFollow> listOfReport(Long reportId) {        return this.zwFollowMapper.selectList(new QueryWrapper<TZwFollow>()
                .eq("report_id", reportId)
                .eq("del_flag", 0)
                .orderByAsc("step_no")
                .orderByAsc("seq_no")
                .orderByAsc("id"));
    }

    @Override
    public List<TZwFollow> selectList(Wrapper<TZwFollow> queryWrapper) {
        return this.zwFollowMapper.selectList(queryWrapper);
    }

    // ------------------------------------------------------------------

    /**
     * 在指定步次开下一条盯办：seq 顺本步已有点数 +1（漏盯也占号）；
     * prev_follow_at 取本单上一条已结条的了结时刻（首盯为空）；
     * due_at 从开条时刻按本步周期顺排。联系人快照只在"反映供水户"步取上报单上的钉版。
     */
    private void openNext(Long reportId, int stepNo, TZwFollowTemplate tpl, Date now) {
        Integer seq = this.zwFollowMapper.selectCount(new QueryWrapper<TZwFollow>()
                .eq("report_id", reportId)
                .eq("step_no", stepNo)
                .eq("del_flag", 0));
        TZwFollow last = this.zwFollowMapper.selectOne(new QueryWrapper<TZwFollow>()
                .eq("report_id", reportId)
                .eq("del_flag", 0)
                .ne("status", AnomalyChain.FOLLOW_WAIT)
                .orderByDesc("finish_time")
                .orderByDesc("id")
                .last("limit 1"));

        TZwFollow f = new TZwFollow();
        f.setReportId(reportId);
        f.setStepNo(stepNo);
        f.setSeqNo((seq == null ? 0 : seq.intValue()) + 1);
        f.setOwnerLogin(tpl.getOwnerLogin());
        f.setDueAt(AnomalyChain.plusDays(now, tpl.getDueDays() == null ? 0 : tpl.getDueDays()));
        f.setPrevFollowAt(last == null ? null : last.getFinishTime());
        if (tpl.getTargetContact() != null && tpl.getTargetContact().intValue() == 1) {
            TZwAnomReport report = this.zwAnomReportMapper.selectById(reportId);
            if (report != null) {
                f.setTargetAccountName(report.getAccountName());
                f.setTargetContactName(report.getContactName());
                f.setTargetContactPhone(report.getContactPhone());
            }
        }
        f.setStatus(AnomalyChain.FOLLOW_WAIT);
        f.setDelFlag(0);
        // 盯办行只由系统开：显式写创建者/时刻，避开自动填充在定时任务无线程登录上下文时取人
        f.setCreateBy(AnomalyChain.SYSTEM);
        f.setCreateTime(now);
        this.zwFollowMapper.insert(f);
    }

    private static boolean int1(Integer v) {
        return v != null && v.intValue() == 1;
    }

    private static TZwFollow reject(List<String> rejectMsg, String msg) {
        if (rejectMsg != null) {
            rejectMsg.clear();
            rejectMsg.add(msg);
        }
        return null;
    }
}
