package com.fc.v2.service.zw;

import java.util.List;

import com.fc.v2.model.auto.TZwRetest;
import com.fc.v2.model.auto.TZwRetestNode;

/**
 * 复测倒查视图：复测单 + 四环逐环台账 + 当前应填环 + 当前登录人对每环可不可填。
 *
 * <p>页面只读这一份：哪环谁填的、什么时刻、照抄对没对上、当前卡在哪一环，
 * 全由服务层顺着只追加台账点算，页面不自算、不预填。
 *
 * @author fuce
 * @date 2026-10-03
 */
public class RetestView {

    private final TZwRetest retest;
    private final List<TZwRetest> rounds;
    private final List<NodeLine> nodes;
    /** 当前应填环（-1=已封单/卡停，没有可填环） */
    private final int currentNode;
    /** 最近一轮是否否决卡停（页面据此亮"另起一轮"，只此一个口） */
    private final boolean latestVetoed;

    public RetestView(TZwRetest retest, List<TZwRetest> rounds, List<NodeLine> nodes,
                      int currentNode, boolean latestVetoed) {
        this.retest = retest;
        this.rounds = rounds;
        this.nodes = nodes;
        this.currentNode = currentNode;
        this.latestVetoed = latestVetoed;
    }

    public TZwRetest getRetest() {
        return retest;
    }

    public List<TZwRetest> getRounds() {
        return rounds;
    }

    public List<NodeLine> getNodes() {
        return nodes;
    }

    public int getCurrentNode() {
        return currentNode;
    }

    public boolean isLatestVetoed() {
        return latestVetoed;
    }

    /** 一环一行：台账原行 + 当前登录人在本环是否有写权（服务层点算，页面只照亮） */
    public static class NodeLine {
        private final TZwRetestNode node;
        private final boolean writableByMe;

        public NodeLine(TZwRetestNode node, boolean writableByMe) {
            this.node = node;
            this.writableByMe = writableByMe;
        }

        public TZwRetestNode getNode() {
            return node;
        }

        public boolean isWritableByMe() {
            return writableByMe;
        }
    }
}
