package com.fc.v2.controller.admin;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import javax.servlet.http.HttpServletResponse;

import com.fc.v2.common.base.BaseController;
import com.fc.v2.common.domain.AjaxResult;
import com.fc.v2.common.log.Log;
import com.fc.v2.model.auto.TZwRetest;
import com.fc.v2.model.auto.TZwRetestNode;
import com.fc.v2.service.ITZwAnomReportService;
import com.fc.v2.service.ITZwRetestService;
import com.fc.v2.service.zw.AnomalyChain;
import com.fc.v2.service.zw.ImpSampleTime;
import com.fc.v2.service.zw.RetestView;
import com.fc.v2.shiro.util.ShiroUtils;
import com.fc.v2.util.file.FileUtils;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.*;

/**
 * 复测环节 Controller。复测单只由系统自动开出，这里只有倒查页与逐环填报口；
 * 写权由服务层按环节绑定登录名卡，提交后本环即只读，页面无任何越级、代填、改名旁路。
 *
 * @author fuce
 * @date 2026-10-03
 */
@Api(value = "水质异常复测环节")
@Controller
@RequestMapping("/zwRetest")
public class ZwRetestController extends BaseController {

    private final String prefix = "admin/zwRetest";

    @Autowired
    private ITZwAnomReportService zwAnomReportService;
    @Autowired
    private com.fc.v2.service.ITZwRetestService zwRetestService;

    @Log(title = "复测环节倒查", action = "view")
    @GetMapping("/view/{id}")
    @RequiresPermissions("zw:zwAnomaly:view")
    public String view(@PathVariable("id") Long id, ModelMap model) {
        model.put("view", zwRetestService.buildView(id, ShiroUtils.getLoginName()));
        return prefix + "/detail";
    }

    @Log(title = "复测环节填报", action = "fill")
    @ApiOperation(value = "本环节填报", notes = "四件套手录、后环照抄上一环逐字比对；非本环节经办人只读")
    @PostMapping("/fill")
    @RequiresPermissions("zw:zwRetest:fill")
    @ResponseBody
    public AjaxResult fill(Long retestId, String testItem, BigDecimal testQty, String testAt,
                           String conclusion, String opinion) {
        List<String> msg = new ArrayList<String>();
        RetestView view = zwAnomReportService.fillRetestNode(retestId, testItem, testQty, testAt,
                conclusion, opinion, ShiroUtils.getLoginName(), msg);
        return view == null ? AjaxResult.error(msg.isEmpty() ? "本环节不收这笔" : msg.get(0))
                : AjaxResult.success("本环节已提交，对其他人只读");
    }

    @Log(title = "复测否决后另起一轮", action = "retry")
    @ApiOperation(value = "另起一轮复测", notes = "仅最近一轮否决卡停才准开；旧轮原样留痕")
    @PostMapping("/retry")
    @RequiresPermissions("zw:zwRetest:fill")
    @ResponseBody
    public AjaxResult retry(Long reportId) {
        List<String> msg = new ArrayList<String>();
        RetestView view = zwAnomReportService.retryRetest(reportId, ShiroUtils.getLoginName(), msg);
        return view == null ? AjaxResult.error(msg.isEmpty() ? "另起一轮未受理" : msg.get(0))
                : AjaxResult.success("已另起一轮复测，从首环重新填报");
    }

    @Log(title = "复测环节明细导出", action = "export")
    @ApiOperation(value = "明细导出", notes = "核对漏填/对不上报表出得去，UTF-8 CSV；各轮逐环节一行")
    @GetMapping("/export")
    @RequiresPermissions("zw:zwAnomaly:list")
    public void export(@RequestParam(value = "reportId", required = false) Long reportId,
                       HttpServletResponse response) throws IOException {
        List<TZwRetest> rounds = reportId == null ? new ArrayList<TZwRetest>()
                : zwRetestService.listRounds(reportId);
        StringBuilder sb = new StringBuilder();
        sb.append('﻿').append("复测单号,轮次,环节,经办人,检测项目,检测数值,检测时间,结论,本环意见,上一环项目,上一环数值,上一环时间,情形,提交人,提交时间\n");
        for (TZwRetest rt : rounds) {
            for (TZwRetestNode n : zwRetestService.listNodes(rt.getId())) {
                sb.append(csv(rt.getRetestNo())).append(',')
                  .append(rt.getRoundNo()).append(',')
                  .append(csv(AnomalyChain.nodeName(n.getNodeNo() == null ? 0 : n.getNodeNo()))).append(',')
                  .append(csv(n.getOwnerLogin())).append(',')
                  .append(csv(n.getTestItem())).append(',')
                  .append(n.getTestQty() == null ? "" : n.getTestQty().toPlainString()).append(',')
                  .append(ImpSampleTime.format(n.getTestAt())).append(',')
                  .append(csv(AnomalyChain.conclusionText(n.getConclusion()))).append(',')
                  .append(csv(n.getOpinion())).append(',')
                  .append(csv(n.getPrevItem())).append(',')
                  .append(n.getPrevQty() == null ? "" : n.getPrevQty().toPlainString()).append(',')
                  .append(ImpSampleTime.format(n.getPrevTestAt())).append(',')
                  .append(n.getStatus() == null ? "" : (n.getStatus() == AnomalyChain.NODE_VETOED
                          ? "否决" : (n.getStatus() == AnomalyChain.NODE_SUBMITTED ? "已提交" : "待填"))).append(',')
                  .append(csv(n.getFillBy())).append(',')
                  .append(ImpSampleTime.format(n.getFillTime())).append('\n');
            }
        }
        byte[] bytes = sb.toString().getBytes("UTF-8");
        response.setContentType("text/csv;charset=UTF-8");
        FileUtils.setAttachmentResponseHeader(response, "复测环节明细.csv");
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
