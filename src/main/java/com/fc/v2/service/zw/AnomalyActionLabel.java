package com.fc.v2.service.zw;

import org.springframework.stereotype.Component;

/**
 * 上报处置动作代号到中文名的唯一对照（动作台账倒查陈列用）。
 *
 * <p>库里落英文代号（稳定、不随文案改），页面只通过本组件回显中文；
 * 动作集合与 {@link AnomalyChain} 的 ACT_* 常量一一对应。
 *
 * @author fuce
 * @date 2026-10-03
 */
@Component("anomalyActionLabel")
public class AnomalyActionLabel {

    public String label(String action) {
        if (action == null) {
            return "";
        }
        if (AnomalyChain.ACT_REPORT.equals(action)) {
            return "异常上报";
        }
        if (AnomalyChain.ACT_REFLECT.equals(action)) {
            return "反映供水户";
        }
        if (AnomalyChain.ACT_ADVANCE.equals(action)) {
            return "处置步推进";
        }
        if (AnomalyChain.ACT_RETEST_OPEN.equals(action)) {
            return "复测开出";
        }
        if (AnomalyChain.ACT_RETEST_DONE.equals(action)) {
            return "复测四环走完";
        }
        if (AnomalyChain.ACT_RETEST_VETO.equals(action)) {
            return "复测否决卡停";
        }
        if (AnomalyChain.ACT_CLOSE.equals(action)) {
            return "结案归档";
        }
        if (AnomalyChain.ACT_CLOSE_ROLLBACK.equals(action)) {
            return "结案回退";
        }
        if (AnomalyChain.ACT_STEP_BACK.equals(action)) {
            return "处置步退回";
        }
        return action;
    }
}
