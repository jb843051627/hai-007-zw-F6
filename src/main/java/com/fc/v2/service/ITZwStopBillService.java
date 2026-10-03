package com.fc.v2.service;

import java.util.List;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.fc.v2.model.auto.TZwStopBill;
import com.fc.v2.service.zw.StopBillView;

/**
 * 停供/限供申请签核单 Service接口（approval-chain 形状：四道关口顺序签核，无增删改查入口）。
 *
 * <p>关口固定为：业务科提出 → 水质科核实 → 调度中心评估（水量调配、管网影响两名点齐）→ 调度主管批准。
 * 推进只认关口次序，越级不算；任何关口写明不同意或撤回，单到此为止；主管打回则旧轮签核整轮作废，
 * 从第二道重新攒起；批准后水源点底档回写退役。页面只读本服务给回的单据结论与
 * {@link StopBillView} 关口视图，不设任何旁路。
 *
 * @author fuce
 * @date 2026-09-14
 */
public interface ITZwStopBillService {

    /** 按主键回查单据 */
    TZwStopBill selectTZwStopBillById(Long id);

    /** 台账查询 */
    List<TZwStopBill> selectTZwStopBillList(Wrapper<TZwStopBill> queryWrapper);

    /**
     * 头一道：业务科提出申请。水源点代号、停水事由（DEPLETED/OVER_LIMIT/REPAIR）、
     * 停水后替代供水方案三样缺一当场挡回（返回 null）。公告文案按事由从名录带出，
     * 单号带出点位当年第几份申请。提交即落成第一道签核，单据停在第二道（水质科核实）。
     */
    TZwStopBill submit(String siteNo, String reason, String altPlan, String applicant, String opinion);

    /**
     * 老签法（入参与返回照旧）：当前关口的同意签批，适用于单角色口——
     * 第二道水质科核实、第四道调度主管批准。返回更新后的单据；被拒返回 null。
     * 第三道调度评估有两名评估人，须走带落名口的同名重载。
     */
    TZwStopBill approve(Long id, String approver, String comment);

    /** 否决（写明不同意）：单到此为止；被拒返回 null */
    TZwStopBill reject(Long id, String approver, String comment);

    /**
     * 老"退回上一环节"（入参与返回照旧）。新章下唯一合法退回是主管打回（{@link #sendBack}），
     * 且必须落主管名；无落名人的老退回一律被拒，返回 null。
     */
    TZwStopBill rollback(Long id, String comment);

    /**
     * 另一种签法（同名重载）：第三道调度中心评估口两名评估人各按落名口签——
     * DISP_QTY 看水量调配、DISP_NET 看管网影响，各评各的、互不替签，同一人不许把两枚名都落了。
     * 只到一枚单仍停在本口；两枚点齐才进主管批准口。被拒返回 null。
     */
    TZwStopBill approve(Long id, String approver, String signRole, String comment);

    /** 调度评估口写明不同意（须带落名口）：单到此为止；被拒返回 null */
    TZwStopBill reject(Long id, String approver, String signRole, String comment);

    /** 申请人撤回：单到此为止，之后谁也不往它身上追记；被拒返回 null */
    TZwStopBill withdraw(Long id, String applicant, String comment);

    /**
     * 主管认为理由不符把单打回：当前轮各关签核整轮作废（原样留痕、不删不并），
     * 补签另起新一轮，单据退回第二道（水质科核实）重新攒起。仅主管批准口可打回；被拒返回 null。
     */
    TZwStopBill sendBack(Long id, String approver, String comment);

    /**
     * 倒查视图：四道关口过没过、每口应到/已落几枚名，全部顺着当前轮签核台账一次点算；
     * 作废轮落印分另案陈列。屏上摆的与本方法回的同一份，不许是两份。
     */
    StopBillView buildView(Long id);
}
