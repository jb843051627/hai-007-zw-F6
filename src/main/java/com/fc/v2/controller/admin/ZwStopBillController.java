package com.fc.v2.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.common.base.BaseController;
import com.fc.v2.common.domain.AjaxResult;
import com.fc.v2.common.domain.ResultTable;
import com.fc.v2.common.log.Log;
import com.fc.v2.mapper.auto.TZwSourceMapper;
import com.fc.v2.model.auto.TZwSource;
import com.fc.v2.model.auto.TZwStopBill;
import com.fc.v2.service.ITZwStopBillService;
import com.fc.v2.service.zw.StopBillView;
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

import java.util.List;

/**
 * 停供/限供申请签核 Controller（approval-chain 形状：提单 + 四道关口签核 + 倒查）。
 *
 * <p>页面没有任何直接改关口、改落印枚数、改名、补印的入口：每个动作只调服务层那套签核方法，
 * 关口过没过、每口几枚名全以服务层 buildView 的同一次点算为准，屏上不许摆第二份。
 *
 * @author fuce
 * @date 2026-09-30
 */
@Api(value = "停供/限供申请签核")
@Controller
@RequestMapping("/zwStopBill")
public class ZwStopBillController extends BaseController {

    private final String prefix = "admin/zwStopBill";

    @Autowired
    private ITZwStopBillService zwStopBillService;
    @Autowired
    private TZwSourceMapper zwSourceMapper;

    @ApiOperation(value = "签核台账跳转", notes = "签核台账跳转")
    @GetMapping("/view")
    @RequiresPermissions("zw:zwStopBill:view")
    public String view(ModelMap model) {
        return prefix + "/list";
    }

    @Log(title = "停供申请签核台账查询", action = "list")
    @ApiOperation(value = "台账查询", notes = "在核/已核讫/已终止各单照常可翻")
    @GetMapping("/list")
    @RequiresPermissions("zw:zwStopBill:list")
    @ResponseBody
    public ResultTable list(TZwStopBill record) {
        QueryWrapper<TZwStopBill> queryWrapper = new QueryWrapper<TZwStopBill>();
        queryWrapper.like(StringUtils.isNotEmpty(record.getBillNo()), "bill_no", record.getBillNo());
        queryWrapper.eq(StringUtils.isNotEmpty(record.getSiteNo()), "site_no", record.getSiteNo());
        queryWrapper.eq(StringUtils.isNotNull(record.getStatus()), "status", record.getStatus());
        queryWrapper.orderByDesc("create_time");
        startPage();
        PageInfo<TZwStopBill> page =
                new PageInfo<TZwStopBill>(zwStopBillService.selectTZwStopBillList(queryWrapper));
        return pageTable(page.getList(), page.getTotal());
    }

    @ApiOperation(value = "提单页跳转", notes = "在用水源点名录与停水事由由系统带出")
    @GetMapping("/add")
    @RequiresPermissions("zw:zwStopBill:add")
    public String add(ModelMap model) {
        List<TZwSource> sites = zwSourceMapper.selectList(new QueryWrapper<TZwSource>()
                .eq("status", 0)
                .eq("del_flag", 0)
                .orderByAsc("site_no"));
        model.put("sites", sites);
        return prefix + "/add";
    }

    @Log(title = "停供申请提出", action = "submit")
    @ApiOperation(value = "业务科提出", notes = "水源点代号/事由/替代方案三样缺一当场挡回；公告按名录带出")
    @PostMapping("/submit")
    @RequiresPermissions("zw:zwStopBill:add")
    @ResponseBody
    public AjaxResult submit(String siteNo, String reason, String altPlan, String opinion) {
        TZwStopBill bill = zwStopBillService.submit(siteNo, reason, altPlan,
                ShiroUtils.getLoginName(), opinion);
        return bill == null
                ? AjaxResult.error("三样要件缺一、事由不在名录或点位不可申请，请核对后重提")
                : AjaxResult.successData(200, bill);
    }

    @ApiOperation(value = "签核倒查页跳转", notes = "四道关口与逐笔落印均可回溯到第一道")
    @GetMapping("/detail/{id}")
    @RequiresPermissions("zw:zwStopBill:view")
    public String detail(@PathVariable("id") Long id, ModelMap model) {
        model.put("view", zwStopBillService.buildView(id));
        return prefix + "/detail";
    }

    @Log(title = "停供申请关口数据", action = "viewData")
    @ApiOperation(value = "关口现况(同一次点算)", notes = "页面刷新只取回这一份，不自算关口与枚数")
    @GetMapping("/viewData/{id}")
    @RequiresPermissions("zw:zwStopBill:view")
    @ResponseBody
    public AjaxResult viewData(@PathVariable("id") Long id) {
        StopBillView view = zwStopBillService.buildView(id);
        return view == null ? AjaxResult.error("单据不存在") : AjaxResult.successData(200, view);
    }

    @Log(title = "停供申请同意", action = "agree")
    @ApiOperation(value = "同意落名", notes = "第二道/第四道走单角色签法；第三道两名评估人各带落名口，互不替签")
    @PostMapping("/agree")
    @RequiresPermissions("zw:zwStopBill:agree")
    @ResponseBody
    public AjaxResult agree(Long id, String signRole, String comment) {
        String who = ShiroUtils.getLoginName();
        TZwStopBill bill = StringUtils.isEmpty(signRole)
                ? zwStopBillService.approve(id, who, comment)
                : zwStopBillService.approve(id, who, signRole, comment);
        return bill == null
                ? AjaxResult.error("本口不收受这笔签：关口次序、落名口或前置核实未过，请刷新核对")
                : AjaxResult.successData(200, bill);
    }

    @Log(title = "停供申请不同意", action = "disagree")
    @ApiOperation(value = "写明不同意", notes = "单到此为止，各栏锁死")
    @PostMapping("/disagree")
    @RequiresPermissions("zw:zwStopBill:agree")
    @ResponseBody
    public AjaxResult disagree(Long id, String signRole, String comment) {
        if (StringUtils.isEmpty(comment)) {
            return AjaxResult.error("写明不同意须留意见");
        }
        String who = ShiroUtils.getLoginName();
        TZwStopBill bill = StringUtils.isEmpty(signRole)
                ? zwStopBillService.reject(id, who, comment)
                : zwStopBillService.reject(id, who, signRole, comment);
        return bill == null ? AjaxResult.error("本口不收受这笔签，请刷新核对") : AjaxResult.successData(200, bill);
    }

    @Log(title = "停供申请主管打回", action = "sendBack")
    @ApiOperation(value = "主管打回", notes = "旧轮签核整轮作废留痕，从第二道重新攒起，作废与补签不摆在一起")
    @PostMapping("/sendBack")
    @RequiresPermissions("zw:zwStopBill:agree")
    @ResponseBody
    public AjaxResult sendBack(Long id, String comment) {
        if (StringUtils.isEmpty(comment)) {
            return AjaxResult.error("打回须写明理由不符之处");
        }
        TZwStopBill bill = zwStopBillService.sendBack(id, ShiroUtils.getLoginName(), comment);
        return bill == null ? AjaxResult.error("只有主管批准口能打回") : AjaxResult.successData(200, bill);
    }

    @Log(title = "停供申请撤回", action = "withdraw")
    @ApiOperation(value = "申请人撤回", notes = "单到此为止，之后谁也不追记")
    @PostMapping("/withdraw")
    @RequiresPermissions("zw:zwStopBill:add")
    @ResponseBody
    public AjaxResult withdraw(Long id, String comment) {
        TZwStopBill bill = zwStopBillService.withdraw(id, ShiroUtils.getLoginName(), comment);
        return bill == null ? AjaxResult.error("已落定的单不能撤回") : AjaxResult.successData(200, bill);
    }
}
