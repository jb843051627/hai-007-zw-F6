package com.fc.v2.service.impl;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fc.v2.common.support.ConvertUtil;
import com.fc.v2.mapper.auto.TZwArchCardMapper;
import com.fc.v2.mapper.auto.TZwSourceMapper;
import com.fc.v2.model.auto.TZwArchCard;
import com.fc.v2.model.auto.TZwSource;
import com.fc.v2.service.ITZwArchCardService;
import com.fc.v2.service.zw.SupplyGrade;
import com.fc.v2.util.MD5Util;
import com.fc.v2.util.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 水源地/水厂档案卡 Service业务层处理
 *
 * <p>档案卡挂在底档下，一张卡（同一 bill_no）管一处水源或一座水厂；卡有版本，每录一版
 * 在 t_zw_arch_card 里另存一行 node_no 顺延：
 * <ul>
 *   <li>旧版本一律留着，从当前在用的位置上退下来另存备查；台账列表只显最近一版
 *       （(bill_no,node_no) 命中各卡号最大版次的那行），旧版走 listVersions 翻。</li>
 *   <li>卡号 billNo 与底档编号一样由系统发，入参夹带不拦、但一律不用，私编号塞不进来。</li>
 *   <li>挂接只凭底档编号（siteId→底档）核对，只看名字像不像不认。底档退役当场封口：
 *       新卡不许挂，旧卡重录新版（改规模/改挂）同样不收；旧版查询不受影响。</li>
 *   <li>保障强度层级由系统按底档三道上限自行折算回填（口径在 {@link SupplyGrade}：
 *       压着上界往高算，等于一档上限算二档，超出三档上限压在三档），界面无手填口；
 *       规模有增减、或改挂别的底档，重录时照新底档阈值重折一次。</li>
 *   <li>校验串 content 只随"已压死"那一步定形：待核对/已核对阶段一律为空；
 *       钉住时由卡号+版次+底档代号+规模+新增规模+层级+当日拼成指印，定形后该行谁也改不得。
 *       串随版本同行：新版另钉一条带新版当日的串，旧版那条一字不动——两版共用一串即为错。</li>
 * </ul>
 *
 * @author fuce
 * @date 2026-09-12
 */
@Service
public class TZwArchCardServiceImpl extends ServiceImpl<TZwArchCardMapper, TZwArchCard> implements ITZwArchCardService {

    /** 卡号前缀 */
    private static final String BILL_NO_PREFIX = "AC";

    /** 底档在用 */
    private static final int SOURCE_ACTIVE = 0;

    /** 进展：待核对 */
    public static final int STATUS_TODO = 0;
    /** 进展：已核对 */
    public static final int STATUS_CHECKED = 1;
    /** 进展：已压死（校验串到这一步才定形） */
    public static final int STATUS_PINNED = 2;

    @Autowired
    private TZwSourceMapper zwSourceMapper;

    @Override
    public TZwArchCard selectTZwArchCardById(Long id) {
        if (id == null) {
            return null;
        }
        return this.baseMapper.selectOne(new QueryWrapper<TZwArchCard>()
                .eq("id", id)
                .eq("del_flag", 0));
    }

    @Override
    public List<TZwArchCard> selectTZwArchCardList(Wrapper<TZwArchCard> queryWrapper) {
        // 原样使用调用方的筛选/排序/分页口径，只补两道：未删除、每张卡只显最新一版。
        // 旧版不进台账（另存一层备查走 listVersions），不许自己另起小灶另分页。
        QueryWrapper<TZwArchCard> wrapper = queryWrapper instanceof QueryWrapper
                ? (QueryWrapper<TZwArchCard>) queryWrapper
                : new QueryWrapper<TZwArchCard>();
        wrapper.eq("del_flag", 0);
        wrapper.inSql("(bill_no, node_no)",
                "SELECT bill_no, MAX(node_no) FROM t_zw_arch_card WHERE del_flag = 0 GROUP BY bill_no");
        List<TZwArchCard> latest = this.baseMapper.selectList(wrapper);
        fillFounder(latest);
        return latest;
    }

    /**
     * 台账每行把"当初是谁立的"一并带出：首版（node_no=1）的立卡人与立卡时刻回填到最新版行上。
     * 最新版自己的 create_by 是最近一次改版人，两者并列，一眼看得出卡的来路与最近改动。
     */
    private void fillFounder(List<TZwArchCard> latest) {
        if (latest.isEmpty()) {
            return;
        }
        List<String> billNos = latest.stream().map(TZwArchCard::getBillNo).collect(java.util.stream.Collectors.toList());
        List<TZwArchCard> firsts = this.baseMapper.selectList(new QueryWrapper<TZwArchCard>()
                .eq("node_no", 1)
                .eq("del_flag", 0)
                .in("bill_no", billNos));
        java.util.Map<String, TZwArchCard> firstByBill = firsts.stream()
                .collect(java.util.stream.Collectors.toMap(TZwArchCard::getBillNo, c -> c, (a, b) -> a));
        for (TZwArchCard card : latest) {
            TZwArchCard first = firstByBill.get(card.getBillNo());
            if (first != null) {
                card.setFounder(first.getCreateBy());
                card.setFounderTime(first.getCreateTime());
            }
        }
    }

    @Override
    public List<TZwArchCard> listVersions(String billNo) {
        if (StringUtils.isEmpty(billNo)) {
            return java.util.Collections.emptyList();
        }
        return this.baseMapper.selectList(new QueryWrapper<TZwArchCard>()
                .eq("bill_no", billNo)
                .eq("del_flag", 0)
                .orderByAsc("node_no"));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TZwArchCard insertTZwArchCard(TZwArchCard record) {
        if (record == null || record.getSiteId() == null || record.getQty() == null) {
            return null;
        }
        // 挂接只认编号：底档须存在、未删除且未退役封口。名字像不像一概不看
        TZwSource refArch = loadActiveSource(record.getSiteId());
        if (refArch == null) {
            return null;
        }

        // 系统发号的几样：卡号、版次、层级、进展、校验串——入参夹带一律清掉，不留手填的缝
        record.setId(null);
        record.setBillNo(nextBillNo());
        record.setNodeNo(1);
        record.setSiteNo(refArch.getSiteNo());
        record.setGradeLevel(SupplyGrade.grade(refArch, record.getQty()));
        record.setStatus(STATUS_TODO);
        record.setContent(null);
        record.setDelFlag(0);
        this.baseMapper.insert(record);
        return this.baseMapper.selectById(record.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TZwArchCard revise(TZwArchCard record) {
        if (record == null || record.getId() == null) {
            return null;
        }
        TZwArchCard current = selectTZwArchCardById(record.getId());
        // 只能从当前在用那一版重录：翻到旧版上改、改一张查无的卡，都不收
        if (current == null || !current.getId().equals(latestVersionId(current.getBillNo()))) {
            return null;
        }
        Long targetSiteId = record.getSiteId() == null ? current.getSiteId() : record.getSiteId();
        // 与立卡同一套点位口径：改挂要凭编号核到在用底档；底档已封口的，旧卡回头改也不收
        TZwSource target = loadActiveSource(targetSiteId);
        if (target == null) {
            return null;
        }
        BigDecimal qty = record.getQty() == null ? current.getQty() : record.getQty();
        BigDecimal fineAmt = record.getFineAmt() == null ? current.getFineAmt() : record.getFineAmt();
        String remark = record.getRemark() == null ? current.getRemark() : record.getRemark();
        if (record.getQty() == null && record.getSiteId() == null
                && record.getFineAmt() == null && record.getRemark() == null) {
            // 一样都没动也录一版，只给台账添乱，不收
            return null;
        }

        TZwArchCard next = new TZwArchCard();
        next.setBillNo(current.getBillNo());
        next.setNodeNo(current.getNodeNo() + 1);
        next.setSiteId(target.getId());
        next.setSiteNo(target.getSiteNo());
        next.setQty(qty);
        next.setFineAmt(fineAmt);
        next.setRemark(remark);
        // 规模有增减、或改挂别的底档，都照新底档阈值重折一次，层级随新版回填
        next.setGradeLevel(SupplyGrade.grade(target, qty));
        // 新版从"待核对"重新走起；校验串未到钉住那一步一律为空，旧版那条不动
        next.setStatus(STATUS_TODO);
        next.setContent(null);
        next.setDelFlag(0);
        this.baseMapper.insert(next);
        // 旧版整行一字不改地留在原处备查（连 update_time 都不碰）
        return this.baseMapper.selectById(next.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TZwArchCard confirm(Long id) {
        TZwArchCard current = requireLatest(id);
        // 只有待核对的当前版能推到已核对；已压死的串已定形，谁也动不了它
        if (current == null || current.getStatus() == null || current.getStatus() != STATUS_TODO) {
            return null;
        }
        current.setStatus(STATUS_CHECKED);
        this.baseMapper.updateById(current);
        return this.baseMapper.selectById(current.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TZwArchCard pin(Long id) {
        TZwArchCard current = requireLatest(id);
        // 卡行至钉住这一步校验串才定形：只收"已核对"的当前版，不跳步
        if (current == null || current.getStatus() == null || current.getStatus() != STATUS_CHECKED) {
            return null;
        }
        String code = buildContent(current, new Date());
        // 两版共用一串即为错：任何一行已钉过同串，宁可钉不住也不许两条共用
        Integer dup = this.baseMapper.selectCount(new QueryWrapper<TZwArchCard>()
                .eq("content", code)
                .ne("id", current.getId()));
        if (dup != null && dup > 0) {
            return null;
        }
        current.setStatus(STATUS_PINNED);
        current.setContent(code);
        this.baseMapper.updateById(current);
        return this.baseMapper.selectById(current.getId());
    }

    @Override
    public int deleteTZwArchCardByIds(String ids) {
        Long[] idArr = ConvertUtil.toLongArray(ids);
        int affected = 0;
        for (Long id : idArr) {
            TZwArchCard row = selectTZwArchCardById(id);
            if (row == null) {
                continue;
            }
            // 删卡按卡号整卡删：各版一并置删，行不物理清除，留着备查
            affected += this.baseMapper.update(null, new UpdateWrapper<TZwArchCard>()
                    .eq("bill_no", row.getBillNo())
                    .eq("del_flag", 0)
                    .set("del_flag", 1));
        }
        return affected;
    }

    /** 取行并确认它是本卡当前在用的最新版；旧版只许翻、不许再过任何写口 */
    private TZwArchCard requireLatest(Long id) {
        TZwArchCard row = selectTZwArchCardById(id);
        if (row == null) {
            return null;
        }
        Long latestId = latestVersionId(row.getBillNo());
        return latestId != null && latestId.equals(row.getId()) ? row : null;
    }

    private Long latestVersionId(String billNo) {
        TZwArchCard latest = this.baseMapper.selectOne(new QueryWrapper<TZwArchCard>()
                .eq("bill_no", billNo)
                .eq("del_flag", 0)
                .orderByDesc("node_no")
                .last("LIMIT 1"));
        return latest == null ? null : latest.getId();
    }

    /** 取未删除的底档（不挑在用与否） */
    private TZwSource loadSource(Long siteId) {
        if (siteId == null) {
            return null;
        }
        return this.zwSourceMapper.selectOne(new QueryWrapper<TZwSource>()
                .eq("id", siteId)
                .eq("del_flag", 0));
    }

    /** 取未删除、在用的底档；不存在或已退役（水源枯竭/达使用年限）封口的一律返回 null */
    private TZwSource loadActiveSource(Long siteId) {
        TZwSource s = loadSource(siteId);
        if (s != null && s.getStatus() != null && s.getStatus() != SOURCE_ACTIVE) {
            return null;
        }
        return s;
    }

    /**
     * 钉校验串：明面上带"卡号-V版次-当日"，暗里把本版规模/新增规模/层级/底档代号折成 MD5 指印。
     * 重录一版版次必变、当日随钉住那天换，旧版那条不动，两版不可能共用一串。
     */
    private static String buildContent(TZwArchCard card, Date pinnedAt) {
        String day = new SimpleDateFormat("yyyyMMdd").format(pinnedAt);
        String fingerprint = card.getBillNo()
                + "|" + card.getNodeNo()
                + "|" + card.getSiteNo()
                + "|" + card.getQty()
                + "|" + card.getFineAmt()
                + "|" + card.getGradeLevel()
                + "|" + day;
        return card.getBillNo() + "-V" + card.getNodeNo() + "-" + day + "-" + MD5Util.encode(fingerprint);
    }

    /**
     * 系统发卡号：AC+四位序顺延。每张卡的第一版（node_no=1）与卡同生、整卡软删时同灭，
     * 数它即数在册卡数，不必再去 DISTINCT。撞号重发，不靠人手让号。
     */
    private synchronized String nextBillNo() {
        Integer count = this.baseMapper.selectCount(new QueryWrapper<TZwArchCard>()
                .eq("node_no", 1)
                .eq("del_flag", 0));
        int seq = (count == null ? 0 : count) + 1;
        String billNo;
        do {
            billNo = BILL_NO_PREFIX + String.format("%04d", seq);
            seq++;
        } while (billNoTaken(billNo));
        return billNo;
    }

    private boolean billNoTaken(String billNo) {
        Integer cnt = this.baseMapper.selectCount(new QueryWrapper<TZwArchCard>().eq("bill_no", billNo));
        return cnt != null && cnt > 0;
    }
}
