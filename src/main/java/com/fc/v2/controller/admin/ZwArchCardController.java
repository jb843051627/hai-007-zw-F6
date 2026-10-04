package com.fc.v2.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.common.base.BaseController;
import com.fc.v2.common.domain.AjaxResult;
import com.fc.v2.common.domain.ResultTable;
import com.fc.v2.common.log.Log;
import com.fc.v2.mapper.auto.TZwSourceMapper;
import com.fc.v2.model.auto.TZwArchCard;
import com.fc.v2.model.auto.TZwSource;
import com.fc.v2.service.ITZwArchCardService;
import com.fc.v2.util.StringUtils;
import com.github.pagehelper.PageInfo;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

/**
 * 水源地/水厂档案卡 Controller
 *
 * <p>页面只摆"立卡 / 重录一版 / 核对 / 压死 / 版本倒查"几个口：卡号、版次、层级、校验串
 * 全由系统发，表单里没有手填格；台账只显每张卡的最新一版，旧版进倒查页翻看。
 *
 * @author fuce
 * @date 2026-09-12
 */
@Api(value = "水源地/水厂档案卡")
@Controller
@RequestMapping("/zwArchCard")
public class ZwArchCardController extends BaseController {

    private final String prefix = "admin/zwArchCard";

    @Autowired
    private ITZwArchCardService zwArchCardService;
    @Autowired
    private TZwSourceMapper zwSourceMapper;

    @ApiOperation(value = "台账跳转", notes = "台账跳转")
    @GetMapping("/view")
    @RequiresPermissions("zw:zwArchCard:view")
    public String view(ModelMap model) {
        return prefix + "/list";
    }

    @Log(title = "水源地/水厂档案卡台账查询", action = "list")
    @ApiOperation(value = "分页查询", notes = "每张卡只显当前在用的最新一版，旧版另走版本倒查")
    @GetMapping("/list")
    @RequiresPermissions("zw:zwArchCard:list")
    @ResponseBody
    public ResultTable list(TZwArchCard record) {
        QueryWrapper<TZwArchCard> queryWrapper = new QueryWrapper<TZwArchCard>();
        queryWrapper.like(StringUtils.isNotEmpty(record.getBillNo()), "bill_no", record.getBillNo());
        queryWrapper.eq(StringUtils.isNotEmpty(record.getSiteNo()), "site_no", record.getSiteNo());
        queryWrapper.eq(StringUtils.isNotNull(record.getGradeLevel()), "grade_level", record.getGradeLevel());
        queryWrapper.eq(StringUtils.isNotNull(record.getStatus()), "status", record.getStatus());
        queryWrapper.orderByDesc("create_time");
        startPage();
        PageInfo<TZwArchCard> page =
                new PageInfo<TZwArchCard>(zwArchCardService.selectTZwArchCardList(queryWrapper));
        return pageTable(page.getList(), page.getTotal());
    }

    @ApiOperation(value = "立卡页跳转", notes = "在用底档名录由系统带出，挂接只凭编号，卡号系统发")
    @GetMapping("/add")
    @RequiresPermissions("zw:zwArchCard:add")
    public String add(ModelMap model) {
        model.put("sites", activeSources());
        return prefix + "/add";
    }

    @Log(title = "水源地/水厂档案卡立卡", action = "add")
    @ApiOperation(value = "立卡", notes = "卡号系统发、层级系统折；退役底档名下不许新挂")
    @PostMapping("/add")
    @RequiresPermissions("zw:zwArchCard:add")
    @ResponseBody
    public AjaxResult add(@RequestParam(required = false) Long siteId,
                          @RequestParam(required = false) BigDecimal qty,
                          @RequestParam(required = false) BigDecimal fineAmt,
                          @RequestParam(required = false) String remark) {
        TZwArchCard record = new TZwArchCard();
        record.setSiteId(siteId);
        record.setQty(qty);
        record.setFineAmt(fineAmt);
        record.setRemark(remark);
        TZwArchCard saved = zwArchCardService.insertTZwArchCard(record);
        return saved == null
                ? AjaxResult.error("立卡须挂在用底档、供水规模须有数；请核对后重立")
                : AjaxResult.successData(200, saved);
    }

    @ApiOperation(value = "重录页跳转", notes = "从当前在用最新一版进，改规模/改挂重折层级")
    @GetMapping("/revise/{id}")
    @RequiresPermissions("zw:zwArchCard:edit")
    public String revisePage(@PathVariable("id") Long id, ModelMap model) {
        TZwArchCard current = zwArchCardService.selectTZwArchCardById(id);
        if (current == null) {
            return prefix + "/list";
        }
        model.put("card", current);
        model.put("sites", activeSources());
        return prefix + "/revise";
    }

    @Log(title = "水源地/水厂档案卡重录新版", action = "revise")
    @ApiOperation(value = "重录一版", notes = "旧版原样留痕备查，另起 nodeNo+1；层级重折、从待核对重走")
    @PostMapping("/revise")
    @RequiresPermissions("zw:zwArchCard:edit")
    @ResponseBody
    public AjaxResult revise(@RequestParam Long id,
                             @RequestParam(required = false) Long siteId,
                             @RequestParam(required = false) BigDecimal qty,
                             @RequestParam(required = false) BigDecimal fineAmt,
                             @RequestParam(required = false) String remark) {
        TZwArchCard record = new TZwArchCard();
        record.setId(id);
        record.setSiteId(siteId);
        record.setQty(qty);
        record.setFineAmt(fineAmt);
        record.setRemark(remark);
        TZwArchCard next = zwArchCardService.revise(record);
        return next == null
                ? AjaxResult.error("只许从最新版重录、只许挂在用底档，且至少有一样改动；请刷新核对")
                : AjaxResult.successData(200, next);
    }

    @Log(title = "档案卡核对", action = "confirm")
    @ApiOperation(value = "核对", notes = "待核对 → 已核对；只收当前最新版")
    @PostMapping("/confirm")
    @RequiresPermissions("zw:zwArchCard:edit")
    @ResponseBody
    public AjaxResult confirm(@RequestParam Long id) {
        TZwArchCard card = zwArchCardService.confirm(id);
        return card == null ? AjaxResult.error("只有待核对的最新版能核对") : AjaxResult.successData(200, card);
    }

    @Log(title = "档案卡压死钉串", action = "pin")
    @ApiOperation(value = "压死", notes = "已核对 → 已压死；校验串到此步才定形，定形后该行不可改")
    @PostMapping("/pin")
    @RequiresPermissions("zw:zwArchCard:edit")
    @ResponseBody
    public AjaxResult pin(@RequestParam Long id) {
        TZwArchCard card = zwArchCardService.pin(id);
        return card == null
                ? AjaxResult.error("只有已核对的最新版能压死；请先核对，且不许跳步")
                : AjaxResult.successData(200, card);
    }

    @ApiOperation(value = "版本倒查页跳转", notes = "同一卡号各版按版次正序陈列，旧版只许翻不许改")
    @GetMapping("/detail/{billNo}")
    @RequiresPermissions("zw:zwArchCard:view")
    public String detail(@PathVariable("billNo") String billNo, ModelMap model) {
        List<TZwArchCard> versions = zwArchCardService.listVersions(billNo);
        model.put("versions", versions);
        model.put("billNo", billNo);
        return prefix + "/detail";
    }

    @Log(title = "水源地/水厂档案卡删除", action = "remove")
    @ApiOperation(value = "删除", notes = "按卡号整卡软删，各版留痕不物理清除")
    @DeleteMapping("/remove")
    @RequiresPermissions("zw:zwArchCard:remove")
    @ResponseBody
    public AjaxResult remove(String ids) {
        return toAjax(zwArchCardService.deleteTZwArchCardByIds(ids));
    }

    private List<TZwSource> activeSources() {
        return zwSourceMapper.selectList(new QueryWrapper<TZwSource>()
                .eq("status", 0)
                .eq("del_flag", 0)
                .orderByAsc("site_no"));
    }
}
