package com.fc.v2.service;

import java.util.List;

import com.fc.v2.model.auto.TZwRetest;
import com.fc.v2.model.auto.TZwRetestNode;
import com.fc.v2.service.zw.RetestView;

/**
 * 复测 Service接口。复测单只由系统在上报落账后自动开出（{@link #createForReport}），
 * 页面没有建单/撤单/改名的口子。
 *
 * <p>固定四环顺序：采样复测→化验复核→水质科判定→主管封卷。每环只给该环绑定的登录名开写权
 * （zw_retest_node_owner 字典）；首环实录四件套，其后每环必须照抄上一环结果，服务端逐字比对；
 * 提交后本环只读；上一环没填完或结论否决，下一环不给开。
 *
 * @author fuce
 * @date 2026-10-03
 */
public interface ITZwRetestService {

    /** 系统从上报单自动开出首轮复测并开首环（首轮已存在则幂等返回；已否决轮不算，另走 startNewRound） */
    TZwRetest createForReport(Long reportId, Long anomId, String siteNo, String itemCode, String operator);

    /** 各轮复测单（按轮次正序，旧否决轮原样留着） */
    List<TZwRetest> listRounds(Long reportId);

    /**
     * 某环否决卡停后另起一轮（旧轮台账原样留痕、不删不并）：只有最近一轮处于否决态才准开；
     * 新轮 round_no+1，从首环重新照四环填。返回新一轮复测单。
     */
    TZwRetest startNewRound(Long reportId, Long anomId, String siteNo, String itemCode,
                            String operator, List<String> rejectMsg);

    /** 按主键回查 */
    TZwRetest selectById(Long id);

    /** 环节序绑定的经办人登录名（系统按环节开通写权的唯一出处） */
    String ownerOfNode(int nodeNo);

    /**
     * 提交本环节：四件套一样不许预填（后环的照抄三件套也由人手录，服务端与上一环逐字比对）；
     * 只能提交"当前应填环"、且登录名必须是该环经办人；PASS 开满下一环，最后一环 PASS 封单；
     * FAIL 本环记否决、复测卡停、后续环不开。任何已提交环再提交一律 0 行被拒。
     */
    TZwRetest fillNode(Long retestId, String testItem, java.math.BigDecimal testQty,
                       String testAtText, String conclusion, String opinion,
                       String operator, List<String> rejectMsg);

    /** 倒查视图：四环台账 + 当前应填环 + 当前登录人对各环只读/可填，全顺台账一次点算 */
    RetestView buildView(Long retestId, String loginName);

    /** 复测单下全部环节台账（报表/明细回退用） */
    List<TZwRetestNode> listNodes(Long retestId);
}
