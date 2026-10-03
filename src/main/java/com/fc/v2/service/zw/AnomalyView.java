package com.fc.v2.service.zw;

import java.util.List;

import com.fc.v2.model.auto.TZwAnomReport;
import com.fc.v2.model.auto.TZwAnomaly;
import com.fc.v2.model.auto.TZwImpRow;
import com.fc.v2.model.auto.TZwLimRule;
import com.fc.v2.model.auto.TZwReportAction;

/**
 * 异常登记下钻视图：登记快照 + 当日原始检测行 + 当天标准线条文 + 所挂上报单 + 上报动作台账。
 *
 * <p>页面只读这一份：从界面点进来就能看到当日原始检测记录、当天生效的标准、判定的完整过程，
 * 以及上报之后每一步的经办流水（动作台账按时刻倒序陈列时由页面反转，不另存数）。
 *
 * @author fuce
 * @date 2026-10-03
 */
public class AnomalyView {

    private final TZwAnomaly anomaly;
    private final TZwImpRow impRow;
    private final TZwLimRule rule;
    private final TZwAnomReport report;
    private final List<TZwReportAction> actions;

    public AnomalyView(TZwAnomaly anomaly, TZwImpRow impRow, TZwLimRule rule,
                       TZwAnomReport report, List<TZwReportAction> actions) {
        this.anomaly = anomaly;
        this.impRow = impRow;
        this.rule = rule;
        this.report = report;
        this.actions = actions;
    }

    public TZwAnomaly getAnomaly() {
        return anomaly;
    }

    public TZwImpRow getImpRow() {
        return impRow;
    }

    public TZwLimRule getRule() {
        return rule;
    }

    public TZwAnomReport getReport() {
        return report;
    }

    public List<TZwReportAction> getActions() {
        return actions;
    }
}
