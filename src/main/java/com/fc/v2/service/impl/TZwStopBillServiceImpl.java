package com.fc.v2.service.impl;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.fc.v2.mapper.auto.TZwCaseFlowMapper;
import com.fc.v2.mapper.auto.TZwImpRowMapper;
import com.fc.v2.mapper.auto.TZwSourceMapper;
import com.fc.v2.mapper.auto.TZwStopBillMapper;
import com.fc.v2.mapper.auto.TZwStopSignMapper;
import com.fc.v2.model.auto.TSysDictData;
import com.fc.v2.model.auto.TZwCaseFlow;
import com.fc.v2.model.auto.TZwImpRow;
import com.fc.v2.model.auto.TZwSource;
import com.fc.v2.model.auto.TZwStopBill;
import com.fc.v2.model.auto.TZwStopSign;
import com.fc.v2.service.ITZwStopBillService;
import com.fc.v2.service.ITSysDictDataService;
import com.fc.v2.service.zw.GateView;
import com.fc.v2.service.zw.StopBillView;
import com.fc.v2.service.zw.StopChain;

/**
 * 停供/限供申请签核单 Service业务层处理。
 *
 * <p><b>签核只有一套口径</b>：单子走到哪一关、这一关过没过、落了几枚名，全部由
 * {@link StopChain} 顺着 {@link TZwStopSign} 只追加台账点算——每次动作先在台账落一笔，
 * 再据同一次点算决定走与不走。单据表上没有任何"已落印数"列，界面也没有代填格子，
 * 更没有能印、能撤、能改名的旁路；屏上摆的与库里数的永远同一份。
 *
 * <p>四道关口固定次序：业务提(0) → 水质核(1) → 调度评(2) → 主管准(3)，越级一律被拒。
 * 调度评口两人各按落名口签（DISP_QTY/DISP_NET），同一分钟仍是两笔、同一人不许占两枚名；
 * 只到一枚单停在本口，到不了主管口。主管打回则当前轮签核整轮作废（原样留痕、不删不并），
 * 补签另起新一轮、从第二道攒起。任何一关写明不同意或撤回，单落"已终止"锁死，另开新单才能再走。
 * 主管批准与水源点底档退役在同一事务内钉齐。
 *
 * @author fuce
 * @date 2026-09-14
 */
@Service
public class TZwStopBillServiceImpl implements ITZwStopBillService {

    /** 底档 0在用 1已退役 */
    private static final int SOURCE_ACTIVE = 0;
    private static final int SOURCE_RETIRED = 1;
    /** 处置工单 0未起 1在办 2已封存；前两者即"未完结" */
    private static final int CASE_TERMINAL = 2;
    /** 检测行 0待核 1已入库 2退回 */
    private static final int ROW_OK = 1;

    private static final String NOTICE_DICT_TYPE = "zw_stop_notice";

    @javax.annotation.Resource
    private TZwStopBillMapper zwStopBillMapper;
    @javax.annotation.Resource
    private TZwStopSignMapper zwStopSignMapper;
    @javax.annotation.Resource
    private TZwSourceMapper zwSourceMapper;
    @javax.annotation.Resource
    private TZwCaseFlowMapper zwCaseFlowMapper;
    @javax.annotation.Resource
    private TZwImpRowMapper zwImpRowMapper;
    @javax.annotation.Resource
    private ITSysDictDataService sysDictDataService;

    @Override
    public TZwStopBill selectTZwStopBillById(Long id) {
        return this.zwStopBillMapper.selectById(id);
    }

    @Override
    public List<TZwStopBill> selectTZwStopBillList(Wrapper<TZwStopBill> queryWrapper) {
        return this.zwStopBillMapper.selectList(queryWrapper);
    }

    /**
     * 头一道业务科提出：代号、事由、替代方案三样缺一当场挡回；点位代号须对得上在用底档。
     * 单号带"点位-年-当年序"，同一点位当年第二份当场单独点名（yearSeq=2）；
     * 公告文案按事由从名录带出、用底档名落版，不经人手抄录。
     * 提单即落第一道经办名，单据停在第二道（水质科核实）。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public TZwStopBill submit(String siteNo, String reason, String altPlan, String applicant, String opinion) {
        String site = trim(siteNo);
        String why = trim(reason);
        String plan = trim(altPlan);
        String who = trim(applicant);
        // 三样缺一当场挡回；落名人也不能是空口
        if (site.isEmpty() || why.isEmpty() || plan.isEmpty() || who.isEmpty()) {
            return null;
        }
        if (!StopChain.validReason(why)) {
            return null;
        }
        TZwSource source = this.zwSourceMapper.selectOne(new QueryWrapper<TZwSource>()
                .eq("site_no", site)
                .eq("del_flag", 0));
        // 代号对不上底档、或底档已退役的，不许再提
        if (source == null || source.getStatus() == null || source.getStatus() != SOURCE_ACTIVE) {
            return null;
        }

        Date now = new Date();
        int year = yearOf(now);
        Integer before = this.zwStopBillMapper.selectCount(new QueryWrapper<TZwStopBill>()
                .eq("site_no", site)
                .eq("bill_year", year)
                .eq("del_flag", 0));
        int seq = (before == null ? 0 : before.intValue()) + 1;

        TZwStopBill bill = new TZwStopBill();
        bill.setBillNo(site + "-" + year + "-" + seq);
        bill.setSiteNo(site);
        bill.setBillYear(year);
        bill.setYearSeq(seq);
        bill.setReason(why);
        bill.setAltPlan(plan);
        bill.setNoticeText(loadNotice(why, source.getSiteName()));
        bill.setNodeNo(StopChain.NODE_WATER);
        bill.setRoundNo(1);
        bill.setStatus(StopChain.STATUS_RUNNING);
        bill.setDelFlag(0);
        try {
            this.zwStopBillMapper.insert(bill);
        } catch (DataIntegrityViolationException e) {
            // 并发同年序号撞 uk_stop_bill_no：整笔不算，重新提即可
            return null;
        }
        // 头一道经办名落账（提单即过第一道）
        appendSign(bill.getId(), 1, StopChain.NODE_BUSINESS, StopChain.ROLE_BUSINESS,
                who, StopChain.ACT_AGREE, opinion, now);
        return this.zwStopBillMapper.selectById(bill.getId());
    }

    /**
     * 老签法：只服务单角色口——第二道水质科核实、第四道主管批准。
     * 第三道两名点齐口走此方法一律被拒（另一种签法另立同名重载）；其他越道/终止情形同样被拒。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public TZwStopBill approve(Long id, String approver, String comment) {
        TZwStopBill bill = requireRunning(id, approver);
        if (bill == null) {
            return null;
        }
        int node = bill.getNodeNo();
        String role;
        if (node == StopChain.NODE_WATER) {
            role = StopChain.ROLE_WATER_QUA;
            // 第二道前置：名下有未完结处置单 → 压在这一道，不往前
            if (hasOpenCase(bill.getSiteNo())) {
                return null;
            }
            // 第二道核实：近月检测数据与申请事由对照，理由写错就算没过（没落同意名，单原样压着）
            if (!StopChain.reasonMatchesData(bill.getReason(), loadRecentRows(bill.getSiteNo(), new Date()))) {
                return null;
            }
        } else if (node == StopChain.NODE_CHIEF) {
            role = StopChain.ROLE_DISP_CHIEF;
        } else {
            // 第一道不能补签；第三道两名口必须带落名口走同名重载
            return null;
        }
        if (slotTaken(bill, node, role)) {
            return null;
        }
        try {
            appendSign(bill.getId(), bill.getRoundNo(), node, role, approver.trim(),
                    StopChain.ACT_AGREE, comment, new Date());
        } catch (DataIntegrityViolationException dup) {
            // 并发同口同人落印（uk_stop_sign_slot）：这笔不算，原样拒绝
            return null;
        }

        if (node == StopChain.NODE_CHIEF) {
            // 批准落定与底档退役同一条事务钉齐：单锁死、点退役，缺一样都不算成
            int rows = this.zwStopBillMapper.update(null, new UpdateWrapper<TZwStopBill>()
                    .set("status", StopChain.STATUS_PASS)
                    .eq("id", id)
                    .eq("node_no", StopChain.NODE_CHIEF)
                    .eq("round_no", bill.getRoundNo())
                    .eq("status", StopChain.STATUS_RUNNING)
                    .eq("del_flag", 0));
            if (rows <= 0) {
                // 单已被并发改动：刚落的印连同回滚，不许留一笔悬空名
                throw new IllegalStateException("单据已被并发改动，请刷新后重签");
            }
            retireSource(bill.getSiteNo());
        } else {
            // 第二道点齐进调度评口；0 行说明单已被并发挪走，回滚刚落的印
            if (advance(bill, StopChain.NODE_DISPATCH) <= 0) {
                throw new IllegalStateException("单据已被并发改动，请刷新后重签");
            }
        }
        return this.zwStopBillMapper.selectById(id);
    }

    /**
     * 另一种签法（同名重载）：第三道调度评估两名评估人各按落名口签。
     * 落名口不对、同一人占两枚名、同一口重复签、非本口单，一律被拒。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public TZwStopBill approve(Long id, String approver, String signRole, String comment) {
        TZwStopBill bill = requireRunning(id, approver);
        if (bill == null) {
            return null;
        }
        int node = bill.getNodeNo();
        String who = approver.trim();
        // 此签法只在调度评口作数，别的关口不收
        if (node != StopChain.NODE_DISPATCH || !StopChain.roleAllowedAt(node, signRole)) {
            return null;
        }
        if (slotTaken(bill, node, signRole)) {
            return null;
        }
        // 互不替签：另一枚名若已是同一人，这枚当作代签，当没签
        for (String other : StopChain.rolesOf(node)) {
            if (!other.equals(signRole) && signerAt(bill, node, other) != null
                    && who.equals(signerAt(bill, node, other))) {
                return null;
            }
        }
        try {
            appendSign(bill.getId(), bill.getRoundNo(), node, signRole, who,
                    StopChain.ACT_AGREE, comment, new Date());
        } catch (DataIntegrityViolationException dup) {
            return null;
        }

        // 枚数顺着台账现数：只到一枚，单停在本口；两枚点齐才进主管口
        List<TZwStopSign> here = linesOfRound(bill, node);
        if (!StopChain.gateFull(node, here)) {
            return this.zwStopBillMapper.selectById(id);
        }
        if (advance(bill, StopChain.NODE_CHIEF) <= 0) {
            throw new IllegalStateException("单据已被并发改动，请刷新后重签");
        }
        return this.zwStopBillMapper.selectById(id);
    }

    /** 老否决：单角色口（第二道/第四道）写明不同意，单到此为止锁死。 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public TZwStopBill reject(Long id, String approver, String comment) {
        TZwStopBill bill = requireRunning(id, approver);
        if (bill == null) {
            return null;
        }
        int node = bill.getNodeNo();
        String role;
        if (node == StopChain.NODE_WATER) {
            role = StopChain.ROLE_WATER_QUA;
        } else if (node == StopChain.NODE_CHIEF) {
            role = StopChain.ROLE_DISP_CHIEF;
        } else {
            // 第三道须走带落名口的同名否决
            return null;
        }
        return terminate(bill, node, role, approver.trim(), StopChain.ACT_DISAGREE, comment);
    }

    /** 调度评口否决（带落名口）：单到此为止锁死。 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public TZwStopBill reject(Long id, String approver, String signRole, String comment) {
        TZwStopBill bill = requireRunning(id, approver);
        if (bill == null) {
            return null;
        }
        int node = bill.getNodeNo();
        if (node != StopChain.NODE_DISPATCH || !StopChain.roleAllowedAt(node, signRole)) {
            return null;
        }
        return terminate(bill, node, signRole, approver.trim(), StopChain.ACT_DISAGREE, comment);
    }

    /** 申请人撤回：任何在核关口都可撤，落 WITHDRAW 一笔，单到此为止。 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public TZwStopBill withdraw(Long id, String applicant, String comment) {
        TZwStopBill bill = requireRunning(id, applicant);
        if (bill == null) {
            return null;
        }
        int node = bill.getNodeNo();
        return terminate(bill, node, StopChain.ROLE_APPLICANT, applicant.trim(),
                StopChain.ACT_WITHDRAW, comment);
    }

    /**
     * 主管打回：只在主管批准口、由主管落名。当前轮各关签核整轮作废——
     * 一笔不删、一笔不改，原样留在台账；补签另起新一轮，单据退回第二道从水质核实重新攒起。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public TZwStopBill sendBack(Long id, String approver, String comment) {
        TZwStopBill bill = requireRunning(id, approver);
        if (bill == null) {
            return null;
        }
        if (bill.getNodeNo() != StopChain.NODE_CHIEF) {
            return null;
        }
        int round = bill.getRoundNo();
        if (slotTaken(bill, StopChain.NODE_CHIEF, StopChain.ROLE_DISP_CHIEF)) {
            return null;
        }
        // 打回也落一笔在旧轮末，作废轮为何作废倒查时说得清
        try {
            appendSign(bill.getId(), round, StopChain.NODE_CHIEF, StopChain.ROLE_DISP_CHIEF,
                    approver.trim(), StopChain.ACT_SENDBACK, comment, new Date());
        } catch (DataIntegrityViolationException dup) {
            return null;
        }

        int nextRound = round + 1;
        int rows = this.zwStopBillMapper.update(null, new UpdateWrapper<TZwStopBill>()
                .set("node_no", StopChain.NODE_WATER)
                // 新轮次 + 退回第二道 + 仍在核，三样同一条更新钉齐
                .set("round_no", nextRound)
                .set("status", StopChain.STATUS_RUNNING)
                .eq("id", id)
                .eq("node_no", StopChain.NODE_CHIEF)
                .eq("round_no", round)
                .eq("status", StopChain.STATUS_RUNNING)
                .eq("del_flag", 0));
        if (rows <= 0) {
            throw new IllegalStateException("单据已被并发改动，请刷新后重试");
        }
        return this.zwStopBillMapper.selectById(id);
    }

    /**
     * 老"退回上一环节"签名保留，但新章下唯一合法退回是主管打回（{@link #sendBack}），
     * 必须落主管名；无落名人的老退回无处可退，一律被拒。
     */
    @Override
    public TZwStopBill rollback(Long id, String comment) {
        return null;
    }

    /**
     * 倒查视图：当前轮四关的应到/已落枚数顺着台账一次点算；作废旧轮分另案陈列。
     * 从主管批准一路可回退到业务提出，逐笔与经办屏对得上。
     */
    @Override
    public StopBillView buildView(Long id) {
        TZwStopBill bill = this.zwStopBillMapper.selectById(id);
        if (bill == null) {
            return null;
        }
        List<TZwStopSign> all = this.zwStopSignMapper.selectList(new QueryWrapper<TZwStopSign>()
                .eq("bill_id", id)
                .eq("del_flag", 0)
                .orderByAsc("round_no")
                .orderByAsc("node_no")
                .orderByAsc("sign_time")
                .orderByAsc("id"));
        int round = bill.getRoundNo() == null ? 1 : bill.getRoundNo().intValue();
        int node = bill.getNodeNo() == null ? 0 : bill.getNodeNo().intValue();
        int status = bill.getStatus() == null ? StopChain.STATUS_RUNNING : bill.getStatus().intValue();

        // 当前轮各关口分组
        Map<Integer, List<TZwStopSign>> byNode = new HashMap<Integer, List<TZwStopSign>>();
        List<TZwStopSign> currentLines = new ArrayList<TZwStopSign>();
        List<TZwStopSign> voidedLines = new ArrayList<TZwStopSign>();
        for (TZwStopSign line : all) {
            boolean oldRound = line.getRoundNo() != null && line.getRoundNo().intValue() < round;
            // 打回只作废旧轮第二道起的签核：头一道业务提件不作废，原笔随单带到新轮倒查
            if (oldRound && line.getNodeNo() != null
                    && line.getNodeNo().intValue() != StopChain.NODE_BUSINESS) {
                voidedLines.add(line);
                continue;
            }
            currentLines.add(line);
            List<TZwStopSign> bucket = byNode.get(line.getNodeNo());
            if (bucket == null) {
                bucket = new ArrayList<TZwStopSign>();
                byNode.put(line.getNodeNo(), bucket);
            }
            bucket.add(line);
        }

        List<GateView> gates = new ArrayList<GateView>();
        for (int g = 0; g < StopChain.NODE_COUNT; g++) {
            List<TZwStopSign> lines = byNode.get(Integer.valueOf(g));
            if (lines == null) {
                lines = new ArrayList<TZwStopSign>();
            }
            int signed = StopChain.countAgree(lines);
            boolean current = status == StopChain.STATUS_RUNNING && g == node;
            // 在核：道次之前即过；已核讫：四口全过；已终止：止于终止口，前口过后口未到
            boolean passed = status == StopChain.STATUS_PASS
                    || (status == StopChain.STATUS_RUNNING && g < node)
                    || (status == StopChain.STATUS_STOPPED && g < node);
            // 打回后新轮从第二道攒：第一道无新轮落印，仍按已过陈列（提单内容不作废）
            if (status == StopChain.STATUS_RUNNING && round > 1 && g == StopChain.NODE_BUSINESS) {
                passed = true;
            }
            gates.add(new GateView(g, StopChain.nodeName(g), StopChain.needCount(g),
                    signed, current, passed, lines));
        }
        return new StopBillView(bill, gates, currentLines, voidedLines);
    }

    // ------------------------------------------------------------------

    /** 单据须存在、在核、未删；落名人非空 */
    private TZwStopBill requireRunning(Long id, String approver) {
        if (id == null || trim(approver).isEmpty()) {
            return null;
        }
        TZwStopBill bill = this.zwStopBillMapper.selectById(id);
        if (bill == null || bill.getDelFlag() == null || bill.getDelFlag() != 0) {
            return null;
        }
        if (bill.getStatus() == null || bill.getStatus() != StopChain.STATUS_RUNNING) {
            // 已核讫/已终止各栏锁死，谁也不往它身上追记
            return null;
        }
        return bill;
    }

    /** 落一笔终止动作并把单据锁成"已终止"，状态与落印同事务钉齐 */
    private TZwStopBill terminate(TZwStopBill bill, int node, String role, String signer,
                                  String action, String comment) {
        if (slotTaken(bill, node, role)) {
            return null;
        }
        try {
            appendSign(bill.getId(), bill.getRoundNo(), node, role, signer, action, comment, new Date());
        } catch (DataIntegrityViolationException dup) {
            return null;
        }
        int rows = this.zwStopBillMapper.update(null, new UpdateWrapper<TZwStopBill>()
                .set("status", StopChain.STATUS_STOPPED)
                .eq("id", bill.getId())
                .eq("node_no", node)
                .eq("round_no", bill.getRoundNo())
                .eq("status", StopChain.STATUS_RUNNING)
                .eq("del_flag", 0));
        if (rows <= 0) {
            throw new IllegalStateException("单据已被并发改动，请刷新后重签");
        }
        return this.zwStopBillMapper.selectById(bill.getId());
    }

    /** 条件推进到下一关口（仍在核） */
    private int advance(TZwStopBill bill, int nextNode) {
        return this.zwStopBillMapper.update(null, new UpdateWrapper<TZwStopBill>()
                .set("node_no", nextNode)
                .eq("id", bill.getId())
                .eq("node_no", bill.getNodeNo())
                .eq("round_no", bill.getRoundNo())
                .eq("status", StopChain.STATUS_RUNNING)
                .eq("del_flag", 0));
    }

    /** 该口该角色当前轮是否已落过名（含非同意笔），防重复落印 */
    private boolean slotTaken(TZwStopBill bill, int node, String role) {
        Integer n = this.zwStopSignMapper.selectCount(new QueryWrapper<TZwStopSign>()
                .eq("bill_id", bill.getId())
                .eq("round_no", bill.getRoundNo())
                .eq("node_no", node)
                .eq("sign_role", role)
                .eq("del_flag", 0));
        return n != null && n > 0;
    }

    /** 该口该角色当前轮的落名人（未落返回 null） */
    private String signerAt(TZwStopBill bill, int node, String role) {
        TZwStopSign one = this.zwStopSignMapper.selectOne(new QueryWrapper<TZwStopSign>()
                .eq("bill_id", bill.getId())
                .eq("round_no", bill.getRoundNo())
                .eq("node_no", node)
                .eq("sign_role", role)
                .eq("del_flag", 0)
                .last("limit 1"));
        return one == null ? null : one.getSigner();
    }

    /** 当前轮该口的全部落印 */
    private List<TZwStopSign> linesOfRound(TZwStopBill bill, int node) {
        return this.zwStopSignMapper.selectList(new QueryWrapper<TZwStopSign>()
                .eq("bill_id", bill.getId())
                .eq("round_no", bill.getRoundNo())
                .eq("node_no", node)
                .eq("del_flag", 0)
                .orderByAsc("sign_time")
                .orderByAsc("id"));
    }

    private void appendSign(Long billId, int round, int node, String role,
                            String signer, String action, String opinion, Date at) {
        TZwStopSign line = new TZwStopSign();
        line.setBillId(billId);
        line.setRoundNo(round);
        line.setNodeNo(node);
        line.setSignRole(role);
        line.setSigner(signer);
        line.setAction(action);
        line.setOpinion(opinion);
        line.setSignTime(at);
        line.setDelFlag(0);
        this.zwStopSignMapper.insert(line);
    }

    /** 名下是否还有未完结处置单（未起/在办都算；已封存不算） */
    private boolean hasOpenCase(String siteNo) {
        Integer n = this.zwCaseFlowMapper.selectCount(new QueryWrapper<TZwCaseFlow>()
                .eq("site_no", siteNo)
                .ne("status", CASE_TERMINAL)
                .eq("del_flag", 0));
        return n != null && n > 0;
    }

    /** 近三个月已入库检测行（只认点位代号捞数） */
    private List<TZwImpRow> loadRecentRows(String siteNo, Date at) {
        Calendar c = Calendar.getInstance();
        c.setTime(at);
        c.add(Calendar.MONTH, -3);
        return this.zwImpRowMapper.selectList(new QueryWrapper<TZwImpRow>()
                .eq("site_no", siteNo)
                .eq("status", ROW_OK)
                .ge("test_at", c.getTime())
                .eq("del_flag", 0)
                .orderByAsc("test_at"));
    }

    /** 批准后回写底档退役：只改在用档；旧采样记录一概不动 */
    private void retireSource(String siteNo) {
        this.zwSourceMapper.update(null, new UpdateWrapper<TZwSource>()
                .set("status", SOURCE_RETIRED)
                .eq("site_no", siteNo)
                .eq("status", SOURCE_ACTIVE)
                .eq("del_flag", 0));
    }

    /** 公告名录取文案：名录没有该事由则不带公告（绝不请人手抄） */
    private String loadNotice(String reason, String siteName) {
        List<TSysDictData> dicts = this.sysDictDataService.selectTSysDictDataList(
                new QueryWrapper<TSysDictData>()
                        .eq("dict_type", NOTICE_DICT_TYPE)
                        .eq("dict_value", reason)
                        .eq("status", "0"));
        if (dicts.isEmpty() || dicts.get(0).getRemark() == null) {
            return null;
        }
        String name = siteName == null ? "" : siteName;
        return dicts.get(0).getRemark().replace("{site}", name);
    }

    private static int yearOf(Date d) {
        Calendar c = Calendar.getInstance();
        c.setTime(d);
        return c.get(Calendar.YEAR);
    }

    private static String trim(String s) {
        return s == null ? "" : s.trim();
    }
}
