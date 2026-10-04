package com.fc.v2.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.sql.Connection;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

import javax.sql.DataSource;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.fc.v2.mapper.auto.TZwArchCardMapper;
import com.fc.v2.mapper.auto.TZwSourceMapper;
import com.fc.v2.model.auto.TZwArchCard;
import com.fc.v2.model.auto.TZwSource;

/**
 * 水源档案两层台账的库级口径集成测试（H2 MySQL 模式 + 真实 MyBatis-Plus，不启 Spring）：
 * 底档编号/卡号两样都只由系统发、退役当场封口（新卡禁挂、旧卡可查不可改）、
 * 挂接只凭编号、层级压着上界往高算、改规模/改挂重折、旧版留痕台账只显最新版、
 * 校验串压死才钉且随版本走（含当日、旧版不动、两版不共用）。
 */
public class TZwArchCardServiceImplH2Test {

    private SqlSession session;
    private TZwSourceMapper sourceMapper;
    private TZwArchCardMapper cardMapper;
    private TZwSourceServiceImpl sourceService;
    private TZwArchCardServiceImpl cardService;

    @BeforeEach
    public void setUp() throws Exception {
        JdbcDataSource ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:arch_zw;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE");
        ds.setUser("sa");
        initSchema(ds);

        MybatisSqlSessionFactoryBean fb = new MybatisSqlSessionFactoryBean();
        fb.setDataSource(ds);
        MybatisConfiguration cfg = new MybatisConfiguration();
        cfg.addMapper(TZwSourceMapper.class);
        cfg.addMapper(TZwArchCardMapper.class);
        fb.setConfiguration(cfg);
        SqlSessionFactory factory = fb.getObject();

        session = factory.openSession(true);
        sourceMapper = session.getMapper(TZwSourceMapper.class);
        cardMapper = session.getMapper(TZwArchCardMapper.class);

        sourceService = new TZwSourceServiceImpl();
        ReflectionTestUtils.setField(sourceService, "baseMapper", sourceMapper);

        cardService = new TZwArchCardServiceImpl();
        ReflectionTestUtils.setField(cardService, "baseMapper", cardMapper);
        ReflectionTestUtils.setField(cardService, "zwSourceMapper", sourceMapper);

        seedSources();
    }

    @AfterEach
    public void tearDown() {
        if (session != null) {
            session.close();
        }
    }

    // ---------- 底档：发号 / 校验 / 退役封口 ----------

    @Test
    public void siteNoIsSystemIssuedAndPrivateNumberIgnored() {
        TZwSource form = newSource();
        form.setSiteNo("ZS99-PRIVATE");
        TZwSource saved = sourceService.createSource(form);
        assertNotNull(saved);
        // 系统接在 ZS00/ZS01/ZS02 后面发 ZS03，夹带的私编号塞不进来
        assertEquals("ZS03", saved.getSiteNo());
        assertEquals(Integer.valueOf(0), saved.getStatus());
    }

    @Test
    public void incompleteOrUnsortedThresholdsRejected() {
        TZwSource bad = newSource();
        bad.setTh3Max(null);
        assertNull(sourceService.createSource(bad));

        TZwSource unsorted = newSource();
        unsorted.setTh1Max(new BigDecimal("80"));
        unsorted.setTh2Max(new BigDecimal("30"));
        assertNull(sourceService.createSource(unsorted));

        TZwSource wrongType = newSource();
        wrongType.setSiteType("加压站");
        assertNull(sourceService.createSource(wrongType));
    }

    @Test
    public void retiredSourceIsSealedEditableNeitherDirectlyNorViaCards() {
        TZwSource src = sourceService.createSource(newSource());
        TZwArchCard card = cardService.insertTZwArchCard(newCard(src.getId(), "10"));
        assertNotNull(card);

        assertNotNull(sourceService.retire(src.getId()));

        // 封口后订正不收（可查是一回事，回头改又是另一回事）
        TZwSource patch = new TZwSource();
        patch.setId(src.getId());
        patch.setSiteName("改个名也不行");
        assertNull(sourceService.updateSource(patch));
        // 再退役一次也不收
        assertNull(sourceService.retire(src.getId()));

        // 先前挂过的卡仍可查询
        assertNotNull(cardService.selectTZwArchCardById(card.getId()));
        // 但回头改（重录新版）不收，改规模不行、改挂别处也不行
        TZwArchCard edit = new TZwArchCard();
        edit.setId(card.getId());
        edit.setQty(new BigDecimal("50"));
        assertNull(cardService.revise(edit));
        // 封口底档名下不许再挂新卡
        assertNull(cardService.insertTZwArchCard(newCard(src.getId(), "10")));
    }

    @Test
    public void activeSourceAllowsEditingThresholdsTogether() {
        TZwSource src = sourceService.createSource(newSource());
        TZwSource patch = new TZwSource();
        patch.setId(src.getId());
        patch.setTh1Max(new BigDecimal("40"));
        patch.setTh2Max(new BigDecimal("90"));
        patch.setTh3Max(new BigDecimal("220"));
        TZwSource saved = sourceService.updateSource(patch);
        assertNotNull(saved);
        assertEquals(0, saved.getTh1Max().compareTo(new BigDecimal("40")));
    }

    // ---------- 立卡：发号、挂接、层级 ----------

    @Test
    public void billNoIsSystemIssuedAndGradeFoldsUpAgainstCeilings() {
        TZwArchCard cheat = newCard(0L, "30");
        cheat.setBillNo("AC9999");
        cheat.setGradeLevel(1);
        TZwArchCard card = cardService.insertTZwArchCard(cheat);
        assertNotNull(card);
        assertEquals("AC0001", card.getBillNo());
        // 夹带的手填层级不认，系统按 30/80/200 重折：恰好等于一档上限 30，压着上界算二档
        assertEquals(Integer.valueOf(2), card.getGradeLevel());
        assertEquals(Integer.valueOf(0), card.getStatus());
        assertNull(card.getContent());
        assertEquals(1, card.getNodeNo());
        assertEquals("ZS00", card.getSiteNo());
    }

    @Test
    public void gradeBoundaries() {
        // 小于一档上限算一档；等于二档上限算三档；超出三档上限仍压三档
        assertEquals(Integer.valueOf(1), cardService.insertTZwArchCard(newCard(0L, "10")).getGradeLevel());
        assertEquals(Integer.valueOf(3), cardService.insertTZwArchCard(newCard(0L, "80")).getGradeLevel());
        assertEquals(Integer.valueOf(3), cardService.insertTZwArchCard(newCard(0L, "500")).getGradeLevel());
    }

    @Test
    public void hangingByNonExistentIdOrNameAlikeIsRejected() {
        assertNull(cardService.insertTZwArchCard(newCard(987654321L, "10")));
        TZwArchCard noQty = new TZwArchCard();
        noQty.setSiteId(0L);
        assertNull(cardService.insertTZwArchCard(noQty));
    }

    // ---------- 版本：旧版留痕、台账只显最新版、重折 ----------

    @Test
    public void reviseKeepsOldVersionAndListOnlyShowsLatest() {
        TZwArchCard v1 = cardService.insertTZwArchCard(newCard(0L, "10"));
        TZwArchCard edit = new TZwArchCard();
        edit.setId(v1.getId());
        edit.setQty(new BigDecimal("50"));
        TZwArchCard v2 = cardService.revise(edit);
        assertNotNull(v2);
        assertEquals(v1.getBillNo(), v2.getBillNo());
        assertEquals(2, v2.getNodeNo());
        assertEquals(Integer.valueOf(2), v2.getGradeLevel());
        assertEquals(Integer.valueOf(0), v2.getStatus());
        assertNull(v2.getContent());

        // 旧版整行原样在：仍是第 1 版、规模没被翻改
        TZwArchCard old = cardService.selectTZwArchCardById(v1.getId());
        assertEquals(1, old.getNodeNo());
        assertEquals(0, old.getQty().compareTo(new BigDecimal("10")));

        // 台账只显最近那一版；首版立卡人随最新版一行带出（一眼看得出卡当初谁立的）
        TZwArchCard firstRow = cardMapper.selectById(v1.getId());
        firstRow.setCreateBy("old-clerk");
        cardMapper.updateById(firstRow);
        List<TZwArchCard> listed = cardService.selectTZwArchCardList(new QueryWrapper<TZwArchCard>());
        assertEquals(1, listed.size());
        assertEquals(v2.getId(), listed.get(0).getId());
        assertEquals("old-clerk", listed.get(0).getFounder());

        // 倒查两版都在、按版次正序
        List<TZwArchCard> versions = cardService.listVersions(v1.getBillNo());
        assertEquals(2, versions.size());
        assertEquals(1, versions.get(0).getNodeNo());
        assertEquals(2, versions.get(1).getNodeNo());

        // 从旧版上不许再录
        TZwArchCard fromOld = new TZwArchCard();
        fromOld.setId(v1.getId());
        fromOld.setQty(new BigDecimal("60"));
        assertNull(cardService.revise(fromOld));
    }

    @Test
    public void reviseRehangsToAnotherSourceByNumberAndRefolds() {
        TZwArchCard v1 = cardService.insertTZwArchCard(newCard(0L, "10"));
        TZwArchCard move = new TZwArchCard();
        move.setId(v1.getId());
        move.setSiteId(2L); // ZS02 在用，凭编号挂接；只看名字像不像不认
        TZwArchCard v2 = cardService.revise(move);
        assertNotNull(v2);
        assertEquals("ZS02", v2.getSiteNo());
        assertEquals(Integer.valueOf(1), v2.getGradeLevel());
        // 改挂到已退役封口的 ZS01 不收
        move.setId(v2.getId());
        move.setSiteId(1L);
        assertNull(cardService.revise(move));
    }

    @Test
    public void reviseWithNoActualChangeRejected() {
        TZwArchCard v1 = cardService.insertTZwArchCard(newCard(0L, "10"));
        TZwArchCard empty = new TZwArchCard();
        empty.setId(v1.getId());
        assertNull(cardService.revise(empty));
    }

    // ---------- 校验串：压死才钉、随版本走、定形不可改 ----------

    @Test
    public void contentOnlyFormsAtPinAndCannotSkipStep() {
        TZwArchCard v1 = cardService.insertTZwArchCard(newCard(0L, "10"));
        // 不跳步：没核对直接压死不收
        assertNull(cardService.pin(v1.getId()));
        assertNotNull(cardService.confirm(v1.getId()));
        // 核对过但没压死：串仍空
        assertNull(cardService.selectTZwArchCardById(v1.getId()).getContent());
        TZwArchCard pinned = cardService.pin(v1.getId());
        assertNotNull(pinned);
        assertEquals(Integer.valueOf(2), pinned.getStatus());
        String today = new SimpleDateFormat("yyyyMMdd").format(new Date());
        assertTrue(pinned.getContent().contains("AC0001-V1-" + today),
                "校验串须带卡号-版次-钉住当日，实际：" + pinned.getContent());

        // 已定形：再来一次压死不动它
        assertNull(cardService.pin(pinned.getId()));
        assertEquals(pinned.getContent(), cardService.selectTZwArchCardById(pinned.getId()).getContent());
    }

    @Test
    public void newVersionGetsNewStringWithNewDateAndOldVersionUntouched() {
        TZwArchCard v1 = cardService.insertTZwArchCard(newCard(0L, "10"));
        cardService.confirm(v1.getId());
        TZwArchCard pinnedV1 = cardService.pin(v1.getId());
        String oldCode = pinnedV1.getContent();

        // 压死之后要改只能另录新版：新版待核对、无串；旧版那条一字不动
        TZwArchCard edit = new TZwArchCard();
        edit.setId(pinnedV1.getId());
        edit.setQty(new BigDecimal("50"));
        TZwArchCard v2 = cardService.revise(edit);
        assertNotNull(v2);
        assertNull(v2.getContent());
        assertEquals(oldCode, cardService.selectTZwArchCardById(pinnedV1.getId()).getContent());

        cardService.confirm(v2.getId());
        TZwArchCard pinnedV2 = cardService.pin(v2.getId());
        // 串随版本同行：版次换了，两版不共用一串
        assertFalse(oldCode.equals(pinnedV2.getContent()));
        assertTrue(pinnedV2.getContent().contains("-V2-"));
        // 旧版那条依旧不动
        assertEquals(oldCode, cardService.selectTZwArchCardById(pinnedV1.getId()).getContent());
    }

    @Test
    public void deleteCardSoftDeletesAllVersions() {
        TZwArchCard v1 = cardService.insertTZwArchCard(newCard(0L, "10"));
        TZwArchCard edit = new TZwArchCard();
        edit.setId(v1.getId());
        edit.setQty(new BigDecimal("50"));
        TZwArchCard v2 = cardService.revise(edit);

        // 一张卡两版一并置删，影响行数为 2
        assertEquals(2, cardService.deleteTZwArchCardByIds(String.valueOf(v2.getId())));
        assertNull(cardService.selectTZwArchCardById(v1.getId()));
        assertNull(cardService.selectTZwArchCardById(v2.getId()));
        assertTrue(cardService.listVersions(v1.getBillNo()).isEmpty());
        // 行没物理清除，软删留痕仍在库里
        Integer all = cardMapper.selectCount(new QueryWrapper<TZwArchCard>().eq("bill_no", v1.getBillNo()));
        assertEquals(Integer.valueOf(2), all);
    }

    // ---------- 装配 ----------

    private TZwSource newSource() {
        TZwSource s = new TZwSource();
        s.setSiteName("青坪市新建水源地");
        s.setSiteType("水源地");
        s.setRoadName("青坪省—青坪市—城北区—北河取水口");
        s.setTh1Max(new BigDecimal("30"));
        s.setTh2Max(new BigDecimal("80"));
        s.setTh3Max(new BigDecimal("200"));
        return s;
    }

    private TZwArchCard newCard(Long siteId, String qty) {
        TZwArchCard c = new TZwArchCard();
        c.setSiteId(siteId);
        c.setQty(new BigDecimal(qty));
        return c;
    }

    private void seedSources() {
        sourceMapper.insert(source(0L, "ZS00", 0, "30", "80", "200"));
        sourceMapper.insert(source(1L, "ZS01", 1, "30", "80", "200"));
        sourceMapper.insert(source(2L, "ZS02", 0, "30", "80", "200"));
    }

    private TZwSource source(Long id, String no, int status, String t1, String t2, String t3) {
        TZwSource s = new TZwSource();
        s.setId(id);
        s.setSiteNo(no);
        s.setSiteName(no + "名称");
        s.setSiteType("水源地");
        s.setRoadName("青坪省—青坪市—某区—某取水口");
        s.setTh1Max(new BigDecimal(t1));
        s.setTh2Max(new BigDecimal(t2));
        s.setTh3Max(new BigDecimal(t3));
        s.setStatus(status);
        s.setDelFlag(0);
        return s;
    }

    private static void initSchema(DataSource ds) throws Exception {
        try (Connection c = ds.getConnection();
             java.sql.Statement st = c.createStatement()) {
            st.execute("DROP ALL OBJECTS");
            st.execute("CREATE TABLE t_zw_source (id bigint PRIMARY KEY, site_no varchar(64), site_name varchar(128), "
                    + "site_type varchar(32), road_name varchar(128), th1_max decimal(12,2), th2_max decimal(12,2), "
                    + "th3_max decimal(12,2), status int, del_flag int DEFAULT 0, "
                    + "create_by varchar(64), create_time datetime, update_by varchar(64), update_time datetime, "
                    + "remark varchar(500))");
            st.execute("CREATE TABLE t_zw_arch_card (id bigint PRIMARY KEY, bill_no varchar(64), node_no int, "
                    + "site_id bigint, site_no varchar(64), qty decimal(12,2), fine_amt decimal(12,2), "
                    + "content varchar(255), grade_level int, status int, del_flag int DEFAULT 0, "
                    + "create_by varchar(64), create_time datetime, update_by varchar(64), update_time datetime, "
                    + "remark varchar(500))");
        }
    }
}
