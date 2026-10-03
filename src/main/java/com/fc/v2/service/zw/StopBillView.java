package com.fc.v2.service.zw;

import java.util.ArrayList;
import java.util.List;

import com.fc.v2.model.auto.TZwStopBill;
import com.fc.v2.model.auto.TZwStopSign;

/**
 * 停供单的签核倒查视图：单据 + 四道关口现况 + 当前轮逐笔 + 作废旧轮逐笔。
 *
 * <p>关口过没过、每口落名枚数，全部由服务层顺着签核台账一次点算后填入——
 * 页面只读这一份视图，不另设计数、不另设状态。作废轮与补签轮分两摆陈列，绝不混排。
 *
 * @author fuce
 * @date 2026-09-30
 */
public class StopBillView {

    private final TZwStopBill bill;
    private final List<GateView> gates;
    /** 当前轮各关口的落印，按关口、时刻排 */
    private final List<TZwStopSign> currentLines;
    /** 主管打回后作废的旧轮落印，原样留痕、不与补签摆在一起 */
    private final List<TZwStopSign> voidedLines;

    public StopBillView(TZwStopBill bill, List<GateView> gates,
                        List<TZwStopSign> currentLines, List<TZwStopSign> voidedLines) {
        this.bill = bill;
        this.gates = gates == null ? new ArrayList<GateView>() : gates;
        this.currentLines = currentLines == null ? new ArrayList<TZwStopSign>() : currentLines;
        this.voidedLines = voidedLines == null ? new ArrayList<TZwStopSign>() : voidedLines;
    }

    public TZwStopBill getBill() {
        return bill;
    }

    public List<GateView> getGates() {
        return gates;
    }

    public List<TZwStopSign> getCurrentLines() {
        return currentLines;
    }

    public List<TZwStopSign> getVoidedLines() {
        return voidedLines;
    }
}
