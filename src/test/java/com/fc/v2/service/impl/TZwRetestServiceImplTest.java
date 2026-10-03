package com.fc.v2.service.impl;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;
import java.util.TimeZone;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.fc.v2.mapper.auto.TZwRetestMapper;
import com.fc.v2.mapper.auto.TZwRetestNodeMapper;
import com.fc.v2.model.auto.TSysDictData;
import com.fc.v2.model.auto.TZwRetest;
import com.fc.v2.model.auto.TZwRetestNode;
import com.fc.v2.service.ITSysDictDataService;
import com.fc.v2.service.zw.AnomalyChain;
import com.fc.v2.service.zw.RetestView;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 复测环节门禁单测（不碰库）：按环节登录名开通写权、后环照抄逐字比对、
 * 提交后只读、否决卡停且后续环不开、否决后另起一轮旧轮留痕。
 *
 * @author fuce
 * @date 2026-10-03
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class TZwRetestServiceImplTest {

    private com.fc.v2.mapper.auto.TZwRetestMapper retestMapper;
    private com.fc.v2.mapper.auto.TZwRetestNodeMapper nodeMapper;
    private ITSysDictDataService dictDataService;
    private TZwRetestServiceImpl service;

    private final List<TZwRetest> roundsTable = new ArrayList<TZwRetest>();
    private final List<TZwRetestNode> nodeTable = new ArrayList<TZwRetestNode>();

    @BeforeEach
    public void setUp() {
        retestMapper = org.mockito.Mockito.mock(TZwRetestMapper.class);
        nodeMapper = org.mockito.Mockito.mock(TZwRetestNodeMapper.class);
        dictDataService = org.mockito.Mockito.mock(ITSysDictDataService.class);
        // 直接按环节序钉经办人，绕开字典 wrapper 解析（ownerOfNode 是服务里唯一的取人出口）
        service = org.mockito.Mockito.spy(new TZwRetestServiceImpl());
        org.mockito.Mockito.doAnswer(inv -> {
            int node = inv.getArgument(0);
            return (node == 1 || node == 3) ? "fuce" : "admin";
        }).when(service).ownerOfNode(org.mockito.ArgumentMatchers.anyInt());
        ReflectionTestUtils.setField(service, "zwRetestMapper", retestMapper);
        ReflectionTestUtils.setField(service, "zwRetestNodeMapper", nodeMapper);
        ReflectionTestUtils.setField(service, "sysDictDataService", dictDataService);
        roundsTable.clear();
        nodeTable.clear();

        lenient().when(retestMapper.selectList(any()))
                .thenAnswer(inv -> new ArrayList<TZwRetest>(roundsTable));
        lenient().when(nodeMapper.selectList(any()))
                .thenAnswer(inv -> new ArrayList<TZwRetestNode>(nodeTable));
        lenient().when(retestMapper.insert(any())).thenAnswer(inv -> {
            TZwRetest r = inv.getArgument(0);
            if (r.getId() == null) r.setId(1000L + roundsTable.size());
            roundsTable.add(r);
            return 1;
        });
        lenient().when(nodeMapper.insert(any())).thenAnswer(inv -> {
            TZwRetestNode n = inv.getArgument(0);
            if (n.getId() == null) n.setId(2000L + nodeTable.size());
            nodeTable.add(n);
            return 1;
        });
        lenient().when(retestMapper.selectById(any())).thenAnswer(inv -> {
            Long id = inv.getArgument(0);
            for (TZwRetest r : roundsTable) if (r.getId().equals(id)) return r;
            return null;
        });
        lenient().when(nodeMapper.selectById(any())).thenAnswer(inv -> {
            Long id = inv.getArgument(0);
            for (TZwRetestNode n : nodeTable) if (n.getId().equals(id)) return n;
            return null;
        });
    }

    @Test
    public void createOpensFirstNodeOwnedByBoundLogin() {
        TZwRetest r = service.createForReport(9L, 90L, "ZS00", "CL", "system");
        assertNotNull(r);
        assertEquals(1, roundsTable.size());
        assertEquals(1, nodeTable.size());
        assertEquals(0, nodeTable.get(0).getNodeNo().intValue());
        assertEquals("admin", nodeTable.get(0).getOwnerLogin());
        assertEquals(AnomalyChain.NODE_PENDING, nodeTable.get(0).getStatus().intValue());
        assertNull(nodeTable.get(0).getTestItem(), "四件套不许预填");
        assertNull(nodeTable.get(0).getTestQty());
    }

    @Test
    public void createIsIdempotentOnFirstRound() {
        service.createForReport(9L, 90L, "ZS00", "CL", "system");
        service.createForReport(9L, 90L, "ZS00", "CL", "system");
        assertEquals(1, roundsTable.size(), "首轮幂等，不重复开");
    }

    @Test
    public void nonOwnerCannotFillAndNodeStaysPending() {
        TZwRetest r = service.createForReport(9L, 90L, "ZS00", "CL", "system");
        when(nodeMapper.selectOne(any())).thenReturn(nodeTable.get(0));

        List<String> msg = new ArrayList<String>();
        TZwRetest out = service.fillNode(r.getId(), "CL", new BigDecimal("0.70"),
                "2026-10-03 09:00:00", AnomalyChain.CON_PASS, "", "fuce", msg);
        assertNull(out);
        assertTrue(msg.get(0).contains("只读"));
        verify(nodeMapper, never()).update(any(), any());
    }

    @Test
    public void firstNodePassOpensSecondNodeWithPrevPinned() {
        TZwRetest r = service.createForReport(9L, 90L, "ZS00", "CL", "system");
        TZwRetestNode n0 = nodeTable.get(0);
        when(nodeMapper.selectOne(any())).thenReturn(n0);
        when(nodeMapper.update(any(), any())).thenAnswer(inv -> {
            applySet(inv.getArgument(1), inv.getArgument(0), n0);
            return 1;
        });
        when(retestMapper.update(any(), any())).thenAnswer(inv -> {
            applySet(inv.getArgument(1), inv.getArgument(0), r);
            return 1;
        });

        List<String> msg = new ArrayList<String>();
        TZwRetest out = service.fillNode(r.getId(), "CL", new BigDecimal("0.70"),
                "2026-10-03 10:00:00", AnomalyChain.CON_PASS, "", "admin", msg);
        assertNotNull(out, msg.isEmpty() ? "" : msg.get(0));
        assertEquals(AnomalyChain.RETEST_RUNNING, out.getStatus().intValue());
        assertEquals(2, nodeTable.size(), "PASS 后开出第二环");
        TZwRetestNode n1 = nodeTable.get(1);
        assertEquals(1, n1.getNodeNo().intValue());
        assertEquals("fuce", n1.getOwnerLogin());
        assertEquals("CL", n1.getPrevItem(), "上一环结果钉成比对准据");
        assertEquals(0, n1.getPrevQty().compareTo(new BigDecimal("0.70")));
        assertEquals(AnomalyChain.CON_PASS, n1.getPrevConclusion());
    }

    @Test
    public void laterNodeMustCopyPreviousExactly() {
        TZwRetest r = service.createForReport(9L, 90L, "ZS00", "CL", "system");
        TZwRetestNode n1 = pendingNode(1, "fuce");
        n1.setPrevItem("CL");
        n1.setPrevQty(new BigDecimal("0.70"));
        Calendar c = Calendar.getInstance(TimeZone.getTimeZone("GMT+8"));
        c.set(2026, Calendar.OCTOBER, 3, 10, 0, 0);
        c.set(Calendar.MILLISECOND, 0);
        n1.setPrevTestAt(c.getTime());
        when(nodeMapper.selectOne(any())).thenReturn(n1);

        // 数值对不上：不收
        List<String> msg = new ArrayList<String>();
        TZwRetest out = service.fillNode(r.getId(), "CL", new BigDecimal("0.71"),
                "2026-10-03 10:00:00", AnomalyChain.CON_PASS, "", "fuce", msg);
        assertNull(out);
        assertTrue(msg.get(0).contains("对不上"));
        verify(nodeMapper, never()).update(any(), any());

        // 逐字照抄：收
        when(nodeMapper.update(any(), any())).thenReturn(1);
        when(retestMapper.update(any(), any())).thenReturn(1);
        List<String> msg2 = new ArrayList<String>();
        TZwRetest ok = service.fillNode(r.getId(), "CL", new BigDecimal("0.70"),
                "2026-10-03 10:00:00", AnomalyChain.CON_PASS, "", "fuce", msg2);
        assertNotNull(ok, msg2.isEmpty() ? "" : msg2.get(0));
    }

    @Test
    public void vetoStopsRetestAndOpensNoNextNode() {
        TZwRetest r = service.createForReport(9L, 90L, "ZS00", "CL", "system");
        TZwRetestNode n0 = nodeTable.get(0);
        when(nodeMapper.selectOne(any())).thenReturn(n0);
        when(nodeMapper.update(any(), any())).thenAnswer(inv -> {
            applySet(inv.getArgument(1), inv.getArgument(0), n0);
            return 1;
        });
        when(retestMapper.update(any(), any())).thenAnswer(inv -> {
            applySet(inv.getArgument(1), inv.getArgument(0), r);
            return 1;
        });

        int nodesBefore = nodeTable.size();
        List<String> msg = new ArrayList<String>();
        TZwRetest out = service.fillNode(r.getId(), "CL", new BigDecimal("0.70"),
                "2026-10-03 10:00:00", AnomalyChain.CON_FAIL, "读数异常", "admin", msg);
        assertNotNull(out);
        assertEquals(AnomalyChain.RETEST_VETOED, out.getStatus().intValue());
        assertEquals(nodesBefore, nodeTable.size(), "否决后不许开下一环");
        assertEquals(AnomalyChain.NODE_VETOED, n0.getStatus().intValue());
    }

    @Test
    public void canStartNewRoundOnlyAfterVeto() {
        TZwRetest r1 = service.createForReport(9L, 90L, "ZS00", "CL", "system");
        assertNull(service.startNewRound(9L, 90L, "ZS00", "CL", "admin", new ArrayList<String>()),
                "在办轮不许另起");

        r1.setStatus(AnomalyChain.RETEST_VETOED);
        TZwRetest r2 = service.startNewRound(9L, 90L, "ZS00", "CL", "admin",
                new ArrayList<String>());
        assertNotNull(r2);
        assertEquals(2, r2.getRoundNo().intValue());
        assertEquals(2, roundsTable.size(), "旧否决轮原样留着");
        assertTrue(r2.getRetestNo().contains("-R2"));
        assertEquals(0, nodeTable.get(nodeTable.size() - 1).getNodeNo().intValue(),
                "新轮从首环重新填");
    }

    @Test
    public void latestVetoedFlagOnlyOnVetoedRound() {
        TZwRetest r = service.createForReport(9L, 90L, "ZS00", "CL", "system");
        assertFalse(service.buildView(r.getId(), "admin").isLatestVetoed());
        r.setStatus(AnomalyChain.RETEST_VETOED);
        assertTrue(service.buildView(r.getId(), "admin").isLatestVetoed());
    }

    private TZwRetestNode pendingNode(int nodeNo, String owner) {
        TZwRetestNode n = new TZwRetestNode();
        n.setId(3000L + nodeNo);
        n.setRetestId(9L);
        n.setNodeNo(nodeNo);
        n.setOwnerLogin(owner);
        n.setStatus(AnomalyChain.NODE_PENDING);
        return n;
    }

    /** 按 UpdateWrapper 的 set 片段（col=#{...MPGENVALn}）与参数图，把值写回内存实体（蛇形→驼峰） */
    private static void applySet(Object wrapper, Object ignoredUw, Object target) {
        UpdateWrapper<?> uw = (UpdateWrapper<?>) wrapper;
        java.util.Map<String, Object> params = uw.getParamNameValuePairs();
        java.util.regex.Matcher m = java.util.regex.Pattern.compile(
                "([a-z_][a-z0-9_]*)\\s*=\\s*#\\{ew\\.paramNameValuePairs\\.(\\w+)\\}").matcher(uw.getSqlSet());
        while (m.find()) {
            invokeSetter(target, m.group(1), params.get(m.group(2)));
        }
    }

    private static void invokeSetter(Object target, String col, Object val) {
        StringBuilder camel = new StringBuilder();
        boolean up = false;
        for (char ch : col.toCharArray()) {
            if (ch == '_') { up = true; continue; }
            camel.append(up ? Character.toUpperCase(ch) : ch);
            up = false;
        }
        String name = "set" + Character.toUpperCase(camel.charAt(0)) + camel.substring(1);
        try {
            if (val instanceof Integer) {
                target.getClass().getMethod(name, Integer.class).invoke(target, val);
            } else if (val != null) {
                target.getClass().getMethod(name, val.getClass()).invoke(target, val);
            }
        } catch (Exception ignore) {
            // 与本断言无关的列类型映射不上时跳过
        }
    }
}
