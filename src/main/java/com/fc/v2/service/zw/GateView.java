package com.fc.v2.service.zw;

import java.util.ArrayList;
import java.util.List;

import com.fc.v2.model.auto.TZwStopSign;

/**
 * 一道关口的视图：应到枚数、已落枚数、过没过，全是顺着签核台账一次点算出来的。
 *
 * <p>页面没有任何让人代填枚数的格子，{@link #signed}/{@link #passed} 与库内计数出自同一次点算，
 * 少一笔 {@link #passed} 就不成立。当前关口之外的关口，是否已过由单据所在道次裁定。
 *
 * @author fuce
 * @date 2026-09-30
 */
public class GateView {

    private final int node;
    private final String nodeName;
    private final int need;
    private final int signed;
    /** 该口已点满（当前轮） */
    private final boolean full;
    /** 单此刻正停在这一口 */
    private final boolean current;
    /** 这一口已经走完（由服务层按所在道次与报批情形裁定，视图不自判） */
    private final boolean passed;
    /** 当前轮该口的逐笔落印，供倒查与经办屏逐条对账 */
    private final List<TZwStopSign> lines;

    public GateView(int node, String nodeName, int need, int signed,
                    boolean current, boolean passed, List<TZwStopSign> lines) {
        this.node = node;
        this.nodeName = nodeName;
        this.need = need;
        this.signed = signed;
        this.full = signed >= need;
        this.current = current;
        this.passed = passed;
        this.lines = lines == null ? new ArrayList<TZwStopSign>() : lines;
    }

    public int getNode() {
        return node;
    }

    public String getNodeName() {
        return nodeName;
    }

    public int getNeed() {
        return need;
    }

    public int getSigned() {
        return signed;
    }

    public boolean isFull() {
        return full;
    }

    public boolean isCurrent() {
        return current;
    }

    public boolean isPassed() {
        return passed;
    }

    public List<TZwStopSign> getLines() {
        return lines;
    }
}
