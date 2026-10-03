package com.fc.v2.controller.admin;

import java.util.ArrayList;
import java.util.List;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.common.base.BaseController;
import com.fc.v2.common.domain.AjaxResult;
import com.fc.v2.common.domain.ResultTable;
import com.fc.v2.common.log.Log;
import com.fc.v2.model.auto.TZwAnomReport;
import com.fc.v2.model.auto.TZwAnomaly;
import com.fc.v2.model.auto.TZwContact;
import com.fc.v2.service.ITZwAnomReportService;
import com.fc.v2.service.ITZwAnomalyService;
import com.fc.v2.service.ITZwContactService;
import com.fc.v2.service.zw.AnomalyView;
import com.fc.v2.service.zw.ReportDetailView;
import com.fc.v2.shiro.util.ShiroUtils;
import com.fc.v2.util.StringUtils;
import com.github.pagehelper.PageInfo;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.*;

/**
 * 水质异常判定留痕 Controller（异常登记台账 + 上报处置门面）。
 *
 * <p>页面上没有任何打破"一条异常只挂一个上报单"的旁路：上报/反映/推进/结案/回退
 * 每个按钮只调服务层对应方法，能走到哪一步、能不能更正全由服务层按台账点算后拒绝或放行。
 *
 * @author fuce
 * @date 2026-10-03
 */
@Api(value = "水质异常判定留痕")
@Controller
@RequestMapping("/zwAnomaly")
public class ZwAnomalyController extends BaseController {

    private final String prefix = "admin/zwAnomaly";

    @Autowired
    private ITZwAnomalyService zwAnomalyService;
    @Autowired
    private ITZwAnomReportService zwAnomReportService;
    @Autowired
    private ITZwContactService zwContactService;

    // ---------------- 异常登记台账 ----------------

    @ApiOperation(value = "异常台账跳转")
    @GetMapping("/view")
    @RequiresPermissions("zw:zwAnomaly:view")
    public String view() {
        return prefix + "/list";
    }

    @Log(title = "异常判定台账查询", action = "list")
    @GetMapping("/list")
    @RequiresPermissions("zw:zwAnomaly:list")
    @ResponseBody
    public ResultTable list(TZwAnomaly query) {
        QueryWrapper<TZwAnomaly> qw = new QueryWrapper<TZwAnomaly>();
        qw.eq("del_flag", 0);
        qw.like(StringUtils.isNotEmpty(query.getAnomNo()), "anom_no", query.getAnomNo());
        qw.eq(StringUtils.isNotEmpty(query.getSiteNo()), "site_no", query.getSiteNo());
        qw.eq(StringUtils.isNotEmpty(query.getItemCode()), "item_code", query.getItemCode());
        qw.eq(StringUtils.isNotNull(query.getGradeLevel()), "grade_level", query.getGradeLevel());
        qw.eq(StringUtils.isNotNull(query.getReported()), "reported", query.getReported());
        qw.eq(StringUtils.isNotNull(query.getReflected()), "reflected", query.getReflected());
        qw.eq(StringUtils.isNotNull(query.getArchived()), "archived", query.getArchived());
        qw.orderByDesc("test_day").orderByDesc("create_time");
        startPage();
        PageInfo<TZwAnomaly> page = new PageInfo<TZwAnomaly>(zwAnomalyService.selectList(qw));
        return pageTable(page.getList(), page.getTotal());
    }

    @Log(title = "异常判定下钻", action = "detail")
    @GetMapping("/detail/{id}")
    @RequiresPermissions("zw:zwAnomaly:view")
    public String detail(@PathVariable("id") Long id, ModelMap model) {
        model.put("view", zwAnomalyService.buildView(id));
        return prefix + "/detail";
    }

    @Log(title = "异常判定更正", action = "correct")
    @PostMapping("/correct")
    @RequiresPermissions("zw:zwAnomaly:correct")
    @ResponseBody
    public AjaxResult correct(Long id, String note) {
        List<String> msg = new ArrayList<String>();
        TZwAnomaly a = zwAnomalyService.correct(id, note, ShiroUtils.getLoginName(), msg);
        return a == null ? AjaxResult.error(msg.isEmpty() ? "更正未生效" : msg.get(0))
                : AjaxResult.success("本条内容已按当日现行标准重算更正（只改本条，已留更正轮痕）");
    }

    // ---------------- 上报处置台账 ----------------

    @ApiOperation(value = "上报处置台账跳转")
    @GetMapping("/reportView")
    @RequiresPermissions("zw:zwAnomaly:view")
    public String reportView() {
        return prefix + "/report";
    }

    @Log(title = "异常上报台账查询", action = "reportList")
    @GetMapping("/reportList")
    @RequiresPermissions("zw:zwAnomaly:list")
    @ResponseBody
    public ResultTable reportList(TZwAnomReport query) {
        QueryWrapper<TZwAnomReport> qw = new QueryWrapper<TZwAnomReport>();
        qw.eq("del_flag", 0);
        qw.like(StringUtils.isNotEmpty(query.getReportNo()), "report_no", query.getReportNo());
        qw.eq(StringUtils.isNotEmpty(query.getSiteNo()), "site_no", query.getSiteNo());
        qw.eq(StringUtils.isNotNull(query.getStatus()), "status", query.getStatus());
        qw.eq(StringUtils.isNotNull(query.getStepNo()), "step_no", query.getStepNo());
        qw.orderByDesc("create_time");
        startPage();
        PageInfo<TZwAnomReport> page =
                new PageInfo<TZwAnomReport>(zwAnomReportService.selectList(qw));
        return pageTable(page.getList(), page.getTotal());
    }

    @Log(title = "上报全链下钻", action = "reportDetail")
    @GetMapping("/reportDetail/{id}")
    @RequiresPermissions("zw:zwAnomaly:view")
    public String reportDetail(@PathVariable("id") Long id, ModelMap model) {
        String loginName = ShiroUtils.getLoginName();
        ReportDetailView view = zwAnomReportService.buildView(id, loginName);
        model.put("view", view);
        model.put("loginName", loginName);
        // 反映页要用的该点位可用联系人（取错可当场订正名册后重取）
        if (view != null) {
            List<TZwContact> contacts = zwContactService.listUsable(view.getReport().getSiteNo());
            model.put("contacts", contacts);
        }
        return prefix + "/reportDetail";
    }

    @Log(title = "水质异常上报", action = "report")
    @PostMapping("/report")
    @RequiresPermissions("zw:zwAnomaly:report")
    @ResponseBody
    public AjaxResult report(Long anomId) {
        List<String> msg = new ArrayList<String>();
        TZwAnomReport r = zwAnomReportService.report(anomId, ShiroUtils.getLoginName(), msg);
        return r == null ? AjaxResult.error(msg.isEmpty() ? "整单不成立" : msg.get(0))
                : AjaxResult.successData(200, r);
    }

    @Log(title = "反映供水户", action = "reflect")
    @PostMapping("/reflect")
    @RequiresPermissions("zw:zwAnomaly:reflect")
    @ResponseBody
    public AjaxResult reflect(Long reportId, Long contactId) {
        if (contactId == null) {
            return AjaxResult.error("请选择供水户联系人（取号有误先到名册订正）");
        }
        List<String> msg = new ArrayList<String>();
        TZwAnomReport r = zwAnomReportService.reflect(reportId, contactId,
                ShiroUtils.getLoginName(), msg);
        return r == null ? AjaxResult.error(msg.isEmpty() ? "反映未生效" : msg.get(0))
                : AjaxResult.successData(200, r);
    }

    @Log(title = "推进待结案", action = "toClose")
    @PostMapping("/toClose")
    @RequiresPermissions("zw:zwAnomaly:close")
    @ResponseBody
    public AjaxResult toClose(Long reportId) {
        List<String> msg = new ArrayList<String>();
        TZwAnomReport r = zwAnomReportService.toClose(reportId, ShiroUtils.getLoginName(), msg);
        return r == null ? AjaxResult.error(msg.isEmpty() ? "推进未生效" : msg.get(0))
                : AjaxResult.successData(200, r);
    }

    @Log(title = "结案归档", action = "close")
    @PostMapping("/close")
    @RequiresPermissions("zw:zwAnomaly:close")
    @ResponseBody
    public AjaxResult close(Long reportId, String opinion) {
        List<String> msg = new ArrayList<String>();
        TZwAnomReport r = zwAnomReportService.close(reportId, opinion,
                ShiroUtils.getLoginName(), msg);
        return r == null ? AjaxResult.error(msg.isEmpty() ? "结案未生效" : msg.get(0))
                : AjaxResult.success("已结案归档，以本次写下的结论为准");
    }

    @Log(title = "结案/处置回退", action = "rollback")
    @PostMapping("/rollback")
    @RequiresPermissions("zw:zwAnomaly:close")
    @ResponseBody
    public AjaxResult rollback(Long reportId, String reason) {
        List<String> msg = new ArrayList<String>();
        TZwAnomReport r = zwAnomReportService.rollbackClose(reportId, reason,
                ShiroUtils.getLoginName(), msg);
        return r == null ? AjaxResult.error(msg.isEmpty() ? "回退未生效" : msg.get(0))
                : AjaxResult.success("已退回上一步，动作台账逐笔留痕");
    }
}
