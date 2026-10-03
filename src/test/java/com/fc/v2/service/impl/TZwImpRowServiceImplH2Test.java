package com.fc.v2.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.sql.Connection;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;

import javax.sql.DataSource;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.fc.v2.mapper.auto.TZwImpBatchMapper;
import com.fc.v2.mapper.auto.TZwImpRowMapper;
import com.fc.v2.mapper.auto.TZwLimRuleMapper;
import com.fc.v2.mapper.auto.TZwSourceMapper;
import com.fc.v2.model.auto.TZwImpBatch;
import com.fc.v2.model.auto.TZwImpRow;
import com.fc.v2.model.auto.TZwLimRule;
import com.fc.v2.model.auto.TZwSource;
import com.fc.v2.service.zw.BatchDiffer;
import com.fc.v2.service.zw.ImpImportResult;
import com.fc.v2.service.zw.ImpRowInspector;

/**
 * 导入编排的库级口径集成测试（H2 MySQL 模式 + 真实 MyBatis-Plus + 真实定级服务，不启 Spring）：
 * 整批拦截不进正式账、定级三件套钉死、同内容重导幂等、异常确认、
 * 一批一次核实（成则归档锁、不成写原因也是终态）、核实后重导/重核全拒、报表只取正式账。
 */
public class TZwImpRowServiceImplH2Test {

    private SqlSession session;
    private TZwImpRowServiceImpl service;
    private TZwImpRowMapper rowMapper;
    private TZwImpBatchMapper batchMapper;
    private TZwSourceMapper sourceMapper;
    private TZwLimRuleMapper ruleMapper;

    private static Date ts(String s) throws Exception {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        sdf.setTimeZone(TimeZone.getTimeZone("GMT+8"));
        return sdf.parse(s);
    }

    @BeforeEach
    public void setUp() throws Exception {
        JdbcDataSource ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:imp_zw;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE");
        ds.setUser("sa");
        initSchema(ds);

        MybatisSqlSessionFactoryBean fb = new MybatisSqlSessionFactoryBean();
        fb.setDataSource(ds);
        MybatisConfiguration cfg = new MybatisConfiguration();
        cfg.addMapper(TZwImpRowMapper.class);
        cfg.addMapper(TZwImpBatchMapper.class);
        cfg.addMapper(TZwSourceMapper.class);
        cfg.addMapper(TZwLimRuleMapper.class);
        fb.setConfiguration(cfg);
        SqlSessionFactory factory = fb.getObject();

        session = factory.openSession(true);
        rowMapper = session.getMapper(TZwImpRowMapper.class);
        batchMapper = session.getMapper(TZwImpBatchMapper.class);
        sourceMapper = session.getMapper(TZwSourceMapper.class);
        ruleMapper = session.getMapper(TZwLimRuleMapper.class);

        TZwLimRuleServiceImpl ruleService = new TZwLimRuleServiceImpl();
        ReflectionTestUtils.setField(ruleService, "zwLimRuleMapper", ruleMapper);

        service = new TZwImpRowServiceImpl();
        ReflectionTestUtils.setField(service, "zwImpRowMapper", rowMapper);
        ReflectionTestUtils.setField(service, "zwImpBatchMapper", batchMapper);
        ReflectionTestUtils.setField(service, "zwSourceMapper", sourceMapper);
        ReflectionTestUtils.setField(service, "zwLimRuleService", ruleService);

        seedBaseData();
    }

    @AfterEach
    public void tearDown() {
        if (session != null) {
            session.close();
        }
    }

    // ---------- 场景 ----------

    @Test
    public void cleanBatchEntersLedgerWithPinnedRule() {
        ImpImportResult r = service.importRows("sep.xlsx",
                Arrays.asList(cleanInput("B9001", "0.62"), cleanInput("B9001", "0.10")), "tester");
        assertNull(r.getFileError());
        assertFalse(r.isBlocked());
        assertEquals(2, r.getImported());

        List<TZwImpRow> rows = service.listBatchRows("B9001");
        for (TZwImpRow x : rows) {
            assertEquals(Integer.valueOf(1), x.getStatus());
            assertEquals(Integer.valueOf(1), x.getCheckStatus());
            // 库里存的规则代号必须是检测当天现行线那一条，生效日一并钉上
            assertEquals("CL-SW-2026", x.getRuleCode());
            assertNotNull(x.getRuleEffDate());
        }
        // 0.62 对 0.20/0.30/0.50 是严重超标；0.10 合格
        assertEquals(Integer.valueOf(4), rows.get(0).getGradeLevel());
        assertEquals(Integer.valueOf(1), rows.get(1).getGradeLevel());
    }

    @Test
    public void oneHardErrorBlocksWholeBatchFromLedger() {
        List<ImpRowInspector.Input> rows = Arrays.asList(
                cleanInput("B9002", "0.62"),
                cleanInput("B9002", "-9"));  // 读数硬错
        ImpImportResult r = service.importRows("bad.xlsx", rows, "tester");
        assertTrue(r.isBlocked());
        assertEquals(0, r.getImported());
        assertEquals(1, r.getHardErrors().size());
        assertTrue(r.getHardErrors().get(0).contains("水位"));

        // 连第一行合格的也不许进正式账
        List<TZwImpRow> stored = service.listBatchRows("B9002");
        for (TZwImpRow x : stored) {
            assertEquals(Integer.valueOf(0), x.getStatus());
        }
        assertEquals(Integer.valueOf(2), stored.get(1).getCheckStatus());
        // 报表口径一行都取不到
        assertTrue(service.reportRows(null, null, null).stream().noneMatch(x -> "B9002".equals(x.getBatchNo())));
        assertEquals(Integer.valueOf(1), service.selectBatchByNo("B9002").getBlocked());
    }

    @Test
    public void declaredRuleMismatchBlocks() {
        ImpRowInspector.Input in = cleanInput("B9004", "0.62");
        in.ruleCode = "CL-SW-2024";
        ImpImportResult r = service.importRows("m.xlsx", Arrays.asList(in), "tester");
        assertTrue(r.isBlocked());
        assertTrue(r.getHardErrors().get(0).contains("对不上"));
    }

    @Test
    public void multipleBatchesInOneFileRejected() {
        ImpImportResult r = service.importRows("mix.xlsx",
                Arrays.asList(cleanInput("B1", "0.62"), cleanInput("B2", "0.62")), "tester");
        assertNotNull(r.getFileError());
        assertTrue(r.getFileError().contains("多个采样批次"));
    }

    @Test
    public void sameContentReimportIsIdempotentAndDoesNotDuplicate() {
        service.importRows("a.xlsx", Arrays.asList(cleanInput("B9005", "0.62")), "tester");
        int rowsBefore = service.listBatchRows("B9005").size();
        ImpImportResult again = service.importRows("a.xlsx",
                Arrays.asList(cleanInput("B9005", "0.62")), "tester");
        assertTrue(again.isIdempotent());
        assertEquals(rowsBefore, service.listBatchRows("B9005").size());
    }

    @Test
    public void changedReimportBeforeVerifyReplacesVersionAndOldRowsKeptDeleted() {
        service.importRows("a.xlsx", Arrays.asList(cleanInput("B9006", "0.62")), "tester");
        // 改了读数重导：旧版置删留痕，新版当前
        service.importRows("a.xlsx", Arrays.asList(cleanInput("B9006", "0.25")), "tester");
        List<TZwImpRow> current = service.listBatchRows("B9006");
        assertEquals(1, current.size());
        assertEquals(0, new BigDecimal("0.25").compareTo(current.get(0).getQty()));
        // 旧版仍在库里（del_flag=1），日志/差异翻得到旧账
        long deletedOld = rowMapper.selectList(null).stream()
                .filter(x -> "B9006".equals(x.getBatchNo()) && Integer.valueOf(1).equals(x.getDelFlag()))
                .count();
        assertEquals(1, deletedOld);
    }

    @Test
    public void anomalyConfirmThenVerifyPassArchivesAndLocks() {
        service.importRows("b.xlsx",
                Arrays.asList(cleanInput("B9010", "0.62"), cleanInput("B9010", "-9")), "tester");
        TZwImpRow bad = service.listBatchRows("B9010").get(1);

        // 合格行不许走确认
        TZwImpRow good = service.listBatchRows("B9010").get(0);
        assertFalse(service.confirmAnomaly(good.getId(), "qa"));
        // 未全确认不许核实
        List<String> msg = new ArrayList<String>();
        assertNull(service.verifyBatch("B9010", 1, null, "qa", msg));
        assertTrue(msg.get(0).contains("尚未确认"));

        assertTrue(service.confirmAnomaly(bad.getId(), "qa"));
        assertTrue(service.confirmAnomaly(bad.getId(), "qa")); // 幂等，不换人换时刻
        assertEquals("qa", service.selectTZwImpRowById(bad.getId()).getConfirmBy());

        TZwImpBatch done = service.verifyBatch("B9010", 1, "核实无误，归档", "qa");
        assertNotNull(done);
        assertEquals(Integer.valueOf(1), done.getVerifyResult());
        assertEquals(Integer.valueOf(1), done.getArchived());
        assertEquals("qa", done.getVerifyBy());
        for (TZwImpRow x : service.listBatchRows("B9010")) {
            assertEquals(Integer.valueOf(1), x.getArchived());
            assertEquals(Integer.valueOf(1), x.getVerifyResult());
        }

        // 归档后：再核实拒、再确认拒、整批重导拒
        assertNull(service.verifyBatch("B9010", 1, null, "qa2", msg));
        assertFalse(service.confirmAnomaly(bad.getId(), "qa2"));
        ImpImportResult reimport = service.importRows("b.xlsx",
                Arrays.asList(cleanInput("B9010", "0.62")), "qa2");
        assertNotNull(reimport.getFileError());
        // 核实人/时间仍以第一次为准
        assertEquals("qa", service.selectBatchByNo("B9010").getVerifyBy());
    }

    @Test
    public void verifyFailRequiresReasonAndIsAlsoTerminal() {
        service.importRows("c.xlsx", Arrays.asList(cleanInput("B9011", "0.62")), "tester");
        List<String> msg = new ArrayList<String>();
        // 不成必须有原因
        assertNull(service.verifyBatch("B9011", 2, "  ", "qa", msg));
        assertTrue(msg.get(0).contains("原因"));

        TZwImpBatch done = service.verifyBatch("B9011", 2, "原始凭证对不上", "qa");
        assertNotNull(done);
        assertEquals(Integer.valueOf(2), done.getVerifyResult());
        assertEquals(Integer.valueOf(0), done.getArchived()); // 不成不归档
        assertEquals("原始凭证对不上", done.getVerifyReason());

        // 但仍是只读终态：第二次核实拒、重导拒
        assertNull(service.verifyBatch("B9011", 1, null, "qa2", msg));
        assertNotNull(service.importRows("c.xlsx",
                Arrays.asList(cleanInput("B9011", "0.62")), "qa2").getFileError());
    }

    @Test
    public void reportOnlyLedgerRowsAndFiltersApply() {
        service.importRows("d.xlsx", Arrays.asList(cleanInput("B9020", "0.62")), "tester");
        service.importRows("e.xlsx",
                Arrays.asList(cleanInput("B9021", "0.62"), cleanInput("B9021", "-9")), "tester");
        // 被拦批一行不取，只取 B9020
        List<TZwImpRow> all = service.reportRows(null, null, null);
        assertEquals(1, all.size());
        assertEquals("B9020", all.get(0).getBatchNo());
        // 点位筛
        assertEquals(1, service.reportRows("ZS00", null, null).size());
        assertEquals(0, service.reportRows("ZS404", null, null).size());
        // 有效/复核筛
        assertEquals(1, service.reportRows(null, 1, 1).size());
        assertEquals(0, service.reportRows(null, 0, null).size());
    }

    @Test
    public void periodDiffPinsRowsByBatch() {
        service.importRows("p.xlsx", Arrays.asList(cleanInput("BP1", "0.62")), "tester");
        // 同批次口径自比：无差异
        assertTrue(service.periodDiff(Arrays.asList("BP1"), Arrays.asList("BP1")).isEmpty());
        // 与被拦批比：整批只在本期
        service.importRows("q.xlsx",
                Arrays.asList(cleanInput("BP2", "0.62"), cleanInput("BP2", "-9")), "tester");
        List<BatchDiffer.BatchDiff> d = service.periodDiff(Arrays.asList("BP1", "BP2"), Arrays.asList("BP1"));
        assertEquals(1, d.size());
        assertEquals("BP2", d.get(0).batchNo);
        assertEquals(BatchDiffer.Kind.ONLY_CURRENT, d.get(0).kind);
    }

    // ---------- 造数 ----------

    private static ImpRowInspector.Input cleanInput(String batch, String qty) {
        ImpRowInspector.Input in = new ImpRowInspector.Input();
        in.batchNo = batch;
        in.siteNo = "ZS00";
        in.itemCode = "CL";
        in.qtyText = qty;
        in.sampleType = "水源水";
        in.testAtText = "2026-09-18 09:00:00";
        in.ruleCode = "CL-SW-2026";
        in.validText = "有效";
        in.reviewedText = "已复核";
        in.rawLine = batch + " | ZS00 | CL | " + qty + " | 水源水 | 2026-09-18 09:00:00 | CL-SW-2026 | 有效 | 已复核";
        return in;
    }

    private void seedBaseData() throws Exception {
        TZwSource s = new TZwSource();
        s.setId(0L);
        s.setSiteNo("ZS00");
        s.setSiteName("城北水源地");
        s.setSiteType("水源地");
        s.setStatus(0);
        s.setDelFlag(0);
        sourceMapper.insert(s);

        TZwLimRule rule = new TZwLimRule();
        rule.setId(102L);
        rule.setRuleCode("CL-SW-2026");
        rule.setRuleName("水源水余氯分档线");
        rule.setSampleType("水源水");
        rule.setTh1Max(new BigDecimal("0.20"));
        rule.setTh2Max(new BigDecimal("0.30"));
        rule.setTh3Max(new BigDecimal("0.50"));
        rule.setEffStart(ts("2026-01-01 00:00:00"));
        rule.setPriority(10);
        rule.setStatus(0);
        rule.setDelFlag(0);
        ruleMapper.insert(rule);
    }

    private static void initSchema(DataSource ds) throws Exception {
        try (Connection c = ds.getConnection();
             java.sql.Statement st = c.createStatement()) {
            // 每个用例独立内存库，先兜底清空，防同名表残留
            st.execute("DROP ALL OBJECTS");
            st.execute("CREATE TABLE t_zw_imp_batch (id bigint PRIMARY KEY, batch_no varchar(64), "
                    + "file_name varchar(255), total_rows int, blocked int DEFAULT 0, block_msg varchar(500), "
                    + "content_hash varchar(64), verify_result int, verify_reason varchar(500), "
                    + "verify_by varchar(64), verify_time datetime, archived int DEFAULT 0, archive_time datetime, "
                    + "del_flag int DEFAULT 0, create_by varchar(64), create_time datetime, "
                    + "update_by varchar(64), update_time datetime, remark varchar(500))");
            st.execute("CREATE TABLE t_zw_imp_row (id bigint PRIMARY KEY, batch_no varchar(64), row_no int, "
                    + "site_no varchar(64), item_code varchar(64), qty decimal(12,2), sample_type varchar(32), "
                    + "test_at datetime, grade_level int, rule_code varchar(64), rule_eff_date datetime, "
                    + "declared_rule_code varchar(64), raw_line varchar(1000), valid_flag int DEFAULT 1, "
                    + "reviewed_flag int DEFAULT 0, check_status int DEFAULT 0, check_msg varchar(500), "
                    + "confirm_by varchar(64), confirm_time datetime, "
                    + "verify_result int, verify_reason varchar(500), verify_by varchar(64), verify_time datetime, "
                    + "archived int DEFAULT 0, archive_time datetime, status int, del_flag int DEFAULT 0, "
                    + "create_by varchar(64), create_time datetime, update_by varchar(64), update_time datetime, "
                    + "remark varchar(500))");
            st.execute("CREATE TABLE t_zw_source (id bigint PRIMARY KEY, site_no varchar(64), site_name varchar(128), "
                    + "site_type varchar(32), road_name varchar(128), th1_max decimal(12,2), th2_max decimal(12,2), "
                    + "th3_max decimal(12,2), status int, del_flag int DEFAULT 0, "
                    + "create_by varchar(64), create_time datetime, update_by varchar(64), update_time datetime, remark varchar(500))");
            st.execute("CREATE TABLE t_zw_lim_rule (id bigint PRIMARY KEY, rule_code varchar(64), rule_name varchar(128), "
                    + "sample_type varchar(32), th1_max decimal(12,2), th2_max decimal(12,2), th3_max decimal(12,2), "
                    + "eff_start datetime, eff_end datetime, priority int, status int, del_flag int DEFAULT 0, "
                    + "create_by varchar(64), create_time datetime, update_by varchar(64), update_time datetime, remark varchar(500))");
        }
    }
}
