package com.fc.v2.service.impl;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.fc.v2.mapper.auto.TZwRetestMapper;
import com.fc.v2.mapper.auto.TZwRetestNodeMapper;
import com.fc.v2.model.auto.TSysDictData;
import com.fc.v2.model.auto.TZwRetest;
import com.fc.v2.model.auto.TZwRetestNode;
import com.fc.v2.service.ITSysDictDataService;
import com.fc.v2.service.ITZwRetestService;
import com.fc.v2.service.zw.AnomalyChain;
import com.fc.v2.service.zw.ImpSampleTime;
import com.fc.v2.service.zw.RetestView;
import com.fc.v2.service.zw.WaterGrade;

/**
 * 复测 Service业务层处理（只管复测单与其只追加环节台账）。
 *
 * <p><b>按环节开通写权</b>：每环经办人取自字典 zw_retest_node_owner（dict_value=环节序、
 * remark=登录名），建单/开环时钉进 owner_login。提交时登录名对不上，整笔不收；
 * 页面据此只给本人亮填格，其他人点进来整环只读。
 *
 * <p><b>逐环照抄、一样不许预填</b>：首环四件套（项目/数值/时间/结论）全由本环人手录；
 * 其后每环开环时，系统把上一环的三件套钉进 prev_* 列（只作比对准据，不回填到填格），
 * 提交人必须自己再填一遍且逐字相等——漏填、对不上都不收。提交走条件更新
 * （行存在且 status=0），提交后本环即只读；PASS 才开下一环，FAIL 复测卡停。
 *
 * @author fuce
 * @date 2026-10-03
 */
@Service
public class TZwRetestServiceImpl implements ITZwRetestService {

    @javax.annotation.Resource
    private TZwRetestMapper zwRetestMapper;
    @javax.annotation.Resource
    private TZwRetestNodeMapper zwRetestNodeMapper;
    @javax.annotation.Resource
    private ITSysDictDataService sysDictDataService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TZwRetest createForReport(Long reportId, Long anomId, String siteNo, String itemCode,
                                     String operator) {
        List<TZwRetest> rounds = listRounds(reportId);
        if (!rounds.isEmpty()) {
            // 首轮幂等（上报事务重试/并发开出）
            return rounds.get(rounds.size() - 1);
        }
        return insertRound(reportId, anomId, siteNo, itemCode, 1, operator);
    }

    @Override
    public List<TZwRetest> listRounds(Long reportId) {
        return this.zwRetestMapper.selectList(new QueryWrapper<TZwRetest>()
                .eq("report_id", reportId)
                .eq("del_flag", 0)
                .orderByAsc("round_no"));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TZwRetest startNewRound(Long reportId, Long anomId, String siteNo, String itemCode,
                                   String operator, List<String> rejectMsg) {
        List<TZwRetest> rounds = listRounds(reportId);
        if (rounds.isEmpty()) {
            return reject(rejectMsg, "尚无首轮复测，不能另起轮次");
        }
        TZwRetest last = rounds.get(rounds.size() - 1);
        if (last.getStatus() == null
                || last.getStatus().intValue() != AnomalyChain.RETEST_VETOED) {
            return reject(rejectMsg, "最近一轮未被否决，不许另起一轮（在办/已走完都不能重开）");
        }
        return insertRound(reportId, anomId, siteNo, itemCode,
                (last.getRoundNo() == null ? 1 : last.getRoundNo().intValue()) + 1, operator);
    }

    /** 插一轮复测（uk_zw_retest_round 兜底并发重轮）并开首环；旧轮原样留着 */
    private TZwRetest insertRound(Long reportId, Long anomId, String siteNo, String itemCode,
                                  int roundNo, String operator) {
        TZwRetest r = new TZwRetest();
        r.setRetestNo(siteNo + "-" + reportId.toString() + "-RT" + (roundNo <= 1 ? "" : "-R" + roundNo));
        r.setReportId(reportId);
        r.setAnomId(anomId);
        r.setSiteNo(siteNo);
        r.setItemCode(itemCode);
        r.setRoundNo(roundNo);
        r.setNodeCount(AnomalyChain.NODE_COUNT);
        r.setCurrentNode(AnomalyChain.NODE_SAMPLE);
        r.setStatus(AnomalyChain.RETEST_WAIT_FIRST);
        r.setDelFlag(0);
        try {
            this.zwRetestMapper.insert(r);
        } catch (DataIntegrityViolationException dup) {
            List<TZwRetest> rounds = listRounds(reportId);
            return rounds.isEmpty() ? null : rounds.get(rounds.size() - 1);
        }
        // 首环开环：四件套一律空着，等本环节经办人自己填
        openNodeReloaded(r, AnomalyChain.NODE_SAMPLE, null);
        return r;
    }

    @Override
    public TZwRetest selectById(Long id) {
        return this.zwRetestMapper.selectById(id);
    }

    @Override
    public String ownerOfNode(int nodeNo) {
        TSysDictData d = this.sysDictDataService.selectTSysDictDataList(new QueryWrapper<TSysDictData>()
                .eq("dict_type", AnomalyChain.DICT_NODE_OWNER)
                .eq("dict_value", String.valueOf(nodeNo))
                .eq("status", "0")).stream().findFirst().orElse(null);
        return d == null || d.getRemark() == null ? null : d.getRemark().trim();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TZwRetest fillNode(Long retestId, String testItem, BigDecimal testQty, String testAtText,
                              String conclusion, String opinion, String operator,
                              List<String> rejectMsg) {
        TZwRetest r = this.zwRetestMapper.selectById(retestId);
        if (r == null || int1(r.getDelFlag())) {
            return reject(rejectMsg, "复测单不存在");
        }
        if (r.getStatus() != null
                && (r.getStatus().intValue() == AnomalyChain.RETEST_DONE
                    || r.getStatus().intValue() == AnomalyChain.RETEST_VETOED)) {
            return reject(rejectMsg, "复测已" + (r.getStatus() == AnomalyChain.RETEST_DONE
                    ? "全环走完封单" : "被否决卡停") + "，环节台账只读");
        }
        // 当前应填环 = 唯一一条待填台账行；不由页面传环节序，页面没有越级的旁路
        TZwRetestNode cur = this.zwRetestNodeMapper.selectOne(new QueryWrapper<TZwRetestNode>()
                .eq("retest_id", retestId)
                .eq("status", AnomalyChain.NODE_PENDING)
                .eq("del_flag", 0)
                .last("limit 1"));
        if (cur == null) {
            return reject(rejectMsg, "没有待填环节（已封单或已卡停）");
        }
        // 按环节开通的权限：本环节该谁填、谁不该填
        if (operator == null || !operator.equals(cur.getOwnerLogin())) {
            return reject(rejectMsg, "本环节派给「" + cur.getOwnerLogin() + "」填，你在本环节只读");
        }

        // 四件套一样不许预填、不许缺；数值要落在合理量域，时间只过统一那把尺
        String item = testItem == null ? "" : testItem.trim();
        if (item.isEmpty()) {
            return reject(rejectMsg, "检测项目没填（不许预填，本环节自己录）");
        }
        if (!WaterGrade.validInput(testQty)) {
            return reject(rejectMsg, "检测数值为空或不在合理量域");
        }
        Date testAt = ImpSampleTime.parse(testAtText);
        if (testAt == null) {
            return reject(rejectMsg, "检测时间没填或格式不对（yyyy-MM-dd HH:mm:ss）");
        }
        if (!AnomalyChain.CON_PASS.equals(conclusion) && !AnomalyChain.CON_FAIL.equals(conclusion)) {
            return reject(rejectMsg, "复核结论只能是 确认通过/否决");
        }

        // 后环必须把上一环结果再填一遍：与开环时系统钉的 prev_* 逐字比对
        if (cur.getNodeNo().intValue() > AnomalyChain.NODE_SAMPLE) {
            String miss = comparePrev(cur, item, testQty, testAt);
            if (miss != null) {
                return reject(rejectMsg, miss);
            }
        }

        boolean fail = AnomalyChain.CON_FAIL.equals(conclusion);
        int nodeStatus = fail ? AnomalyChain.NODE_VETOED : AnomalyChain.NODE_SUBMITTED;
        Date now = new Date();
        int n = this.zwRetestNodeMapper.update(null, new UpdateWrapper<TZwRetestNode>()
                .set("test_item", item)
                .set("test_qty", testQty)
                .set("test_at", testAt)
                .set("conclusion", conclusion)
                .set("opinion", opinion == null ? null : opinion.trim())
                .set("status", nodeStatus)
                .set("fill_by", operator)
                .set("fill_time", now)
                .eq("id", cur.getId())
                .eq("status", AnomalyChain.NODE_PENDING)
                .eq("del_flag", 0));
        if (n == 0) {
            return reject(rejectMsg, "本环节已被提交（并发抢先），请刷新查看");
        }

        if (fail) {
            // 否决：复测卡停，后续环不给开
            this.zwRetestMapper.update(null, new UpdateWrapper<TZwRetest>()
                    .set("status", AnomalyChain.RETEST_VETOED)
                    .set("current_node", cur.getNodeNo())
                    .eq("id", retestId)
                    .in("status", AnomalyChain.RETEST_WAIT_FIRST, AnomalyChain.RETEST_RUNNING)
                    .eq("del_flag", 0));
            return this.zwRetestMapper.selectById(retestId);
        }

        int nextNo = cur.getNodeNo().intValue() + 1;
        if (nextNo >= AnomalyChain.NODE_COUNT) {
            // 最后一环 PASS：封单
            int m = this.zwRetestMapper.update(null, new UpdateWrapper<TZwRetest>()
                    .set("status", AnomalyChain.RETEST_DONE)
                    .set("current_node", AnomalyChain.NODE_COUNT)
                    .set("finish_by", operator)
                    .set("finish_time", now)
                    .eq("id", retestId)
                    .ne("status", AnomalyChain.RETEST_DONE)
                    .eq("del_flag", 0));
            if (m == 0) {
                throw new IllegalStateException("复测单已被并发改动，请刷新查看");
            }
        } else {
            // 重取已提交的上一环（取系统钉版），开下一环并钉 prev_*
            TZwRetestNode done = this.zwRetestNodeMapper.selectById(cur.getId());
            openNodeReloaded(r, nextNo, done);
            this.zwRetestMapper.update(null, new UpdateWrapper<TZwRetest>()
                    .set("status", AnomalyChain.RETEST_RUNNING)
                    .set("current_node", nextNo)
                    .eq("id", retestId)
                    .eq("del_flag", 0));
        }
        return this.zwRetestMapper.selectById(retestId);
    }

    @Override
    public RetestView buildView(Long retestId, String loginName) {
        TZwRetest r = this.zwRetestMapper.selectById(retestId);
        if (r == null) {
            return null;
        }
        List<TZwRetest> rounds = listRounds(r.getReportId());
        TZwRetest latest = rounds.isEmpty() ? r : rounds.get(rounds.size() - 1);
        boolean latestVetoed = latest.getStatus() != null
                && latest.getStatus().intValue() == AnomalyChain.RETEST_VETOED;
        List<TZwRetestNode> all = listNodes(retestId);
        int current = -1;
        List<RetestView.NodeLine> lines = new ArrayList<RetestView.NodeLine>();
        for (TZwRetestNode node : all) {
            boolean pending = node.getStatus() != null
                    && node.getStatus().intValue() == AnomalyChain.NODE_PENDING;
            boolean mine = loginName != null && loginName.equals(node.getOwnerLogin());
            if (pending) {
                current = node.getNodeNo().intValue();
            }
            lines.add(new RetestView.NodeLine(node, pending && mine));
        }
        return new RetestView(r, rounds, lines, current, latestVetoed && latest.getId().equals(r.getId()));
    }

    @Override
    public List<TZwRetestNode> listNodes(Long retestId) {
        return this.zwRetestNodeMapper.selectList(new QueryWrapper<TZwRetestNode>()
                .eq("retest_id", retestId)
                .eq("del_flag", 0)
                .orderByAsc("node_no"));
    }

    // ------------------------------------------------------------------

    /** 开一环：owner 按环字典钉，prev_* 由系统钉（不回填到人手填格） */
    private void openNodeReloaded(TZwRetest r, int nodeNo, TZwRetestNode prev) {
        String owner = ownerOfNode(nodeNo);
        TZwRetestNode node = new TZwRetestNode();
        node.setRetestId(r.getId());
        node.setNodeNo(nodeNo);
        // 字典没绑经办人：宁可开不出环，也不放任无主环等人填
        if (owner == null || owner.isEmpty()) {
            throw new IllegalStateException("环节「" + AnomalyChain.nodeName(nodeNo)
                    + "」未在字典 zw_retest_node_owner 绑定经办人，开环中止");
        }
        node.setOwnerLogin(owner);
        node.setStatus(AnomalyChain.NODE_PENDING);
        node.setDelFlag(0);
        if (prev != null) {
            node.setPrevItem(prev.getTestItem());
            node.setPrevQty(prev.getTestQty());
            node.setPrevTestAt(prev.getTestAt());
            node.setPrevConclusion(prev.getConclusion());
        }
        this.zwRetestNodeMapper.insert(node);
    }

    /** 后环照抄比对：项目逐字、数值同精度、检测时间同一刻；任一不等即点名 */
    private static String comparePrev(TZwRetestNode cur, String item, BigDecimal qty, Date at) {
        if (cur.getPrevItem() != null && !cur.getPrevItem().equals(item)) {
            return "本环节填的检测项目「" + item + "」与上一环结果「" + cur.getPrevItem()
                    + "」对不上，照抄确认不过";
        }
        if (cur.getPrevQty() != null && (qty == null || qty.compareTo(cur.getPrevQty()) != 0)) {
            return "本环节填的检测数值「" + qty + "」与上一环结果「" + cur.getPrevQty().toPlainString()
                    + "」对不上，照抄确认不过";
        }
        if (cur.getPrevTestAt() != null && (at == null || at.getTime() != cur.getPrevTestAt().getTime())) {
            return "本环节填的检测时间与上一环结果（" + ImpSampleTime.format(cur.getPrevTestAt())
                    + "）对不上，照抄确认不过";
        }
        return null;
    }

    private static boolean int1(Integer v) {
        return v != null && v.intValue() == 1;
    }

    private static TZwRetest reject(List<String> rejectMsg, String msg) {
        if (rejectMsg != null) {
            rejectMsg.clear();
            rejectMsg.add(msg);
        }
        return null;
    }
}
