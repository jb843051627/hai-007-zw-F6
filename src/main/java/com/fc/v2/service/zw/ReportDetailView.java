package com.fc.v2.service.zw;

import java.util.List;

import com.fc.v2.model.auto.TZwAnomaly;
import com.fc.v2.model.auto.TZwAnomReport;
import com.fc.v2.model.auto.TZwFollow;
import com.fc.v2.model.auto.TZwReportAction;

/**
 * 上报单全链下钻视图：上报单 + 异常登记 + 动作台账 + 复测倒查 + 盯办流水。
 *
 * <p>从最后一步回退到第一步时逐条与经办屏对账，看的就是这一份：动作先后只认台账 seq_no，
 * 复测四环与盯办各次都是只追加原行，页面不另存、不自算。
 *
 * @author fuce
 * @date 2026-10-03
 */
public class ReportDetailView {

    private final TZwAnomReport report;
    private final TZwAnomaly anomaly;
    private final List<TZwReportAction> actions;
    private final RetestView retest;
    private final List<TZwFollow> follows;

    public ReportDetailView(TZwAnomReport report, TZwAnomaly anomaly,
                            List<TZwReportAction> actions, RetestView retest,
                            List<TZwFollow> follows) {
        this.report = report;
        this.anomaly = anomaly;
        this.actions = actions;
        this.retest = retest;
        this.follows = follows;
    }

    public TZwAnomReport getReport() {
        return report;
    }

    public TZwAnomaly getAnomaly() {
        return anomaly;
    }

    public List<TZwReportAction> getActions() {
        return actions;
    }

    public RetestView getRetest() {
        return retest;
    }

    public List<TZwFollow> getFollows() {
        return follows;
    }
}
