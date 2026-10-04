package com.fc.v2.service.impl;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
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
import com.fc.v2.shiro.util.ShiroUtils;
import com.fc.v2.util.MD5Util;
import com.fc.v2.util.StringUtils;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 水源地/水厂档案卡Service业务层处理
 *
 * <p><b>编号只有系统发</b>：底档编号与档案卡号都由系统发号，界面上没有手填的格子，
 * 外头私编的号也塞不进来——入参里带了这两样，系统不拦（不当场报错），但也绝不采用，
 * 落库的一律是系统当场发的号。卡号随卡不随版：重录多少版，卡号还是那一串。
 *
 * <p><b>挂接只凭编号核对</b>：立卡、改挂都拿底档代号（兜底主键）去核——底档对不上、
 * 或已退役（水源枯竭/达使用年限）当场封口，新卡一律立不上、旧卡一律改不动；
 * 只看名字像不像就当同一家，不认。退役底档名下的旧卡仍可查询，但可查是一回事、
 * 回头改又是另一回事。
 *
 * <p><b>层级系统折算</b>：保障强度层级由系统按底档三档上限折出并回填，界面上不设手填口；
 * 规模有增减、或卡改挂到别的底档名下，都重新折算一次。口径只有一句——压着上界往高算：
 * 规模恰好等于一档上限算二档，超出三档上限的一律算三档。缺阈值不乱判，层级置 0。
 *
 * <p><b>版本只进不退</b>：每次重录另起一版（版次 +1）落在当前位置，旧版从当前位置退下来
 * 另存一层备查（del_flag=1，各栏一字不动），页面上只显最近那一版；备查走
 * {@link #selectTZwArchCardHistory} 顺卡号把各版一次翻全。
 *
 * <p><b>校验码随版本同行</b>：卡行至钉住（进展=已压死）那一步校验码才定形，定形之后谁也
 * 改它不得——旧版那条原样留着；重录一版，新版的串换成新版当日重算，两版共用一串即为错。
 * 入参里塞来的串一律不看。
 *
 * <p><b>谁立的、谁改的一眼可见</b>：立卡人随卡留底（重录不换人），最近一次修改人记本次
 * 重录的经办；谁也不许把卡号写进立卡人栏里充数。
 *
 * @author fuce
 * @date 2026-09-12
 */
@Service
public class TZwArchCardServiceImpl extends ServiceImpl<TZwArchCardMapper, TZwArchCard> implements ITZwArchCardService {

    /** 底档情形 0在用 1已退役 */
    private static final int SOURCE_ACTIVE = 0;
    /** 进展 2已压死（钉住）——卡行到这一步校验码才定形 */
    private static final int STATUS_PINNED = 2;

    @javax.annotation.Resource
    private TZwArchCardMapper zwArchCardMapper;

    @javax.annotation.Resource
    private TZwSourceMapper zwSourceMapper;

    /** 卡表写口：命名字段与底座字段哪边注了用哪边，两把尺量同一张表 */
    private TZwArchCardMapper cardMapper() {
        return this.zwArchCardMapper != null ? this.zwArchCardMapper : this.baseMapper;
    }

    @Override
    public TZwArchCard selectTZwArchCardById(Long id) {
        return cardMapper().selectOne(new QueryWrapper<TZwArchCard>()
                .eq("id", id)
                .eq("del_flag", 0)
                .last("limit 1"));
    }

    @Override
    public List<TZwArchCard> selectTZwArchCardList(Wrapper<TZwArchCard> queryWrapper) {
        // 原样使用调用方的筛选/排序/分页口径，只补一道与详情一致的"未删除"，
        // 页面上只显最近那一版——退下来的旧版不在这里露面
        QueryWrapper<TZwArchCard> wrapper = queryWrapper instanceof QueryWrapper
                ? (QueryWrapper<TZwArchCard>) queryWrapper
                : new QueryWrapper<TZwArchCard>();
        wrapper.eq("del_flag", 0);
        return cardMapper().selectList(wrapper);
    }

    /**
     * 备查层：顺卡号把一张卡的各版一次翻全（含已退下的旧版），版次从小到大排。
     * 旧版本一律留着，查得到、改不得。
     */
    @Override
    public List<TZwArchCard> selectTZwArchCardHistory(String billNo) {
        if (StringUtils.isEmpty(billNo)) {
            return new ArrayList<TZwArchCard>();
        }
        return cardMapper().selectList(new QueryWrapper<TZwArchCard>()
                .eq("bill_no", billNo)
                .orderByAsc("node_no")
                .orderByAsc("id"));
    }

    /**
     * 立卡：挂接凭编号核对（代号优先、主键兜底），底档对不上或已退役当场挡回；
     * 卡号系统发号、层级系统折算、校验码只在钉住时定形，入参里这三样一律不看。
     */
    @Override
    public int insertTZwArchCard(TZwArchCard record) {
        if (record == null) {
            return 0;
        }
        TZwSource source = resolveSource(record.getSiteNo(), record.getSiteId());
        if (!isActive(source)) {
            // 底档对不上、或已退役封口：新卡不许挂到它名下
            return 0;
        }

        Date now = new Date();
        record.setSiteId(source.getId() == null ? null : source.getId().intValue());
        record.setSiteNo(source.getSiteNo());
        // 卡号系统当场发，入参私编的号不拦也不用
        record.setBillNo(nextBillNo(source.getSiteNo(), now));
        record.setNodeNo(1);
        // 层级系统折算回填，入参层级不看
        record.setGradeLevel(gradeLevel(source, record.getQty()));
        int status = record.getStatus() == null ? 0 : record.getStatus().intValue();
        record.setStatus(status);
        // 校验码得卡行至钉住那一步才定形；入参塞来的串一律不用
        record.setContent(status == STATUS_PINNED ? checksum(record, now) : null);
        record.setDelFlag(0);
        if (StringUtils.isEmpty(record.getCreateBy())) {
            // 谁立的记谁：入参没指名就记当前经办，绝不拿卡号充数
            record.setCreateBy(currentOperator());
        }
        try {
            return cardMapper().insert(record);
        } catch (DataIntegrityViolationException dup) {
            // 并发同年序号撞号：整笔不算，重新立即可
            return 0;
        }
    }

    /**
     * 重录一版：新版落在当前位置（版次 +1），旧版退下另存备查。
     * 退役底档名下的卡可查不可改；改挂只认编号，目标底档同样须在用。
     * 规模增减或改挂他档，层级都重新折算；新版行至钉住，校验码按新版当日重新定形。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateTZwArchCard(TZwArchCard record) {
        if (record == null || record.getId() == null) {
            return 0;
        }
        TZwArchCard existed = cardMapper().selectOne(new QueryWrapper<TZwArchCard>()
                .eq("id", record.getId())
                .eq("del_flag", 0)
                .last("limit 1"));
        if (existed == null) {
            return 0;
        }

        // 退役封口：名下旧卡可查不可改——当前底档对不上或已退役，一律不许动
        TZwSource current = resolveSource(existed.getSiteNo(), existed.getSiteId());
        if (!isActive(current)) {
            return 0;
        }

        // 改挂：入参带了编号才算换底档，目标同样须对得上且在用
        TZwSource target = current;
        if (StringUtils.isNotEmpty(record.getSiteNo()) || record.getSiteId() != null) {
            target = resolveSource(record.getSiteNo(), record.getSiteId());
            if (!isActive(target)) {
                return 0;
            }
        }

        Date now = new Date();
        TZwArchCard next = new TZwArchCard();
        // 卡号随卡不随版；版次顺旧版加一
        next.setBillNo(existed.getBillNo());
        next.setNodeNo((existed.getNodeNo() == null ? 1 : existed.getNodeNo().intValue()) + 1);
        next.setSiteId(target.getId() == null ? null : target.getId().intValue());
        next.setSiteNo(target.getSiteNo());
        BigDecimal qty = record.getQty() != null ? record.getQty() : existed.getQty();
        next.setQty(qty);
        next.setFineAmt(record.getFineAmt() != null ? record.getFineAmt() : existed.getFineAmt());
        int status = record.getStatus() != null ? record.getStatus().intValue()
                : (existed.getStatus() == null ? 0 : existed.getStatus().intValue());
        next.setStatus(status);
        // 规模增减或改挂他档，层级按目标底档阈值重新折算一次
        next.setGradeLevel(gradeLevel(target, qty));
        // 校验码随版本同行：新版行至钉住按新版当日重新定形，旧版那条不动
        next.setContent(status == STATUS_PINNED ? checksum(next, now) : null);
        next.setDelFlag(0);
        // 当初是谁立的随卡留底；最近一次是谁改的记本次重录经办
        next.setCreateBy(existed.getCreateBy());
        next.setCreateTime(existed.getCreateTime());
        next.setUpdateBy(StringUtils.isNotEmpty(record.getUpdateBy())
                ? record.getUpdateBy() : currentOperator());
        next.setUpdateTime(now);
        next.setRemark(record.getRemark() != null ? record.getRemark() : existed.getRemark());

        int rows = cardMapper().insert(next);
        if (rows <= 0) {
            return 0;
        }
        // 旧版从当前位置退下另存备查：只翻转删除标记，旧版各栏（含校验码）一字不动
        int retired = cardMapper().update(null, new UpdateWrapper<TZwArchCard>()
                .set("del_flag", 1)
                .eq("id", existed.getId())
                .eq("del_flag", 0));
        if (retired <= 0) {
            // 旧版已被并发挪走：刚落的新版连同回滚，不许两版同占当前位置
            throw new IllegalStateException("档案卡已被并发改动，请刷新后重录");
        }
        return rows;
    }

    @Override
    public int deleteTZwArchCardByIds(String ids) {
        Long[] idArr = ConvertUtil.toLongArray(ids);
        return cardMapper().deleteBatchIds(Arrays.asList(idArr));
    }

    @Override
    public int deleteTZwArchCardById(Long id) {
        return cardMapper().deleteById(id);
    }

    // ------------------------------------------------------------------

    /**
     * 凭编号核底档：代号优先、主键兜底，未删除即算对得上。
     * 只认编号不认名字——名字像不像在这里一分钱不值。
     */
    private TZwSource resolveSource(String siteNo, Integer siteId) {
        if (StringUtils.isNotEmpty(siteNo)) {
            return this.zwSourceMapper.selectOne(new QueryWrapper<TZwSource>()
                    .eq("site_no", siteNo)
                    .eq("del_flag", 0)
                    .last("limit 1"));
        }
        if (siteId != null) {
            return this.zwSourceMapper.selectOne(new QueryWrapper<TZwSource>()
                    .eq("id", siteId)
                    .eq("del_flag", 0)
                    .last("limit 1"));
        }
        return null;
    }

    /** 底档须对得上且在used；已退役（水源枯竭/达使用年限）即封口 */
    private static boolean isActive(TZwSource source) {
        return source != null && source.getStatus() != null && source.getStatus() == SOURCE_ACTIVE;
    }

    /**
     * 系统发号：底档代号-年-当年序。首版（node_no=1）一卡一条，
     * 退下、删除过的首版也占号——发出去的号绝不回炉重发。
     */
    private String nextBillNo(String siteNo, Date now) {
        Calendar c = Calendar.getInstance();
        c.setTime(now);
        String prefix = siteNo + "-" + c.get(Calendar.YEAR) + "-";
        Integer n = cardMapper().selectCount(new QueryWrapper<TZwArchCard>()
                .eq("node_no", 1)
                .likeRight("bill_no", prefix));
        return prefix + ((n == null ? 0 : n.intValue()) + 1);
    }

    /**
     * 按底档三档上限折保障强度层级，压着上界往高算：
     * 规模与上限相同归入高一档（恰为一档上限算二档）；超出三档上限的一律算三档。
     * 底档缺失或三档上限全未维护时判不出层级，置 0，绝不拿 null 去比。
     */
    private static Integer gradeLevel(TZwSource source, BigDecimal qty) {
        if (source == null || qty == null) {
            return 0;
        }
        BigDecimal th1 = source.getTh1Max();
        BigDecimal th2 = source.getTh2Max();
        BigDecimal th3 = source.getTh3Max();
        if (th1 == null && th2 == null && th3 == null) {
            return 0;
        }
        int level = 1;
        if (th1 != null && qty.compareTo(th1) >= 0) {
            level = 2;
        }
        if (th2 != null && qty.compareTo(th2) >= 0) {
            level = 3;
        }
        // 压到三档上界也没有更高档可升：超出三档上限一律算三档
        if (th3 != null && qty.compareTo(th3) >= 0) {
            level = 3;
        }
        return level;
    }

    /**
     * 钉住那一刻给本版定形一串校验码：卡号-版次-定形当日-内容指印。
     * 串上带着当日日期——重录一版，日期换成新版当日；版次不同两版永不共串。
     */
    private static String checksum(TZwArchCard card, Date day) {
        String ymd = new SimpleDateFormat("yyyyMMdd").format(day);
        String raw = nullToEmpty(card.getBillNo()) + "|" + card.getNodeNo() + "|" + ymd + "|"
                + nullToEmpty(card.getSiteNo()) + "|" + card.getQty() + "|" + card.getFineAmt()
                + "|" + card.getGradeLevel();
        return card.getBillNo() + "-" + card.getNodeNo() + "-" + ymd + "-" + MD5Util.encode(raw);
    }

    /** 当前经办登录名；无登录上下文（跑批/直测）记 Task，与全局自动填充同一把尺 */
    private static String currentOperator() {
        try {
            return ShiroUtils.getLoginName();
        } catch (Exception e) {
            return "Task";
        }
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
