package com.fc.v2.service.impl;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fc.v2.mapper.auto.TZwSourceMapper;
import com.fc.v2.model.auto.TZwSource;
import com.fc.v2.service.ITZwSourceService;
import com.fc.v2.util.StringUtils;
import org.springframework.stereotype.Service;

/**
 * 水源地/水厂底档 Service业务层处理
 *
 * <p>底档是两层台账的上层，一档一家。两样铁规矩：
 * <ul>
 *   <li>底档编号只由系统发——立卡建档时入参夹带的 siteNo 当场丢弃，外面私编的号塞不进来；
 *       发号按未删除底档存量顺延（ZS+两位序），并发撞号靠重发兜，不靠人手让号。</li>
 *   <li>底档退役（水源枯竭/达使用年限）当场封口：本服务一切订正写口对退役底档都不收。
 *       封口只钉"不许再写、不许新卡再挂"，先前挂过的卡查询不受影响（查询不加情形过滤）。</li>
 * </ul>
 *
 * @author fuce
 * @date 2026-10-04
 */
@Service
public class TZwSourceServiceImpl extends ServiceImpl<TZwSourceMapper, TZwSource> implements ITZwSourceService {

    /** 底档编号前缀与起步字宽（ZS00、ZS01……） */
    private static final String SITE_NO_PREFIX = "ZS";

    /** 底档在用 */
    private static final int SOURCE_ACTIVE = 0;
    /** 底档已退役（封口） */
    public static final int SOURCE_RETIRED = 1;

    /** 档案类别只收这两样，多一个字都不是这张底档 */
    private static final List<String> SITE_TYPES = Arrays.asList("水源地", "水厂");

    @Override
    public TZwSource selectTZwSourceById(Long id) {
        if (id == null) {
            return null;
        }
        return this.baseMapper.selectOne(new QueryWrapper<TZwSource>()
                .eq("id", id)
                .eq("del_flag", 0));
    }

    @Override
    public List<TZwSource> selectTZwSourceList(Wrapper<TZwSource> queryWrapper) {
        // 原样使用调用方的筛选/排序口径，只补一道与详情一致的"未删除"；退役底档照常翻得到
        QueryWrapper<TZwSource> wrapper = queryWrapper instanceof QueryWrapper
                ? (QueryWrapper<TZwSource>) queryWrapper
                : new QueryWrapper<TZwSource>();
        wrapper.eq("del_flag", 0);
        return this.baseMapper.selectList(wrapper);
    }

    @Override
    public TZwSource createSource(TZwSource record) {
        if (record == null
                || StringUtils.isEmpty(record.getSiteName())
                || StringUtils.isEmpty(record.getSiteType())
                || StringUtils.isEmpty(record.getRoadName())
                || !validThresholds(record.getTh1Max(), record.getTh2Max(), record.getTh3Max())
                || !SITE_TYPES.contains(record.getSiteType())) {
            return null;
        }

        // 编号只由系统发：入参带号、带退役标记、带审计列一律不认，另存的是系统这一份
        record.setId(null);
        record.setSiteNo(nextSiteNo());
        record.setStatus(SOURCE_ACTIVE);
        record.setDelFlag(0);
        record.setCreateTime(new Date());
        this.baseMapper.insert(record);
        return this.baseMapper.selectById(record.getId());
    }

    @Override
    public TZwSource updateSource(TZwSource record) {
        if (record == null || record.getId() == null) {
            return null;
        }
        TZwSource existed = selectTZwSourceById(record.getId());
        // 不存在或已退役封口：订正一律不收（可查是一回事，回头改又是另一回事）
        if (existed == null || existed.getStatus() == null || existed.getStatus() != SOURCE_ACTIVE) {
            return null;
        }
        if (StringUtils.isNotEmpty(record.getSiteName())) {
            existed.setSiteName(record.getSiteName());
        }
        if (StringUtils.isNotEmpty(record.getRoadName())) {
            existed.setRoadName(record.getRoadName());
        }
        if (validThresholds(record.getTh1Max(), record.getTh2Max(), record.getTh3Max())) {
            existed.setTh1Max(record.getTh1Max());
            existed.setTh2Max(record.getTh2Max());
            existed.setTh3Max(record.getTh3Max());
        } else if (record.getTh1Max() != null || record.getTh2Max() != null || record.getTh3Max() != null) {
            // 三道上限要改一起改、还得排成递增；半截子的、倒序的当场挡回，不许留一道半的口
            return null;
        }
        if (record.getRemark() != null) {
            existed.setRemark(record.getRemark());
        }
        // 编号、类别、退役情形不由订正口改动，入参里夹带也白搭
        existed.setUpdateTime(new Date());
        this.baseMapper.updateById(existed);
        return this.baseMapper.selectById(existed.getId());
    }

    @Override
    public TZwSource retire(Long id) {
        TZwSource existed = selectTZwSourceById(id);
        if (existed == null || existed.getStatus() == null || existed.getStatus() != SOURCE_ACTIVE) {
            return null;
        }
        existed.setStatus(SOURCE_RETIRED);
        existed.setUpdateTime(new Date());
        this.baseMapper.updateById(existed);
        return this.baseMapper.selectById(existed.getId());
    }

    /** 三道上限都得有数，且严格递增：一档 &lt; 二档 &lt; 三档，等于、倒序、为负都不立 */
    private static boolean validThresholds(BigDecimal th1, BigDecimal th2, BigDecimal th3) {
        if (th1 == null || th2 == null || th3 == null) {
            return false;
        }
        return th1.signum() > 0
                && th1.compareTo(th2) < 0
                && th2.compareTo(th3) < 0;
    }

    /**
     * 系统发底档编号：取库中本前缀最大尾号 +1，ZS+两位序（过百自动加宽）。
     * 私编的号在 createSource 入口就已丢弃，到这儿只有系统自己发的份；
     * 落号前再查一次占用（含已置删的号，不与旧账重号），撞了就顺延。
     */
    private synchronized String nextSiteNo() {
        TZwSource top = this.baseMapper.selectOne(new QueryWrapper<TZwSource>()
                .likeRight("site_no", SITE_NO_PREFIX)
                .orderByDesc("CHAR_LENGTH(site_no)")
                .orderByDesc("site_no")
                .last("LIMIT 1"));
        int seq = (top == null ? 0 : suffix(top.getSiteNo())) + 1;
        String siteNo;
        do {
            siteNo = SITE_NO_PREFIX + String.format("%02d", seq);
            seq++;
        } while (siteNoTaken(siteNo));
        return siteNo;
    }

    /** 取编号尾部数字（ZS02→2、ZS100→100），号不是自家格式就当没有尾号 */
    private static int suffix(String no) {
        if (no == null || no.length() <= SITE_NO_PREFIX.length()) {
            return 0;
        }
        String tail = no.substring(SITE_NO_PREFIX.length());
        return tail.matches("\\d+") ? Integer.parseInt(tail) : 0;
    }

    private boolean siteNoTaken(String siteNo) {
        Integer cnt = this.baseMapper.selectCount(new QueryWrapper<TZwSource>().eq("site_no", siteNo));
        return cnt != null && cnt > 0;
    }
}
