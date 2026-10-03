package com.fc.v2.service.impl;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.fc.v2.mapper.auto.TZwAnomReportMapper;
import com.fc.v2.mapper.auto.TZwAnomalyMapper;
import com.fc.v2.mapper.auto.TZwContactMapper;
import com.fc.v2.mapper.auto.TZwReportActionMapper;
import com.fc.v2.model.auto.TZwAnomReport;
import com.fc.v2.model.auto.TZwAnomaly;
import com.fc.v2.model.auto.TZwContact;
import com.fc.v2.model.auto.TZwReportAction;
import com.fc.v2.model.auto.TZwRetest;
import com.fc.v2.service.ITZwAnomReportService;
import com.fc.v2.service.ITZwFollowService;
import com.fc.v2.service.ITZwRetestService;
import com.fc.v2.service.zw.AnomalyChain;
import com.fc.v2.service.zw.ReportDetailView;
import com.fc.v2.service.zw.RetestView;

import java.math.BigDecimal;

/**
 * 水质异常上报处置 Service业务层处理（门面：编排上报/反映/复测/盯办/结案，跨表同事务）。
 *
 * <p><b>一笔异常一单</b>：上报插入靠 uk_zw_report_anom 与 uk_zw_report_site_day_item 双兜底，
 * 重复上报整单回滚；上报后登记 reported=1 条件回写，台账自此只读——页面没有改、撤、改名的口。
 *
 * <p><b>步次只由系统事件推进</b>：上报(0内部处置)→反映供水户(1，紧盯 2 天)→
 * 复测四环全过自动进复测回程(2)→待结案(3)→结案归档。每一步都落只追加动作台账，
 * 并同步换盯办步次；任何推进都带"当前步+在办"条件，并发后到者 0 行。
 *
 * <p><b>归档那一次写下的为准</b>：结案在同一事务条件更新上报单(status 0→1)与登记
 * (closed 0→1, archived 0→1)、收口全部盯办；登记/上报/归档同时被改时，0 行即整笔回滚。
 * 回退只追加一笔、处置步退一格；reported/reflected/archived 三个锁一旦置位永不清除
 * （登记更正口对回退过的单照样锁死），能重走的只有结案之后的处置段。
 *
 * @author fuce
 * @date 2026-10-03
 */
@Service
public class TZwAnomReportServiceImpl implements ITZwAnomReportService {

    private static final int CONTACT_OK = 0;

    @javax.annotation.Resource
    private TZwAnomReportMapper zwAnomReportMapper;
    @javax.annotation.Resource
    private TZwAnomalyMapper zwAnomalyMapper;
    @javax.annotation.Resource
    private TZwContactMapper zwContactMapper;
    @javax.annotation.Resource
    private TZwReportActionMapper zwReportActionMapper;
    @javax.annotation.Resource
    private ITZwRetestService zwRetestService;
    @javax.annotation.Resource
    private ITZwFollowService zwFollowService;

    @Override
    public List<TZwAnomReport> selectList(Wrapper<TZwAnomReport> queryWrapper) {
        return this.zwAnomReportMapper.selectList(queryWrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TZwAnomReport report(Long anomId, String operator, List<String> rejectMsg) {
        TZwAnomaly a = anomId == null ? null : this.zwAnomalyMapper.selectById(anomId);
        if (a == null || int1(a.getDelFlag())) {
            return reject(rejectMsg, "异常登记不存在");
        }
        if (int1(a.getReported())) {
            return reject(rejectMsg, "同一笔异常只能上报一次，该登记已挂上报单「"
                    + reportNoOf(a.getReportId()) + "」");
        }
        if (int1(a.getClosed()) || int1(a.getArchived())) {
            return reject(rejectMsg, "已结案归档的登记不能再上报");
        }
        Date now = new Date();
        int year = AnomalyChain.yearOf(now);
        Integer before = this.zwAnomReportMapper.selectCount(new QueryWrapper<TZwAnomReport>()
                .eq("site_no", a.getSiteNo())
                .eq("bill_year", year)
                .eq("del_flag", 0));
        int seq = (before == null ? 0 : before.intValue()) + 1;

        TZwAnomReport r = new TZwAnomReport();
        r.setReportNo(a.getSiteNo() + "-" + year + "-" + seq);
        r.setAnomId(a.getId());
        r.setSiteNo(a.getSiteNo());
        r.setItemCode(a.getItemCode());
        r.setTestDay(a.getTestDay());
        r.setBillYear(year);
        r.setYearSeq(seq);
        r.setGradeLevel(a.getGradeLevel());
        r.setNeedRetest(a.getNeedRetest());
        r.setNeedStop(a.getNeedStop());
        r.setStepNo(AnomalyChain.STEP_INTERNAL);
        r.setStatus(AnomalyChain.REPORT_RUNNING);
        r.setDelFlag(0);
        try {
            this.zwAnomReportMapper.insert(r);
        } catch (DataIntegrityViolationException dup) {
            // 同笔异常第二单 / 同点位同日同指标第二单：整单不成立
            return reject(rejectMsg, "整单不成立：同一笔异常只能报一次，同点位同日同指标也不许报第二单");
        }

        // 登记回写已挂上报单：条件带 reported=0，并发第二单到此 0 行整笔回滚
        int n = this.zwAnomalyMapper.update(null, new UpdateWrapper<TZwAnomaly>()
                .set("reported", 1)
                .set("report_id", r.getId())
                .eq("id", a.getId())
                .eq("reported", 0)
                .eq("del_flag", 0));
        if (n == 0) {
            throw new IllegalStateException("异常已被并发上报，本整单回滚");
        }

        appendAction(r.getId(), AnomalyChain.ACT_REPORT, AnomalyChain.STEP_INTERNAL,
                "异常上报：判" + gradeText(a.getGradeLevel())
                        + (int1(a.getNeedStop()) ? "，须停水" : "")
                        + (int1(a.getNeedRetest()) ? "，须复测" : ""),
                operator, now);

        if (int1(a.getNeedRetest())) {
            // 复测单系统自动从此异常开一条，页面没有建单口
            TZwRetest rt = this.zwRetestService.createForReport(
                    r.getId(), a.getId(), a.getSiteNo(), a.getItemCode(), AnomalyChain.SYSTEM);
            r.setRetestId(rt.getId());
            this.zwAnomReportMapper.update(null, new UpdateWrapper<TZwAnomReport>()
                    .set("retest_id", rt.getId()).eq("id", r.getId()));
            appendAction(r.getId(), AnomalyChain.ACT_RETEST_OPEN, AnomalyChain.STEP_INTERNAL,
                    "复测单「" + rt.getRetestNo() + "」已自动开出，四环顺序填报",
                    AnomalyChain.SYSTEM, now);
        }

        // 第一步盯办系统自己开
        this.zwFollowService.openStep(r.getId(), AnomalyChain.STEP_INTERNAL, now);
        return this.zwAnomReportMapper.selectById(r.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TZwAnomReport reflect(Long reportId, Long contactId, String operator, List<String> rejectMsg) {
        TZwAnomReport r = requireRunning(reportId);
        if (r == null) {
            return reject(rejectMsg, "上报单不存在或已结案归档（只读）");
        }
        int step = r.getStepNo() == null ? 0 : r.getStepNo().intValue();
        boolean first = step == AnomalyChain.STEP_INTERNAL;
        if (!first && step != AnomalyChain.STEP_REFLECTED) {
            return reject(rejectMsg, "已过反映供水户这一步，不能再改反映对象");
        }
        TZwContact c = contactId == null ? null : this.zwContactMapper.selectById(contactId);
        if (c == null || int1(c.getDelFlag()) || c.getStatus() == null
                || c.getStatus().intValue() != CONTACT_OK) {
            return reject(rejectMsg, "联系人不存在或已停用（取号错了先在名册订正，再重新反映）");
        }
        if (!c.getSiteNo().equals(r.getSiteNo())) {
            return reject(rejectMsg, "联系人不属于本单点位「" + r.getSiteNo() + "」，不许张冠李戴");
        }
        // 联系人允许在反映后步随时订正（需求本意：卡住多半是联系人取错/联系不上）；
        // 已开盯办条各自带着当时快照不受影响，新开的盯办条照新联系人钉。

        Date now = new Date();
        UpdateWrapper<TZwAnomReport> uw = new UpdateWrapper<TZwAnomReport>()
                .set("account_no", c.getAccountNo())
                .set("account_name", c.getAccountName())
                .set("contact_id", c.getId())
                .set("contact_name", c.getContactName())
                .set("contact_phone", c.getContactPhone())
                .set("reflect_by", operator)
                .set("reflect_time", now)
                .eq("id", r.getId())
                .eq("status", AnomalyChain.REPORT_RUNNING)
                .eq("del_flag", 0);
        if (first) {
            uw.set("step_no", AnomalyChain.STEP_REFLECTED)
              .in("step_no", AnomalyChain.STEP_INTERNAL);
        } else {
            uw.eq("step_no", AnomalyChain.STEP_REFLECTED);
        }
        int n = this.zwAnomReportMapper.update(null, uw);
        if (n == 0) {
            return reject(rejectMsg, "上报单已被并发推进，请刷新后重试");
        }
        // 反映事实钉一次即永久（回退也不清除）；重复反映不重入异常表
        this.zwAnomalyMapper.update(null, new UpdateWrapper<TZwAnomaly>()
                .set("reflected", 1)
                .eq("id", r.getAnomId())
                .eq("reflected", 0));

        appendAction(r.getId(), AnomalyChain.ACT_REFLECT, AnomalyChain.STEP_REFLECTED,
                (first ? "反映到供水户「" : "订正反映对象为「") + c.getAccountName()
                        + "」，联系人 " + c.getContactName() + " " + c.getContactPhone(),
                operator, now);
        // 反映落账后若复测已四环全过（含回退后重反映、复测先于反映走完两种次序），同事务补推进到
        // 复测回程；否则停在反映后步，等复测四环走完时由 fillRetestNode 自动推进。盯办照最终步开。
        int followStep = AnomalyChain.STEP_REFLECTED;
        if (int1(r.getNeedRetest()) && r.getRetestId() != null) {
            TZwRetest rtNow = this.zwRetestService.selectById(r.getRetestId());
            if (rtNow != null && rtNow.getStatus() != null
                    && rtNow.getStatus().intValue() == AnomalyChain.RETEST_DONE) {
                int moved = this.zwAnomReportMapper.update(null, new UpdateWrapper<TZwAnomReport>()
                        .set("step_no", AnomalyChain.STEP_RETEST_BACK)
                        .eq("id", r.getId())
                        .eq("step_no", AnomalyChain.STEP_REFLECTED)
                        .eq("status", AnomalyChain.REPORT_RUNNING)
                        .eq("del_flag", 0));
                if (moved > 0) {
                    followStep = AnomalyChain.STEP_RETEST_BACK;
                    appendAction(r.getId(), AnomalyChain.ACT_RETEST_DONE, AnomalyChain.STEP_RETEST_BACK,
                            first ? "复测此前已四环全部通过，反映后接续进入复测回程"
                                    : "复测四环已全部通过，订正反映对象后接续进入复测回程",
                            operator, now);
                }
            }
        }
        // 换步即换盯办：反映户步 2 天一盯；已补推进到回程步则开 3 天回程盯办
        this.zwFollowService.openStep(r.getId(), followStep, now);
        return this.zwAnomReportMapper.selectById(r.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TZwAnomReport toClose(Long reportId, String operator, List<String> rejectMsg) {
        TZwAnomReport r = requireRunning(reportId);
        if (r == null) {
            return reject(rejectMsg, "上报单不存在或已结案");
        }
        int step = r.getStepNo() == null ? 0 : r.getStepNo().intValue();
        // 要复测的：必须复测回程步（四环全过自动进）才准往结案走；无需复测的：反映后即可
        boolean needRetest = int1(r.getNeedRetest());
        int from = needRetest ? AnomalyChain.STEP_RETEST_BACK : AnomalyChain.STEP_REFLECTED;
        if (step != from) {
            return reject(rejectMsg, needRetest
                    ? "复测四环还没全部走完（或被否决卡停），进不了待结案"
                    : "还没反映到供水户，进不了待结案");
        }
        int n = this.zwAnomReportMapper.update(null, new UpdateWrapper<TZwAnomReport>()
                .set("step_no", AnomalyChain.STEP_TO_CLOSE)
                .eq("id", r.getId())
                .eq("step_no", from)
                .eq("status", AnomalyChain.REPORT_RUNNING)
                .eq("del_flag", 0));
        if (n == 0) {
            return reject(rejectMsg, "已被并发推进，请刷新查看");
        }
        appendAction(r.getId(), AnomalyChain.ACT_ADVANCE, AnomalyChain.STEP_TO_CLOSE,
                "进入待结案（最后一步）", operator, new Date());
        this.zwFollowService.openStep(r.getId(), AnomalyChain.STEP_TO_CLOSE, new Date());
        return this.zwAnomReportMapper.selectById(r.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TZwAnomReport close(Long reportId, String opinion, String operator, List<String> rejectMsg) {
        String text = opinion == null ? "" : opinion.trim();
        if (text.isEmpty()) {
            return reject(rejectMsg, "结案要写下结论（归档那一次写下的为准）");
        }
        TZwAnomReport r = requireRunning(reportId);
        if (r == null) {
            return reject(rejectMsg, "上报单不存在或已结案归档");
        }
        if (r.getStepNo() == null || r.getStepNo().intValue() != AnomalyChain.STEP_TO_CLOSE) {
            return reject(rejectMsg, "还没走到最后一步（待结案），不能归档结案");
        }
        if (int1(r.getNeedRetest())) {
            TZwRetest rt = r.getRetestId() == null ? null : this.zwRetestService.selectById(r.getRetestId());
            if (rt == null || rt.getStatus() == null
                    || rt.getStatus().intValue() != AnomalyChain.RETEST_DONE) {
                return reject(rejectMsg, "复测未全环走完，不能结案");
            }
        }
        Date now = new Date();
        // 上报单：归档那一次条件更新，0 行即被并发抢先，整笔回滚
        int n = this.zwAnomReportMapper.update(null, new UpdateWrapper<TZwAnomReport>()
                .set("status", AnomalyChain.REPORT_CLOSED)
                .set("close_opinion", text)
                .set("closed_by", operator)
                .set("closed_time", now)
                .eq("id", r.getId())
                .eq("step_no", AnomalyChain.STEP_TO_CLOSE)
                .eq("status", AnomalyChain.REPORT_RUNNING)
                .eq("del_flag", 0));
        if (n == 0) {
            return reject(rejectMsg, "上报单已被并发改动，请刷新后重试");
        }
        // 登记同步结案归档；archived 一旦置位，任何后续回退都不清除（更正口永久锁死）
        int m = this.zwAnomalyMapper.update(null, new UpdateWrapper<TZwAnomaly>()
                .set("closed", 1)
                .set("archived", 1)
                .set("archive_time", now)
                .eq("id", r.getAnomId())
                .eq("reported", 1)
                .eq("closed", 0)
                .eq("del_flag", 0));
        if (m == 0) {
            throw new IllegalStateException("登记结案条件不成立（已结案或未上报），整笔回滚");
        }
        // 盯办系统自己收口，只结不开
        this.zwFollowService.closeAll(r.getId(), now);
        appendAction(r.getId(), AnomalyChain.ACT_CLOSE, AnomalyChain.STEP_TO_CLOSE,
                "结案归档：" + text, operator, now);
        return this.zwAnomReportMapper.selectById(r.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TZwAnomReport rollbackClose(Long reportId, String reason, String operator,
                                       List<String> rejectMsg) {
        String text = reason == null ? "" : reason.trim();
        if (text.isEmpty()) {
            return reject(rejectMsg, "回退要写明原因（逐笔留痕，与当时经办屏对得上）");
        }
        TZwAnomReport r = this.zwAnomReportMapper.selectById(reportId);
        if (r == null || int1(r.getDelFlag())) {
            return reject(rejectMsg, "上报单不存在");
        }
        Date now = new Date();
        int step = r.getStepNo() == null ? 0 : r.getStepNo().intValue();
        boolean wasClosed = r.getStatus() != null
                && r.getStatus().intValue() == AnomalyChain.REPORT_CLOSED;

        int targetStep;
        String act;
        String detail;
        if (wasClosed) {
            // 已结案：先退回"待结案"，解除结案锁（archived 不清除，登记更正照样锁死）
            targetStep = AnomalyChain.STEP_TO_CLOSE;
            act = AnomalyChain.ACT_CLOSE_ROLLBACK;
            detail = "结案回退至待结案：" + text;
        } else {
            if (r.getStatus() == null
                    || r.getStatus().intValue() != AnomalyChain.REPORT_RUNNING) {
                return reject(rejectMsg, "单不在办，不能回退");
            }
            if (step == AnomalyChain.STEP_INTERNAL) {
                return reject(rejectMsg, "已在第一步（内部处置），无处可退；上报本身不可撤");
            }
            // 在办：退回上一步。从待结案退时，要复测的回复测回程、不要复测的回反映后
            if (step == AnomalyChain.STEP_TO_CLOSE) {
                targetStep = int1(r.getNeedRetest())
                        ? AnomalyChain.STEP_RETEST_BACK : AnomalyChain.STEP_REFLECTED;
            } else {
                targetStep = step - 1;
            }
            act = AnomalyChain.ACT_STEP_BACK;
            detail = "处置步由「" + AnomalyChain.stepName(step) + "」退回「"
                    + AnomalyChain.stepName(targetStep) + "」：" + text;
        }

        UpdateWrapper<TZwAnomReport> uw = new UpdateWrapper<TZwAnomReport>()
                .set("status", AnomalyChain.REPORT_RUNNING)
                .set("step_no", targetStep)
                .eq("id", r.getId())
                .eq("del_flag", 0);
        if (wasClosed) {
            uw.eq("status", AnomalyChain.REPORT_CLOSED);
        } else {
            uw.eq("status", AnomalyChain.REPORT_RUNNING).eq("step_no", step);
        }
        int n = this.zwAnomReportMapper.update(null, uw);
        if (n == 0) {
            return reject(rejectMsg, "已被并发改动，请刷新后重试");
        }
        if (wasClosed) {
            // 只解除 closed；archived/reflected/reported 三个锁保持置位，登记仍不可更正
            this.zwAnomalyMapper.update(null, new UpdateWrapper<TZwAnomaly>()
                    .set("closed", 0)
                    .eq("id", r.getAnomId())
                    .eq("closed", 1));
        }
        appendAction(r.getId(), act, targetStep, detail, operator, now);
        // 退回的那一步盯办照该步周期重开（新条 prev_follow_at 仍接旧条，前段流水原样不动）
        this.zwFollowService.openStep(r.getId(), targetStep, now);
        return this.zwAnomReportMapper.selectById(r.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RetestView fillRetestNode(Long retestId, String testItem, BigDecimal testQty,
                                     String testAtText, String conclusion, String opinion,
                                     String operator, List<String> rejectMsg) {
        TZwRetest before = this.zwRetestService.selectById(retestId);
        if (before == null) {
            reject(rejectMsg, "复测单不存在");
            return null;
        }
        TZwRetest after = this.zwRetestService.fillNode(retestId, testItem, testQty, testAtText,
                conclusion, opinion, operator, rejectMsg);
        if (after == null) {
            return null;
        }
        Date now = new Date();
        if (after.getStatus() != null
                && after.getStatus().intValue() == AnomalyChain.RETEST_DONE
                && before.getStatus() != null
                && before.getStatus().intValue() != AnomalyChain.RETEST_DONE) {
            TZwAnomReport cur = this.zwAnomReportMapper.selectById(after.getReportId());
            int curStep = cur == null || cur.getStepNo() == null ? 0 : cur.getStepNo().intValue();
            if (curStep == AnomalyChain.STEP_REFLECTED) {
                // 正常次序：四环全过，自动进复测回程步，换 3 天回程盯办
                int n = this.zwAnomReportMapper.update(null, new UpdateWrapper<TZwAnomReport>()
                        .set("step_no", AnomalyChain.STEP_RETEST_BACK)
                        .eq("id", after.getReportId())
                        .eq("step_no", AnomalyChain.STEP_REFLECTED)
                        .eq("status", AnomalyChain.REPORT_RUNNING)
                        .eq("del_flag", 0));
                appendAction(after.getReportId(), AnomalyChain.ACT_RETEST_DONE,
                        AnomalyChain.STEP_RETEST_BACK,
                        "复测四环全部通过，处置进入复测回程", operator, now);
                if (n > 0) {
                    this.zwFollowService.openStep(after.getReportId(),
                            AnomalyChain.STEP_RETEST_BACK, now);
                }
            } else {
                // 复测在反映供水户之前就走完：只留痕，处置步等反映后补推进
                appendAction(after.getReportId(), AnomalyChain.ACT_RETEST_DONE, curStep,
                        "复测四环已全部通过；尚未反映供水户，处置步待反映后接续", operator, now);
            }
        } else if (after.getStatus() != null
                && after.getStatus().intValue() == AnomalyChain.RETEST_VETOED
                && (before.getStatus() == null
                    || before.getStatus().intValue() != AnomalyChain.RETEST_VETOED)) {
            // 否决：只落台账，处置步不往前（上一环否决，这一环不给开的链内体现）
            appendAction(after.getReportId(), AnomalyChain.ACT_RETEST_VETO,
                    after.getCurrentNode() == null ? null : after.getCurrentNode(),
                    "复测环节被否决，复测卡停；处置步停在原地，需订正后重新反映/复测",
                    operator, now);
        }
        return this.zwRetestService.buildView(retestId, operator);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RetestView retryRetest(Long reportId, String operator, List<String> rejectMsg) {
        TZwAnomReport r = requireRunning(reportId);
        if (r == null) {
            reject(rejectMsg, "上报单不在办，不能另起复测轮次");
            return null;
        }
        List<TZwRetest> rounds = this.zwRetestService.listRounds(reportId);
        TZwRetest last = rounds.isEmpty() ? null : rounds.get(rounds.size() - 1);
        if (last == null || last.getStatus() == null
                || last.getStatus().intValue() != AnomalyChain.RETEST_VETOED) {
            reject(rejectMsg, "最近一轮复测不是否决卡停，不能另起一轮");
            return null;
        }
        TZwRetest nr = this.zwRetestService.startNewRound(reportId, r.getAnomId(),
                r.getSiteNo(), r.getItemCode(), operator, rejectMsg);
        if (nr == null) {
            return null;
        }
        this.zwAnomReportMapper.update(null, new UpdateWrapper<TZwAnomReport>()
                .set("retest_id", nr.getId()).eq("id", r.getId()));
        appendAction(r.getId(), AnomalyChain.ACT_RETEST_OPEN, r.getStepNo(),
                "第" + nr.getRoundNo() + "轮复测单「" + nr.getRetestNo()
                        + "」已开出（前序否决轮原样留痕），从首环重新填报",
                operator, new Date());
        return this.zwRetestService.buildView(nr.getId(), operator);
    }

    @Override
    public List<TZwReportAction> listActions(Long reportId) {
        return this.zwAnomReportMapper.selectById(reportId) == null
                ? new ArrayList<TZwReportAction>()
                : this.zwReportActionMapper.selectList(new QueryWrapper<TZwReportAction>()
                        .eq("report_id", reportId)
                        .eq("del_flag", 0)
                        .orderByAsc("seq_no"));
    }

    @Override
    public ReportDetailView buildView(Long reportId, String loginName) {
        TZwAnomReport r = this.zwAnomReportMapper.selectById(reportId);
        if (r == null) {
            return null;
        }
        TZwAnomaly a = this.zwAnomalyMapper.selectById(r.getAnomId());
        List<TZwReportAction> actions = listActions(reportId);
        RetestView retest = null;
        if (r.getRetestId() != null) {
            retest = this.zwRetestService.buildView(r.getRetestId(), loginName);
        }
        List<com.fc.v2.model.auto.TZwFollow> follows = this.zwFollowService.listOfReport(reportId);
        return new ReportDetailView(r, a, actions, retest, follows);
    }

    // ------------------------------------------------------------------

    private TZwAnomReport requireRunning(Long id) {
        if (id == null) {
            return null;
        }
        TZwAnomReport r = this.zwAnomReportMapper.selectById(id);
        if (r == null || int1(r.getDelFlag())) {
            return null;
        }
        if (r.getStatus() == null || r.getStatus().intValue() != AnomalyChain.REPORT_RUNNING) {
            return null;
        }
        return r;
    }

    /** 动作台账只追加：seq 顺本单已有点数 +1（回退也占新号，绝不改旧笔） */
    private void appendAction(Long reportId, String action, Integer stepNo, String detail,
                              String actor, Date at) {
        Integer cnt = this.zwReportActionMapper.selectCount(new QueryWrapper<TZwReportAction>()
                .eq("report_id", reportId)
                .eq("del_flag", 0));
        TZwReportAction line = new TZwReportAction();
        line.setReportId(reportId);
        line.setSeqNo((cnt == null ? 0 : cnt.intValue()) + 1);
        line.setAction(action);
        line.setStepNo(stepNo);
        line.setDetail(detail == null || detail.length() <= 500 ? detail : detail.substring(0, 500));
        line.setActor(actor);
        line.setActTime(at);
        line.setDelFlag(0);
        this.zwReportActionMapper.insert(line);
    }

    private String reportNoOf(Long reportId) {
        if (reportId == null) {
            return "";
        }
        TZwAnomReport r = this.zwAnomReportMapper.selectById(reportId);
        return r == null ? "" : r.getReportNo();
    }

    private static String gradeText(Integer g) {
        return g == null ? "" : com.fc.v2.service.zw.WaterGrade.gradeText(g.intValue());
    }

    private static boolean int1(Integer v) {
        return v != null && v.intValue() == 1;
    }

    private static TZwAnomReport reject(List<String> rejectMsg, String msg) {
        if (rejectMsg != null) {
            rejectMsg.clear();
            rejectMsg.add(msg);
        }
        return null;
    }
}
