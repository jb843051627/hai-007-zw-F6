package com.fc.v2.service.impl;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.fc.v2.mapper.auto.TZwAnomReportMapper;
import com.fc.v2.mapper.auto.TZwFollowMapper;
import com.fc.v2.mapper.auto.TZwFollowTemplateMapper;
import com.fc.v2.model.auto.TZwAnomReport;
import com.fc.v2.model.auto.TZwFollow;
import com.fc.v2.model.auto.TZwFollowTemplate;
import com.fc.v2.service.zw.AnomalyChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;

/**
 * 盯办流水单测（不碰库）：只系统能开结、按 owner 卡登记人、联系不上也续开、
 * 每条带"上一次盯的时间"、跑批把到期未盯自动结漏盯并续开。
 *
 * @author fuce
 * @date 2026-10-03
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class TZwFollowServiceImplTest {

    private TZwFollowMapper followMapper;
    private TZwFollowTemplateMapper tplMapper;
    private TZwAnomReportMapper reportMapper;
    private TZwFollowServiceImpl service;

    private final List<TZwFollow> table = new ArrayList<TZwFollow>();
    private long followSeq = 5000L;

    @BeforeEach
    public void setUp() {
        followMapper = org.mockito.Mockito.mock(TZwFollowMapper.class);
        tplMapper = org.mockito.Mockito.mock(TZwFollowTemplateMapper.class);
        reportMapper = org.mockito.Mockito.mock(TZwAnomReportMapper.class);
        service = new TZwFollowServiceImpl();
        ReflectionTestUtils.setField(service, "zwFollowMapper", followMapper);
        ReflectionTestUtils.setField(service, "zwFollowTemplateMapper", tplMapper);
        ReflectionTestUtils.setField(service, "zwAnomReportMapper", reportMapper);
        table.clear();

        // 反映供水户步模板：2 天周期、盯 fuce、盯联系人
        TZwFollowTemplate tpl = new TZwFollowTemplate();
        tpl.setStepNo(AnomalyChain.STEP_REFLECTED);
        tpl.setDueDays(2);
        tpl.setOwnerLogin("fuce");
        tpl.setTargetContact(1);
        lenient().when(tplMapper.selectOne(any())).thenReturn(tpl);

        lenient().when(followMapper.insert(any())).thenAnswer(inv -> {
            TZwFollow f = inv.getArgument(0);
            f.setId(++followSeq);
            table.add(f);
            return 1;
        });
        lenient().when(followMapper.selectList(any())).thenAnswer(inv -> new ArrayList<TZwFollow>(table));
        lenient().when(followMapper.selectById(any())).thenAnswer(inv -> {
            Long id = inv.getArgument(0);
            for (TZwFollow f : table) if (f.getId().equals(id)) return f;
            return null;
        });
        lenient().when(followMapper.selectOne(any())).thenAnswer(inv -> {
            List<TZwFollow> sorted = new ArrayList<TZwFollow>(table);
            sorted.sort((x, y) -> {
                Date a = x.getFinishTime(), b = y.getFinishTime();
                if (a == null || b == null) return 0;
                return b.compareTo(a);
            });
            for (TZwFollow f : sorted) {
                if (f.getStatus() != AnomalyChain.FOLLOW_WAIT) return f;
            }
            return null;
        });
        lenient().when(followMapper.selectCount(any())).thenAnswer(inv -> {
            int n = 0;
            for (TZwFollow f : table) n++;
            return n;
        });
        lenient().when(followMapper.update(any(), any())).thenAnswer(inv -> {
            applySet(inv.getArgument(1), inv.getArgument(0));
            return 1;
        });

        TZwAnomReport running = new TZwAnomReport();
        running.setId(77L);
        running.setStatus(AnomalyChain.REPORT_RUNNING);
        running.setStepNo(AnomalyChain.STEP_REFLECTED);
        running.setAccountName("城北供水营业所");
        running.setContactName("周联络");
        running.setContactPhone("13900000001");
        lenient().when(reportMapper.selectById(77L)).thenReturn(running);
    }

    @Test
    public void openStepCreatesFirstFollowWithContactSnapshot() {
        service.openStep(77L, AnomalyChain.STEP_REFLECTED, new Date());
        assertEquals(1, table.size());
        TZwFollow f = table.get(0);
        assertEquals(1, f.getSeqNo().intValue());
        assertEquals("fuce", f.getOwnerLogin());
        assertEquals("周联络", f.getTargetContactName());
        assertNull(f.getPrevFollowAt(), "首盯没有上一次盯的时间");
        assertEquals(AnomalyChain.FOLLOW_WAIT, f.getStatus().intValue());
    }

    @Test
    public void onlyAssignedOwnerCanFinish() {
        service.openStep(77L, AnomalyChain.STEP_REFLECTED, new Date());
        TZwFollow f = table.get(0);

        List<String> msg = new ArrayList<String>();
        assertNull(service.finish(f.getId(), true, "已通知到本人", "admin", msg));
        assertTrue(msg.get(0).contains("fuce"));
        assertEquals(AnomalyChain.FOLLOW_WAIT, f.getStatus().intValue(), "非经办人结不掉");

        List<String> msg2 = new ArrayList<String>();
        assertNotNull(service.finish(f.getId(), true, "已通知到本人", "fuce", msg2));
        assertEquals(AnomalyChain.FOLLOW_DONE, f.getStatus().intValue());
    }

    @Test
    public void resultRequiredAndUnreachableStillOpensNext() {
        service.openStep(77L, AnomalyChain.STEP_REFLECTED, new Date());
        TZwFollow first = table.get(0);

        // 空结果不收
        assertNull(service.finish(first.getId(), false, "  ", "fuce", new ArrayList<String>()));

        Date now = new Date();
        TZwFollow done = service.finish(first.getId(), false, "号码停机，联系不上", "fuce",
                new ArrayList<String>());
        assertNotNull(done);
        assertEquals(AnomalyChain.FOLLOW_UNREACHABLE, done.getStatus().intValue());
        assertEquals(2, table.size(), "联系不上也算盯完，系统同事务续开下一条");
        TZwFollow second = table.get(1);
        assertEquals(2, second.getSeqNo().intValue());
        assertNotNull(second.getPrevFollowAt(), "每一条结果都带上一次盯的时间");
        assertEquals(AnomalyChain.FOLLOW_WAIT, second.getStatus().intValue());
        assertEquals("fuce", second.getOwnerLogin());
    }

    @Test
    public void runDueMarksOverdueMissedAndOpensNext() {
        service.openStep(77L, AnomalyChain.STEP_REFLECTED, new Date(System.currentTimeMillis()
                - 3L * 24 * 3600 * 1000));
        TZwFollow overdue = table.get(0);
        // 开条时刻已在三天前、周期 2 天，故此刻已到期
        overdue.setDueAt(new Date(System.currentTimeMillis() - 3600 * 1000));

        int n = service.runDue(new Date());
        assertEquals(1, n);
        assertEquals(AnomalyChain.FOLLOW_MISSED, overdue.getStatus().intValue());
        assertEquals(AnomalyChain.SYSTEM, overdue.getFinishBy(), "漏盯由系统自己结");
        assertEquals(2, table.size(), "漏盯后系统原样开下一条");
    }

    @Test
    public void closedFollowCannotBeFinishedAgain() {
        service.openStep(77L, AnomalyChain.STEP_REFLECTED, new Date());
        TZwFollow f = table.get(0);
        assertNotNull(service.finish(f.getId(), true, "第一次盯完", "fuce", new ArrayList<String>()));
        // 台账只追加：第二次再结同一条被拒
        List<String> msg = new ArrayList<String>();
        assertNull(service.finish(f.getId(), true, "想改前一次结果", "fuce", msg));
        assertTrue(msg.get(0).contains("不许改"));
    }

    /** 按 set 片段占位键从参数图取值，只处理本测试关心的列 */
    private void applySet(Object wrapperObj, Wrapper<?> ignored) {
        UpdateWrapper<?> uw = (UpdateWrapper<?>) wrapperObj;
        java.util.Map<String, Object> params = uw.getParamNameValuePairs();
        java.util.regex.Matcher m = java.util.regex.Pattern.compile(
                "([a-z_][a-z0-9_]*)\\s*=\\s*#\\{ew\\.paramNameValuePairs\\.(\\w+)\\}").matcher(uw.getSqlSet());
        while (m.find()) {
            String col = m.group(1);
            Object val = params.get(m.group(2));
            for (TZwFollow f : table) {
                if ("status".equals(col) && val instanceof Integer) f.setStatus((Integer) val);
                if ("result".equals(col)) f.setResult((String) val);
                if ("finish_by".equals(col)) f.setFinishBy((String) val);
                if ("finish_time".equals(col) && val instanceof Date) f.setFinishTime((Date) val);
            }
        }
    }
}
