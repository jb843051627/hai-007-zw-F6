package com.fc.v2.service.impl;

import java.util.ArrayList;
import java.util.List;

import com.fc.v2.mapper.auto.TZwAnomReportMapper;
import com.fc.v2.mapper.auto.TZwAnomalyMapper;
import com.fc.v2.mapper.auto.TZwContactMapper;
import com.fc.v2.mapper.auto.TZwReportActionMapper;
import com.fc.v2.model.auto.TZwAnomReport;
import com.fc.v2.model.auto.TZwAnomaly;
import com.fc.v2.service.ITZwFollowService;
import com.fc.v2.service.ITZwRetestService;
import com.fc.v2.service.zw.AnomalyChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 上报处置门面单测（不碰库）：一笔异常一单、报过只读、结案必须在最后一步、
 * 第一步不可退、归档结论必填。唯一键冲突与条件更新的并发兜底由数据库约束，
 * 此处验服务层自身的门禁与拒绝文案。
 *
 * @author fuce
 * @date 2026-10-03
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class TZwAnomReportServiceImplTest {

    private TZwAnomReportMapper reportMapper;
    private TZwAnomalyMapper anomalyMapper;
    private TZwContactMapper contactMapper;
    private TZwReportActionMapper actionMapper;
    private ITZwRetestService retestService;
    private ITZwFollowService followService;
    private TZwAnomReportServiceImpl service;

    @BeforeEach
    public void setUp() {
        reportMapper = mock(TZwAnomReportMapper.class);
        anomalyMapper = mock(TZwAnomalyMapper.class);
        contactMapper = mock(TZwContactMapper.class);
        actionMapper = mock(TZwReportActionMapper.class);
        retestService = mock(ITZwRetestService.class);
        followService = mock(ITZwFollowService.class);
        service = new TZwAnomReportServiceImpl();
        ReflectionTestUtils.setField(service, "zwAnomReportMapper", reportMapper);
        ReflectionTestUtils.setField(service, "zwAnomalyMapper", anomalyMapper);
        ReflectionTestUtils.setField(service, "zwContactMapper", contactMapper);
        ReflectionTestUtils.setField(service, "zwReportActionMapper", actionMapper);
        ReflectionTestUtils.setField(service, "zwRetestService", retestService);
        ReflectionTestUtils.setField(service, "zwFollowService", followService);

        lenient().when(actionMapper.insert(any())).thenReturn(1);
        lenient().when(actionMapper.selectCount(any())).thenReturn(0);
        lenient().when(reportMapper.selectCount(any())).thenReturn(0);
        lenient().when(anomalyMapper.update(any(), any())).thenReturn(1);
    }

    private TZwAnomaly anomaly(int grade, int reported) {
        TZwAnomaly a = new TZwAnomaly();
        a.setId(90L);
        a.setAnomNo("ZS00-20260918-CL");
        a.setSiteNo("ZS00");
        a.setItemCode("CL");
        a.setGradeLevel(grade);
        a.setNeedRetest(grade >= 3 ? 1 : 0);
        a.setNeedStop(grade >= 4 ? 1 : 0);
        a.setReported(reported);
        a.setReflected(0);
        a.setClosed(0);
        a.setArchived(0);
        a.setDelFlag(0);
        return a;
    }

    @Test
    public void sameAnomalyCannotReportTwice() {
        when(anomalyMapper.selectById(90L)).thenReturn(anomaly(4, 1));
        TZwAnomReport existing = new TZwAnomReport();
        existing.setReportNo("ZS00-2026-1");
        when(reportMapper.selectById(any())).thenReturn(existing);

        List<String> msg = new ArrayList<String>();
        assertNull(service.report(90L, "admin", msg));
        assertTrue(msg.get(0).contains("只能上报一次"));
        verify(reportMapper, never()).insert(any());
    }

    @Test
    public void duplicateSiteDayItemKeyRejectsWholeBill() {
        when(anomalyMapper.selectById(90L)).thenReturn(anomaly(4, 0));
        when(reportMapper.insert(any())).thenThrow(new DataIntegrityViolationException("uk"));

        List<String> msg = new ArrayList<String>();
        assertNull(service.report(90L, "admin", msg));
        assertTrue(msg.get(0).contains("整单不成立"));
        // 唯一键兜底后不应再回写登记
        verify(anomalyMapper, never()).update(any(), any());
    }

    @Test
    public void reportOpensRetestAndFollowWhenRetestNeeded() {
        TZwAnomaly a = anomaly(4, 0);
        when(anomalyMapper.selectById(90L)).thenReturn(a);
        when(reportMapper.insert(any())).thenAnswer(inv -> {
            ((TZwAnomReport) inv.getArgument(0)).setId(700L);
            return 1;
        });
        com.fc.v2.model.auto.TZwRetest rt = new com.fc.v2.model.auto.TZwRetest();
        rt.setId(701L);
        rt.setRetestNo("ZS00-700-RT");
        when(retestService.createForReport(any(), any(), any(), any(), any())).thenReturn(rt);
        when(reportMapper.selectById(700L)).thenAnswer(inv -> {
            TZwAnomReport r = new TZwAnomReport();
            r.setId(700L);
            r.setStatus(AnomalyChain.REPORT_RUNNING);
            r.setStepNo(AnomalyChain.STEP_INTERNAL);
            r.setRetestId(701L);
            return r;
        });

        TZwAnomReport r = service.report(90L, "admin", new ArrayList<String>());
        assertNotNull(r);
        // 复测单由系统自动开出，盯办第一步自动开
        verify(retestService).createForReport(eq(700L), eq(90L), eq("ZS00"), eq("CL"), any());
        verify(followService).openStep(eq(700L), eq(AnomalyChain.STEP_INTERNAL), any());
    }

    @Test
    public void closeRequiresLastStepAndOpinion() {
        TZwAnomReport running = report(7L, AnomalyChain.STEP_REFLECTED, AnomalyChain.REPORT_RUNNING);
        when(reportMapper.selectById(7L)).thenReturn(running);

        // 不在待结案步
        assertNull(service.close(7L, "结论", "admin", new ArrayList<String>()));
        // 空结论
        running.setStepNo(AnomalyChain.STEP_TO_CLOSE);
        assertNull(service.close(7L, "  ", "admin", new ArrayList<String>()));
        verify(reportMapper, never()).update(any(), any());
    }

    @Test
    public void rollbackRefusedAtFirstStep() {
        TZwAnomReport first = report(7L, AnomalyChain.STEP_INTERNAL, AnomalyChain.REPORT_RUNNING);
        when(reportMapper.selectById(7L)).thenReturn(first);
        List<String> msg = new ArrayList<String>();
        assertNull(service.rollbackClose(7L, "想撤回上报", "admin", msg));
        assertTrue(msg.get(0).contains("不可撤"));
        verify(reportMapper, never()).update(any(), any());
    }

    @Test
    public void closedRollbackUnlocksStepButKeepsArchive() {
        TZwAnomReport closed = report(7L, AnomalyChain.STEP_TO_CLOSE, AnomalyChain.REPORT_CLOSED);
        when(reportMapper.selectById(7L)).thenReturn(closed);
        when(reportMapper.update(any(), any())).thenAnswer(inv -> {
            closed.setStatus(AnomalyChain.REPORT_RUNNING);
            closed.setStepNo(AnomalyChain.STEP_TO_CLOSE);
            return 1;
        });
        TZwAnomaly a = anomaly(4, 1);
        a.setClosed(1);
        when(anomalyMapper.selectById(90L)).thenReturn(a);

        TZwAnomReport out = service.rollbackClose(7L, "结论需复核", "admin", new ArrayList<String>());
        assertNotNull(out);
        assertEquals(AnomalyChain.REPORT_RUNNING, out.getStatus().intValue());
        // closed 解除、盯办重开；archived/reflected/reported 不清除（更正口照样锁）
        verify(followService).openStep(eq(7L), eq(AnomalyChain.STEP_TO_CLOSE), any());
    }

    private TZwAnomReport report(long id, int step, int status) {
        TZwAnomReport r = new TZwAnomReport();
        r.setId(id);
        r.setAnomId(90L);
        r.setReportNo("ZS00-2026-1");
        r.setSiteNo("ZS00");
        r.setItemCode("CL");
        r.setStepNo(step);
        r.setStatus(status);
        r.setNeedRetest(0);
        r.setDelFlag(0);
        return r;
    }
}
