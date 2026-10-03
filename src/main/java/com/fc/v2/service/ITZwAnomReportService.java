package com.fc.v2.service;

import java.util.List;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.fc.v2.model.auto.TZwAnomReport;
import com.fc.v2.service.zw.ReportDetailView;
import com.fc.v2.service.zw.RetestView;

import java.math.BigDecimal;

/**
 * 水质异常上报处置 Service接口（整条异常链的编排门面）。
 *
 * <p>规矩：
 * <ul>
 *   <li>同一笔异常只能上报一次（uk_anom 物理兜底），同点位同日同指标第二单整单不成立
 *       （uk_site_day_item）；报过台账只读，没有改、撤、改名的口子；</li>
 *   <li>反映到供水户名下时取联系人并钉快照，反映后前段（登记判定、上报内容）锁定；</li>
 *   <li>需要复测的自动开复测单（无旁路）；复测全环走完才允许推进到"待结案"，
 *       复测否决则卡在反映后/复测回程步，需另走反映信息订正或重新反映；</li>
 *   <li>结案即归档：以归档那一次写下的为准；已结案可从最后一步退回上一步（只追加动作台账），
 *       只追加台账/快照/复测环节/盯办流水原样留着，只回退处置步与上报单状态并重开盯办。</li>
 * </ul>
 *
 * @author fuce
 * @date 2026-10-03
 */
public interface ITZwAnomReportService {

    /** 台账查询 */
    List<TZwAnomReport> selectList(Wrapper<TZwAnomReport> queryWrapper);

    /**
     * 异常上报：同笔异常只能报一次；同点位同日同指标第二单整单不成立。
     * 需要复测的同事务自动开复测单，并开内部处置步盯办第一条。
     */
    TZwAnomReport report(Long anomId, String operator, List<String> rejectMsg);

    /**
     * 反映到供水户名下：从 t_zw_contact 取该点位当前可用联系人快照到单上，
     * 同时把异常登记 reflected=1（更正口随之锁死）；自此刻起前段不可倒改。
     * 反映信息填错允许在同一在办步订正重映一次（仅联系人快照与 reflected 留痕，不新增单）。
     */
    TZwAnomReport reflect(Long reportId, Long contactId, String operator, List<String> rejectMsg);

    /**
     * 复测四环全部通过后处置步由系统自动推进到"复测回程"（在复测填报同事务内完成，无手推口）；
     * 没走完（含否决）停在原地。盯办同步换步（2 天紧盯切 3 天回程盯办）。
     * 推进到最后一步"待结案"：复测回程步之后进入；本单无需复测的，反映后可直接进入。
     */
    TZwAnomReport toClose(Long reportId, String operator, List<String> rejectMsg);

    /**
     * 结案归档：必须在最后一步（待结案）；归档那一次条件更新写下结论、status=1，
     * 同时把异常登记 closed/archived=1、复测若在办则拒、盯办全部收口。
     * 登记/上报/归档同时被改时，以本方法这一次条件更新为准，日志同刻翻得到。
     */
    TZwAnomReport close(Long reportId, String opinion, String operator, List<String> rejectMsg);

    /**
     * 回退上一步：已结案的先回到"待结案"（同时解除结案锁、重开盯办）；在办的从当前步退回一格。
     * 只追加动作台账，快照/复测环节/盯办旧条原样留痕；已反映供水户这一事实不随回退撤销
     （登记更正口仍锁死）。可一格格从最后一步退回第一步。
     */
    TZwAnomReport rollbackClose(Long reportId, String reason, String operator, List<String> rejectMsg);

    /** 上报单下动作台账（正序） */
    List<com.fc.v2.model.auto.TZwReportAction> listActions(Long reportId);

    /**
     * 复测环节填报的唯一入口（门面）：本环节写权与照抄比对全在复测服务内；
     * 填完若复测走到终态，本方法在同一事务接续：全环走完则从"反映后"推进到"复测回程步"
     * 并落 RETEST_DONE 动作、换盯办步；否决则落 RETEST_VETO 动作、处置步不往前。
     */
    RetestView fillRetestNode(Long retestId, String testItem, BigDecimal testQty, String testAtText,
                              String conclusion, String opinion, String operator,
                              List<String> rejectMsg);

    /**
     * 复测最近一轮被否决卡停后，另起一轮（旧轮四环原样留痕，新轮从首环重填），
     * 落 RETEST_OPEN 动作台账。最近一轮不是否决态一律拒。
     */
    RetestView retryRetest(Long reportId, String operator, List<String> rejectMsg);

    /** 全链下钻视图：上报单 + 动作台账 + 复测视图 + 盯办流水 + 联系人 */
    ReportDetailView buildView(Long reportId, String loginName);
}
