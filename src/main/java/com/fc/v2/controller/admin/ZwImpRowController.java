package com.fc.v2.controller.admin;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletResponse;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.common.base.BaseController;
import com.fc.v2.common.domain.AjaxResult;
import com.fc.v2.common.domain.ResultTable;
import com.fc.v2.common.log.Log;
import com.fc.v2.model.auto.TZwImpBatch;
import com.fc.v2.model.auto.TZwImpRow;
import com.fc.v2.model.auto.TZwLimRule;
import com.fc.v2.service.ITZwImpRowService;
import com.fc.v2.service.ITZwLimRuleService;
import com.fc.v2.service.zw.BatchDiffer;
import com.fc.v2.service.zw.ImpExcel;
import com.fc.v2.service.zw.ImpImportResult;
import com.fc.v2.service.zw.ImpRowInspector;
import com.fc.v2.service.zw.ImpSampleTime;
import com.fc.v2.service.zw.WaterGrade;
import com.fc.v2.util.StringUtils;
import com.fc.v2.util.file.FileUtils;
import com.github.pagehelper.PageInfo;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * 化验室检测数据 Excel 导入核实 Controller。
 *
 * <p>主线：整份导入（硬错整批拦在库外）→ 异常逐行确认 → 一批一次核实（成则归档锁死）→
 * 明细台账下钻 → 报表筛选与 UTF-8 Excel 导出 → 本期/上期同批次口径差异。
 * 所有写动作挂 {@link Log}，系统日志里翻得到是哪一次写下的核实结论。
 *
 * @author fuce
 * @date 2026-10-02
 */
@Api(value = "检测数据导入核实")
@Controller
@RequestMapping("/zwImpRow")
public class ZwImpRowController extends BaseController {

    private final String prefix = "admin/zwImpRow";

    @Autowired
    private ITZwImpRowService zwImpRowService;
    @Autowired
    private ITZwLimRuleService zwLimRuleService;

    @ApiOperation(value = "导入明细台账跳转", notes = "导入明细台账跳转")
    @GetMapping("/view")
    @RequiresPermissions("zw:zwImpRow:view")
    public String view(ModelMap model) {
        return prefix + "/list";
    }

    @Log(title = "检测数据明细台账查询", action = "list")
    @ApiOperation(value = "明细分页查询", notes = "批次/点位/有效/复核/校验结论/落地情形/归档皆可筛")
    @GetMapping("/list")
    @RequiresPermissions("zw:zwImpRow:list")
    @ResponseBody
    public ResultTable list(TZwImpRow query,
                            @RequestParam(value = "ledgerOnly", required = false) Integer ledgerOnly) {
        QueryWrapper<TZwImpRow> qw = new QueryWrapper<TZwImpRow>();
        qw.eq("del_flag", 0);
        qw.like(StringUtils.isNotEmpty(query.getBatchNo()), "batch_no", query.getBatchNo());
        qw.like(StringUtils.isNotEmpty(query.getSiteNo()), "site_no", query.getSiteNo());
        qw.eq(StringUtils.isNotEmpty(query.getItemCode()), "item_code", query.getItemCode());
        qw.eq(StringUtils.isNotNull(query.getValidFlag()), "valid_flag", query.getValidFlag());
        qw.eq(StringUtils.isNotNull(query.getReviewedFlag()), "reviewed_flag", query.getReviewedFlag());
        qw.eq(StringUtils.isNotNull(query.getCheckStatus()), "check_status", query.getCheckStatus());
        qw.eq(StringUtils.isNotNull(query.getArchived()), "archived", query.getArchived());
        qw.eq(StringUtils.isNotNull(query.getVerifyResult()), "verify_result", query.getVerifyResult());
        // 报表口径开关：只看正式账（status=1）；拦在库外的异常行默认也留在台账里可见
        qw.eq(ledgerOnly != null && ledgerOnly.intValue() == 1, "status", 1);
        qw.orderByDesc("batch_no").orderByAsc("row_no");
        startPage();
        PageInfo<TZwImpRow> page =
                new PageInfo<TZwImpRow>(zwImpRowService.selectTZwImpRowList(qw));
        return pageTable(page.getList(), page.getTotal());
    }

    @Log(title = "检测数据批次下钻", action = "detail")
    @ApiOperation(value = "批次下钻", notes = "原始行/规则条文/校验复核核实状态并排看")
    @GetMapping("/detail")
    @RequiresPermissions("zw:zwImpRow:view")
    public String detail(@RequestParam("batchNo") String batchNo, ModelMap model) {
        TZwImpBatch batch = zwImpRowService.selectBatchByNo(batchNo);
        List<TZwImpRow> rows = zwImpRowService.listBatchRows(batchNo);
        // 每行附上当时所依规则条文（让位行也调得出），与原始行、复核结论并排
        Map<Long, TZwLimRule> ruleOfRow = new HashMap<Long, TZwLimRule>();
        for (TZwImpRow r : rows) {
            if (r.getRuleCode() != null && r.getTestAt() != null) {
                ruleOfRow.put(r.getId(),
                        zwLimRuleService.ruleOf(r.getRuleCode(), r.getTestAt(), true));
            }
        }
        model.put("batch", batch);
        model.put("rows", rows);
        model.put("ruleOfRow", ruleOfRow);
        return prefix + "/detail";
    }

    @Log(title = "检测数据Excel整批导入", action = "import")
    @ApiOperation(value = "Excel 整批导入", notes = "硬错整批拦在库外，异常全量入明细；已核实批次拒绝重导")
    @PostMapping("/import")
    @RequiresPermissions("zw:zwImpRow:import")
    @ResponseBody
    public AjaxResult importFile(@RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return AjaxResult.error("没有收到文件，请选择化验室导出的 Excel 再上传");
        }
        ImpExcel.ParsedFile parsed;
        try {
            parsed = ImpExcel.read(file.getInputStream());
        } catch (IOException e) {
            return AjaxResult.error("读取 Excel 失败：" + e.getMessage());
        }
        if (parsed.error != null) {
            // 文件级错误：不当成任何一批，一笔不写
            return AjaxResult.error(parsed.error);
        }
        String operator = com.fc.v2.shiro.util.ShiroUtils.getLoginName();
        ImpImportResult result = zwImpRowService.importRows(
                file.getOriginalFilename(), parsed.rows, operator);
        if (result.getFileError() != null) {
            return AjaxResult.error(result.getFileError());
        }
        // 放行/拦截/幂等三种结局由页面照回值分别亮，不许含糊一句
        return AjaxResult.successData(200, result);
    }

    @Log(title = "检测数据异常确认", action = "confirm")
    @ApiOperation(value = "异常确认", notes = "仅校验异常且未核实行可确认；归档只读")
    @PostMapping("/confirm")
    @RequiresPermissions("zw:zwImpRow:confirm")
    @ResponseBody
    public AjaxResult confirm(Long id) {
        String operator = com.fc.v2.shiro.util.ShiroUtils.getLoginName();
        return zwImpRowService.confirmAnomaly(id, operator)
                ? AjaxResult.success("异常已确认，批次可进入核实")
                : AjaxResult.error("确认未生效：该行不是待确认异常，或批次已核实归档");
    }

    @Log(title = "检测数据批次核实", action = "verify")
    @ApiOperation(value = "一批一次核实", notes = "核实成归档锁行；核实不成必填原因；核实过即只读")
    @PostMapping("/verify")
    @RequiresPermissions("zw:zwImpRow:verify")
    @ResponseBody
    public AjaxResult verify(String batchNo, Integer result, String reason) {
        if (result == null) {
            return AjaxResult.error("请选择核实结论：核实成 / 核实不成");
        }
        List<String> rejectMsg = new ArrayList<String>();
        String operator = com.fc.v2.shiro.util.ShiroUtils.getLoginName();
        TZwImpBatch batch = zwImpRowService.verifyBatch(
                batchNo, result.intValue(), reason, operator, rejectMsg);
        if (batch == null) {
            return AjaxResult.error(rejectMsg.isEmpty() ? "核实未生效" : rejectMsg.get(0));
        }
        return AjaxResult.success(result.intValue() == 1
                ? "核实成，整批已归档并锁定为只读" : "核实不成已记名，该批次归只读终态");
    }

    @Log(title = "检测数据明细导出", action = "export")
    @ApiOperation(value = "明细导出", notes = "报表口径正式账；按点位/有效/复核筛；UTF-8 xlsx，两次导出内容一致")
    @GetMapping("/export")
    @RequiresPermissions("zw:zwImpRow:export")
    public void export(@RequestParam(value = "siteNo", required = false) String siteNo,
                       @RequestParam(value = "validFlag", required = false) Integer validFlag,
                       @RequestParam(value = "reviewedFlag", required = false) Integer reviewedFlag,
                       HttpServletResponse response) throws IOException {
        List<TZwImpRow> rows = zwImpRowService.reportRows(siteNo, validFlag, reviewedFlag);
        List<List<String>> lines = new ArrayList<List<String>>();
        for (TZwImpRow r : rows) {
            List<String> line = new ArrayList<String>();
            line.add(s(r.getBatchNo()));
            line.add(r.getRowNo() == null ? "" : String.valueOf(r.getRowNo()));
            line.add(s(r.getSiteNo()));
            line.add(s(r.getItemCode()));
            line.add(r.getQty() == null ? "" : r.getQty().toPlainString());
            line.add(s(r.getSampleType()));
            // 检测时间只过统一尺，导出与页面/接口同一串字符
            line.add(ImpSampleTime.format(r.getTestAt()));
            line.add(s(r.getDeclaredRuleCode()));
            line.add(s(r.getRuleCode()));
            line.add(ImpSampleTime.formatDay(r.getRuleEffDate()));
            line.add(r.getGradeLevel() == null ? "无可用标准" : WaterGrade.gradeText(r.getGradeLevel()));
            line.add(flagText(r.getValidFlag(), true));
            line.add(flagText(r.getReviewedFlag(), false));
            line.add(s(r.getRawLine()));
            line.add(checkText(r.getCheckStatus()));
            line.add(s(r.getCheckMsg()));
            line.add(s(r.getConfirmBy()));
            line.add(ImpSampleTime.format(r.getConfirmTime()));
            line.add(verifyText(r.getVerifyResult()));
            line.add(s(r.getVerifyReason()));
            line.add(s(r.getVerifyBy()));
            line.add(ImpSampleTime.format(r.getVerifyTime()));
            line.add(int1(r.getArchived()) ? "已归档" : "未归档");
            line.add(ImpSampleTime.format(r.getArchiveTime()));
            lines.add(line);
        }
        byte[] bytes = ImpExcel.write(lines);

        // 文件名带点位编号与统计日期（GMT+8 业务日，与页面同一把尺）；同点位同日导两次文件名也相同
        String sitePart = StringUtils.isNotEmpty(siteNo) ? siteNo.trim() : "ALL";
        SimpleDateFormat dayFmt = new SimpleDateFormat("yyyyMMdd");
        dayFmt.setTimeZone(ImpSampleTime.ZONE);
        String dayPart = dayFmt.format(new Date());
        String fileName = "检测数据明细_" + sitePart + "_" + dayPart + ".xlsx";
        response.setContentType(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet;charset=UTF-8");
        FileUtils.setAttachmentResponseHeader(response, fileName);
        response.setContentLength(bytes.length);
        response.getOutputStream().write(bytes);
        response.getOutputStream().flush();
    }

    @Log(title = "本期上期批次差异", action = "diff")
    @ApiOperation(value = "本期/上期差异", notes = "按同批次口径比，点名行次与两边原始行")
    @GetMapping("/diff")
    @RequiresPermissions("zw:zwImpRow:list")
    @ResponseBody
    public AjaxResult diff(@RequestParam("current") String current,
                           @RequestParam("previous") String previous) {
        List<BatchDiffer.BatchDiff> diffs = zwImpRowService.periodDiff(
                splitBatchNos(current), splitBatchNos(previous));
        List<Map<String, Object>> view = new ArrayList<Map<String, Object>>();
        for (BatchDiffer.BatchDiff d : diffs) {
            Map<String, Object> m = new LinkedHashMap<String, Object>();
            m.put("batchNo", d.batchNo);
            m.put("kind", d.kind.name());
            m.put("kindText", d.kind == BatchDiffer.Kind.ONLY_CURRENT ? "本期新到批次"
                    : d.kind == BatchDiffer.Kind.ONLY_PREVIOUS ? "本期缺席批次" : "原始行对不上");
            List<Map<String, Object>> ms = new ArrayList<Map<String, Object>>();
            for (BatchDiffer.RowMismatch mm : d.mismatches) {
                Map<String, Object> row = new LinkedHashMap<String, Object>();
                row.put("rowNo", mm.rowNo);
                row.put("currentRaw", mm.currentRaw);
                row.put("previousRaw", mm.previousRaw);
                ms.add(row);
            }
            m.put("mismatches", ms);
            view.add(m);
        }
        return AjaxResult.successData(200, view);
    }

    /** 逗号/空白分隔的批次号清单（去空去重保序） */
    private static List<String> splitBatchNos(String text) {
        List<String> out = new ArrayList<String>();
        if (text == null) {
            return out;
        }
        for (String t : text.split("[,，\\s]+")) {
            String x = t.trim();
            if (!x.isEmpty() && !out.contains(x)) {
                out.add(x);
            }
        }
        return out;
    }

    private static String s(String v) {
        return v == null ? "" : v;
    }

    private static boolean int1(Integer v) {
        return v != null && v.intValue() == 1;
    }

    private static String flagText(Integer flag, boolean validColumn) {
        if (flag == null) {
            return "";
        }
        if (flag.intValue() == 1) {
            return validColumn ? "有效" : "已复核";
        }
        return validColumn ? "无效" : "未复核";
    }

    private static String checkText(Integer check) {
        if (check == null) {
            return "";
        }
        switch (check.intValue()) {
            case 1:
                return "格式合格";
            case 2:
                return "异常";
            default:
                return "待校";
        }
    }

    private static String verifyText(Integer result) {
        if (result == null) {
            return "";
        }
        return result.intValue() == 1 ? "核实成" : "核实不成";
    }
}
