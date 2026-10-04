package com.fc.v2.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
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
 * 档案立卡的库级口径集成测试（H2 MySQL 模式 + 真实 MyBatis-Plus，不启 Spring）：
 * 系统发号、凭编号挂接、退役封口可查不可改、层级压上界往高算、
 * 重录一版旧版退下备查、校验码钉住定形随版换日期、立卡人/修改人留痕。
 */
public class TZwArchCardServiceImplH2Test {

    private SqlSession session;
    private TZwArchCardServiceImpl service;
    private TZwArchCardMapper cardMapper;
    private TZwSourceMapper sourceMapper;

    @BeforeEach
    public void setUp() throws Exception {
        JdbcDataSource ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:arch_zw;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE");
        ds.setUser("sa");
        initSchema(ds);

        MybatisSqlSessionFactoryBean fb = new MybatisSqlSessionFactoryBean();
        fb.setDataSource(ds);
        MybatisConfiguration cfg = new MybatisConfiguration();
        cfg.addMapper(TZwArchCardMapper.class);
        cfg.addMapper(TZwSourceMapper.class);
        fb.setConfiguration(cfg);
        SqlSessionFactory factory = fb.getObject();

        session = factory.openSession(true);
        cardMapper = session.getMapper(TZwArchCardMapper.class);
        sourceMapper = session.getMapper(TZwSourceMapper.class);

        service = new TZwArchCardServiceImpl();
        ReflectionTestUtils.setField(service, "zwArchCardMapper", cardMapper);
        ReflectionTestUtils.setField(service, "zwSourceMapper", sourceMapper);

        seedSources();
    }

    @AfterEach
    public void tearDown() {
        if (session != null) {
            session.close();
        }
    }

    // ---------- 系统发号与凭编号挂接 ----------

    @Test
    public void billNoIssuedBySystemAndInjectedNumberIgnored() {
        TZwArchCard in = new TZwArchCard();
        in.setSiteNo("ZS00");
        in.setQty(new BigDecimal("10"));
        in.setBillNo("HACK-私编-1");   // 外头私编的号：不拦，但也绝不采用
        in.setGradeLevel(3);          // 入参层级同样不看
        assertEquals(1, service.insertTZwArchCard(in));

        TZwArchCard stored = service.selectTZwArchCardList(new QueryWrapper<TZwArchCard>()).get(0);
        assertNotEquals("HACK-私编-1", stored.getBillNo());
        assertNotNull(stored.getBillNo());
        assertTrue(stored.getBillNo().startsWith("ZS00-"));
        assertEquals(Integer.valueOf(1), stored.getNodeNo());
        // 层级系统折算：10 < 一档上限 30 → 一档，入参的 3 不作数
        assertEquals(Integer.valueOf(1), stored.getGradeLevel());
        // 立卡人栏绝不拿卡号充数
        assertNotEquals(stored.getBillNo(), stored.getCreateBy());
        assertNotNull(stored.getCreateBy());
    }

    @Test
    public void attachOnlyByNumberNeverByName() {
        // 名字像不算数：拿名字当编号挂，对不上底档当场挡回
        TZwArchCard byName = new TZwArchCard();
        byName.setSiteNo("青坪市城北水源地（在用）");
        byName.setQty(new BigDecimal("10"));
        assertEquals(0, service.insertTZwArchCard(byName));

        // 代号对得上才立得上
        TZwArchCard byNo = new TZwArchCard();
        byNo.setSiteNo("ZS00");
        byNo.setQty(new BigDecimal("10"));
        assertEquals(1, service.insertTZwArchCard(byNo));

        // 主键兜底也认
        TZwArchCard byId = new TZwArchCard();
        byId.setSiteId(0);
        byId.setQty(new BigDecimal("20"));
        assertEquals(1, service.insertTZwArchCard(byId));

        // 同一底档两张卡，系统各发各的号
        List<TZwArchCard> all = service.selectTZwArchCardList(new QueryWrapper<TZwArchCard>());
        assertEquals(2, all.size());
        assertNotEquals(all.get(0).getBillNo(), all.get(1).getBillNo());
    }

    // ---------- 退役封口 ----------

    @Test
    public void retiredSourceSealsNewAttachAndOldCardEdit() {
        // 退役底档不许新挂
        TZwArchCard toRetired = new TZwArchCard();
        toRetired.setSiteNo("ZS01");
        toRetired.setQty(new BigDecimal("10"));
        assertEquals(0, service.insertTZwArchCard(toRetired));

        // 在用底档立卡后底档退役：旧卡可查不可改
        TZwArchCard c = new TZwArchCard();
        c.setSiteNo("ZS00");
        c.setQty(new BigDecimal("10"));
        assertEquals(1, service.insertTZwArchCard(c));
        Long id = service.selectTZwArchCardList(new QueryWrapper<TZwArchCard>()).get(0).getId();
        retire("ZS00");

        assertNotNull(service.selectTZwArchCardById(id));   // 可查
        TZwArchCard edit = new TZwArchCard();
        edit.setId(id);
        edit.setQty(new BigDecimal("20"));
        assertEquals(0, service.updateTZwArchCard(edit));   // 改又是另一回事
    }

    // ---------- 层级折算：压着上界往高算 ----------

    @Test
    public void gradePressesUpperBoundAndCapsAtThird() {
        // 恰为一档上限算二档；超出三档上限一律算三档
        assertGrade("10", 1);
        assertGrade("30", 2);   // == 一档上限
        assertGrade("50", 2);
        assertGrade("80", 3);   // == 二档上限
        assertGrade("200", 3);  // == 三档上限
        assertGrade("260", 3);  // 超三档上限仍三档
    }

    @Test
    public void gradeRecalculatedOnQtyChangeAndRelink() {
        TZwArchCard c = new TZwArchCard();
        c.setSiteNo("ZS00");
        c.setQty(new BigDecimal("10"));
        assertEquals(1, service.insertTZwArchCard(c));
        Long id = currentOf("ZS00").getId();

        // 规模增减重算
        TZwArchCard up = new TZwArchCard();
        up.setId(id);
        up.setQty(new BigDecimal("90"));
        assertEquals(1, service.updateTZwArchCard(up));
        assertEquals(Integer.valueOf(3), currentOf("ZS00").getGradeLevel());

        // 改挂到别的底档名下重算（ZS02 三档上限不同：5/10/20）
        TZwArchCard relink = new TZwArchCard();
        relink.setId(currentOf("ZS00").getId());
        relink.setSiteNo("ZS02");
        assertEquals(1, service.updateTZwArchCard(relink));
        TZwArchCard moved = currentOf("ZS02");
        assertEquals("ZS02", moved.getSiteNo());
        assertEquals(Integer.valueOf(3), moved.getGradeLevel());   // 90 对 5/10/20 仍三档

        // 改挂到退役底档：不许新挂
        TZwArchCard toRetired = new TZwArchCard();
        toRetired.setId(moved.getId());
        toRetired.setSiteNo("ZS01");
        assertEquals(0, service.updateTZwArchCard(toRetired));
    }

    // ---------- 版本备查与校验码 ----------

    @Test
    public void reRecordKeepsOldVersionAsideAndShowsOnlyLatest() {
        TZwArchCard c = new TZwArchCard();
        c.setSiteNo("ZS00");
        c.setQty(new BigDecimal("10"));
        c.setCreateBy("立卡人甲");
        assertEquals(1, service.insertTZwArchCard(c));
        TZwArchCard v1 = currentOf("ZS00");
        Long v1Id = v1.getId();

        TZwArchCard up = new TZwArchCard();
        up.setId(v1Id);
        up.setQty(new BigDecimal("40"));
        up.setUpdateBy("重录人乙");
        assertEquals(1, service.updateTZwArchCard(up));

        // 页面上只显最近那一版
        List<TZwArchCard> current = service.selectTZwArchCardList(new QueryWrapper<TZwArchCard>());
        assertEquals(1, current.size());
        TZwArchCard v2 = current.get(0);
        assertEquals(Integer.valueOf(2), v2.getNodeNo());
        assertEquals(v1.getBillNo(), v2.getBillNo());   // 卡号随卡不随版
        assertEquals(0, new BigDecimal("40").compareTo(v2.getQty()));
        // 当初是谁立的随卡留底；最近一次是谁改的记本次重录人
        assertEquals("立卡人甲", v2.getCreateBy());
        assertEquals("重录人乙", v2.getUpdateBy());

        // 旧版本一律留着：退下备查，查得到、改不得
        List<TZwArchCard> history = service.selectTZwArchCardHistory(v1.getBillNo());
        assertEquals(2, history.size());
        assertEquals(Integer.valueOf(1), history.get(0).getNodeNo());
        assertEquals(Integer.valueOf(1), history.get(0).getDelFlag());
        assertNull(service.selectTZwArchCardById(v1Id));   // 旧版不在当前位置
        TZwArchCard editOld = new TZwArchCard();
        editOld.setId(v1Id);
        editOld.setQty(new BigDecimal("99"));
        assertEquals(0, service.updateTZwArchCard(editOld));
    }

    @Test
    public void checksumFinalizesAtPinAndTravelsWithVersion() throws Exception {
        TZwArchCard c = new TZwArchCard();
        c.setSiteNo("ZS00");
        c.setQty(new BigDecimal("10"));
        c.setContent("外头塞来的串");   // 入参串一律不看
        assertEquals(1, service.insertTZwArchCard(c));
        // 未钉住不定形
        TZwArchCard v1 = currentOf("ZS00");
        assertNull(v1.getContent());

        // 行至钉住那一步才定形
        TZwArchCard pin = new TZwArchCard();
        pin.setId(v1.getId());
        pin.setStatus(2);
        assertEquals(1, service.updateTZwArchCard(pin));
        TZwArchCard v2 = currentOf("ZS00");
        assertEquals(Integer.valueOf(2), v2.getStatus());
        String ck2 = v2.getContent();
        assertNotNull(ck2);
        // 串上带着定形当日日期
        assertTrue(ck2.contains(new SimpleDateFormat("yyyyMMdd").format(new Date())));

        // 重录一版仍钉住：新串换新版，旧版那条不动，两版不共一串
        TZwArchCard again = new TZwArchCard();
        again.setId(v2.getId());
        again.setQty(new BigDecimal("20"));
        again.setStatus(2);
        assertEquals(1, service.updateTZwArchCard(again));
        TZwArchCard v3 = currentOf("ZS00");
        assertNotNull(v3.getContent());
        assertNotEquals(ck2, v3.getContent());

        List<TZwArchCard> history = service.selectTZwArchCardHistory(v1.getBillNo());
        assertEquals(3, history.size());
        assertNull(history.get(0).getContent());          // 首版未钉，串从未有过
        assertEquals(ck2, history.get(1).getContent());   // 旧版那条不动
        assertEquals(v3.getContent(), history.get(2).getContent());
    }

    // ---------- 造数与帮手 ----------

    private void assertGrade(String qty, int expect) {
        TZwArchCard c = new TZwArchCard();
        c.setSiteNo("ZS00");
        c.setQty(new BigDecimal(qty));
        assertEquals(1, service.insertTZwArchCard(c));
        TZwArchCard stored = cardMapper.selectOne(new QueryWrapper<TZwArchCard>()
                .eq("site_no", "ZS00").eq("del_flag", 0)
                .orderByDesc("id").last("limit 1"));
        assertEquals(0, new BigDecimal(qty).compareTo(stored.getQty()));
        assertEquals(Integer.valueOf(expect), stored.getGradeLevel(), "qty=" + qty);
    }

    private TZwArchCard currentOf(String siteNo) {
        return cardMapper.selectOne(new QueryWrapper<TZwArchCard>()
                .eq("site_no", siteNo).eq("del_flag", 0).last("limit 1"));
    }

    private void retire(String siteNo) {
        TZwSource s = sourceMapper.selectOne(new QueryWrapper<TZwSource>().eq("site_no", siteNo));
        s.setStatus(1);
        sourceMapper.updateById(s);
    }

    private void seedSources() {
        sourceMapper.insert(source(0L, "ZS00", "青坪市城北水源地（在用）", "水源地", 0,
                "30", "80", "200"));
        sourceMapper.insert(source(1L, "ZS01", "青坪市城南水厂（已退役）", "水厂", 1,
                "30", "80", "200"));
        sourceMapper.insert(source(2L, "ZS02", "青坪市青西水源地（在用）", "水源地", 0,
                "5", "10", "20"));
    }

    private static TZwSource source(Long id, String no, String name, String type, int status,
                                    String th1, String th2, String th3) {
        TZwSource s = new TZwSource();
        s.setId(id);
        s.setSiteNo(no);
        s.setSiteName(name);
        s.setSiteType(type);
        s.setRoadName("青坪省—青坪市—城东区—东湖取水口");
        s.setTh1Max(new BigDecimal(th1));
        s.setTh2Max(new BigDecimal(th2));
        s.setTh3Max(new BigDecimal(th3));
        s.setStatus(status);
        s.setDelFlag(0);
        return s;
    }

    private static void initSchema(DataSource ds) throws Exception {
        try (Connection c = ds.getConnection();
             java.sql.Statement st = c.createStatement()) {
            // 每个用例独立内存库，先兜底清空，防同名表残留
            st.execute("DROP ALL OBJECTS");
            st.execute("CREATE TABLE t_zw_arch_card (id bigint PRIMARY KEY, bill_no varchar(64), node_no int, "
                    + "site_id int, site_no varchar(64), qty decimal(12,2), fine_amt decimal(12,2), "
                    + "content varchar(255), grade_level int, status int, del_flag int DEFAULT 0, "
                    + "create_by varchar(64), create_time datetime, update_by varchar(64), update_time datetime, "
                    + "remark varchar(500))");
            st.execute("CREATE TABLE t_zw_source (id bigint PRIMARY KEY, site_no varchar(64), site_name varchar(128), "
                    + "site_type varchar(32), road_name varchar(128), th1_max decimal(12,2), th2_max decimal(12,2), "
                    + "th3_max decimal(12,2), status int, del_flag int DEFAULT 0, "
                    + "create_by varchar(64), create_time datetime, update_by varchar(64), update_time datetime, "
                    + "remark varchar(500))");
        }
    }
}
