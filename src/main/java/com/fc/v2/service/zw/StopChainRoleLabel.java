package com.fc.v2.service.zw;

import org.springframework.stereotype.Component;

/**
 * 落名口代号到中文口名的唯一对照（页面倒查陈列用）。
 *
 * <p>页面只做代号到名的回显，不允许在此之外自行解释落名口；
 * 名字集合与 {@link StopChain} 角色常量一一对应。
 *
 * @author fuce
 * @date 2026-09-30
 */
@Component("stopChainRoleLabel")
public class StopChainRoleLabel {

    public String label(String role) {
        if (role == null) {
            return "";
        }
        if (StopChain.ROLE_BUSINESS.equals(role)) {
            return "业务经办";
        }
        if (StopChain.ROLE_WATER_QUA.equals(role)) {
            return "水质核实";
        }
        if (StopChain.ROLE_DISP_QTY.equals(role)) {
            return "水量调配评估";
        }
        if (StopChain.ROLE_DISP_NET.equals(role)) {
            return "管网影响评估";
        }
        if (StopChain.ROLE_DISP_CHIEF.equals(role)) {
            return "调度主管";
        }
        if (StopChain.ROLE_APPLICANT.equals(role)) {
            return "申请人撤回";
        }
        return role;
    }
}
