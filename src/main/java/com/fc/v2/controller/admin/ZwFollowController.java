package com.fc.v2.controller.admin;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import javax.servlet.http.HttpServletResponse;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.common.base.BaseController;
import com.fc.v2.common.domain.AjaxResult;
import com.fc.v2.common.domain.ResultTable;
import com.fc.v2.common.log.Log;
import com.fc.v2.model.auto.TZwFollow;
import com.fc.v2.service.ITZwFollowService;
import com.fc.v2.service.zw.AnomalyChain;
import com.fc.v2.service.zw.ImpSampleTime;
import com.fc.v2.shiro.util.ShiroUtils;
import com.fc.v2.util.StringUtils;
import com.fc.v2.util.file.FileUtils;
import com.github.pagehelper.PageInfo;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

/**
 * 盯办 Controller。记录只有系统能开、只能系统结；本控制器只提供：
 * 流水查询、到期跑批（系统/管理员触发同一口）、以及"登记本次盯办结果"——且该口
 * 还要被服务层按 owner_login 卡一道（不是这条的经办人只能看）。
 *
 * @author fuce
 * @date 2026-10-03
 */
@Api(value = "水质异常盯办")
@Controller
@RequestMapping("/zwFollow")
public class ZwFollowController extends BaseController {

    @Autowired
    private ITZwFollowService zwFollowService;

    @ApiOperation(value = "盯办台账跳转")
    @GetMapping("/view")
    @RequiresPermissions("zw:zwFollow:list")
    public String view() {
        return "admin/zwFollow/list";
    }

    @Log(title = "盯办流水查询", action = "list")
    @GetMapping("/listData")
    @RequiresPermissions("zw:zwFollow:list")
    @ResponseBody
    public ResultTable listData(TZwFollow query,
            @RequestParam(value = "reportId", required = false) Long reportId) {
        QueryWrapper<TZwFollow> qw = new QueryWrapper<TZwFollow>();
        qw.eq("del_flag", 0);
        qw.eq(reportId != null, "report_id", reportId);
        qw.eq(StringUtils.isNotNull(query.getStepNo()), "step_no", query.getStepNo());
        qw.eq(StringUtils.isNotNull(query.getStatus()), "status", query.getStatus());
        qw.eq(StringUtils.isNotEmpty(query.getOwnerLogin()), "owner_login", query.getOwnerLogin());
        qw.orderByDesc("create_time");
        startPage();
        PageInfo<TZwFollow> page = new PageInfo<TZwFollow>(zwFollowService.selectList(qw));
        return pageTable(page.getList(), page.getTotal());
    }

    @Log(title = "登记本次盯办结果", action = "finish")
    @ApiOperation(value = "登记盯办结果", notes = "只能结派给当前登录人的待盯条；联系不上照样登记，系统续开下一条")
    @PostMapping("/finish")
    @RequiresPermissions("zw:zwFollow:finish")
    @ResponseBody
    public AjaxResult finish(Long id, @RequestParam(value = "reached", defaultValue = "1") Integer reached,
                             String result) {
        List<String> errors = new ArrayList<String>();
        TZwFollow f = zwFollowService.finish(id, reached == null || reached.intValue() == 1, result,
                ShiroUtils.getLoginName(), errors);
        return f == null ? AjaxResult.error(errors.isEmpty() ? "这笔没被收受" : errors.get(0))
                : AjaxResult.success("已登记并由系统续开下一条盯办");
    }

    @Log(title = "盯办到期跑批", action = "runDue")
    @ApiOperation(value = "到期自动结漏盯并续开", notes = "定时任务 zwFollowTask.runDue 调同一口")
    @PostMapping("/runDue")
    @RequiresPermissions("zw:zwFollow:run")
    @ResponseBody
    public AjaxResult runDue() {
        int n = zwFollowService.runDue(new Date());
        return AjaxResult.success("本次结漏盯并续开 " + n + " 条");
    }

    @Log(title = "盯办漏盯报表导出", action = "export")
    @GetMapping("/export")
    @RequiresPermissions("zw:zwFollow:list")
    public void export(@RequestParam(value = "reportId", required = false) Long reportId,
                       @RequestParam(value = "status", required = false) Integer status,
                       HttpServletResponse response) throws IOException {
        QueryWrapper<TZwFollow> qw = new QueryWrapper<TZwFollow>()
                .eq("del_flag", 0)
                .eq(reportId != null, "report_id", reportId)
                .eq(status != null, "status", status)
                .orderByAsc("report_id", "step_no", "seq_no");
        List<TZwFollow> rows = zwFollowService.selectList(qw);
        StringBuilder sb = new StringBuilder();
        sb.append('﻿').append("上报单ID,步次,序号,盯办人,应盯时刻,上次盯的时间,供水户,联系人,电话,情形,结果,了结人,了结时刻\n");
        for (TZwFollow f : rows) {
            sb.append(csv(f.getReportId())).append(',')
              .append(f.getStepNo() == null ? "" : f.getStepNo()).append(',')
              .append(f.getSeqNo() == null ? "" : f.getSeqNo()).append(',')
              .append(csv(f.getOwnerLogin())).append(',')
              .append(ImpSampleTime.format(f.getDueAt())).append(',')
              .append(ImpSampleTime.format(f.getPrevFollowAt())).append(',')
              .append(csv(f.getTargetAccountName())).append(',')
              .append(csv(f.getTargetContactName())).append(',')
              .append(csv(f.getTargetContactPhone())).append(',')
              .append(AnomalyChain.followStatusText(f.getStatus() == null ? 0 : f.getStatus().intValue())).append(',')
              .append(csv(f.getResult())).append(',')
              .append(csv(f.getFinishBy())).append(',')
              .append(ImpSampleTime.format(f.getFinishTime())).append('\n');
        }
        byte[] bytes = sb.toString().getBytes("UTF-8");
        response.setContentType("text/csv;charset=UTF-8");
        FileUtils.setAttachmentResponseHeader(response, "盯办流水.csv");
        response.setContentLength(bytes.length);
        response.getOutputStream().write(bytes);
        response.getOutputStream().flush();
    }

    private static String csv(Object v) {
        if (v == null) {
            return "";
        }
        String s = String.valueOf(v);
        if (s.contains(",") || s.contains("\"") || s.contains("\n")) {
            return '"' + s.replace("\"", "\"\"") + '"';
        }
        return s;
    }
}
