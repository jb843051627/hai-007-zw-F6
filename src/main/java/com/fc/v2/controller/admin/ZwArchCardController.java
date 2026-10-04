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

import java.util.List;

/**
 * 水源地/水厂档案卡 Controller
 *
 * <p>页面上没有卡号、层级、校验码的手填格子：卡号系统发、层级系统折、校验码钉住才定形，
 * 三样只展示不录入。每次保存都是重录一版——旧版退下备查，页面只显最近那一版；
 * 当初是谁立的、最近一次是谁改的，台账上直接摆出来。
 *
 * @author fuce
 * @date 2026-09-12
 */
@Api(value = "水源地/水厂档案卡")
@Controller
@RequestMapping("/ZwArchCardController")
public class ZwArchCardController extends BaseController {

    private final String prefix = "admin/zwArchCard";

    @Autowired
    private ITZwArchCardService zwArchCardService;

    @Autowired
    private TZwSourceMapper zwSourceMapper;

    @ApiOperation(value = "分页跳转", notes = "分页跳转")
    @GetMapping("/view")
    @RequiresPermissions("zw:zwArchCard:view")
    public String view(ModelMap model) {
        return prefix + "/list";
    }

    @Log(title = "水源地/水厂档案卡集合查询", action = "list")
    @ApiOperation(value = "分页查询", notes = "页面上只显最近那一版")
    @GetMapping("/list")
    @RequiresPermissions("zw:zwArchCard:list")
    @ResponseBody
    public ResultTable list(TZwArchCard record) {
        QueryWrapper<TZwArchCard> queryWrapper = new QueryWrapper<TZwArchCard>();
        queryWrapper.like(StringUtils.isNotEmpty(record.getBillNo()), "bill_no", record.getBillNo());
        queryWrapper.eq(StringUtils.isNotEmpty(record.getSiteNo()), "site_no", record.getSiteNo());
        queryWrapper.eq(StringUtils.isNotNull(record.getStatus()), "status", record.getStatus());
        queryWrapper.orderByDesc("id");
        startPage();
        PageInfo<TZwArchCard> page =
                new PageInfo<TZwArchCard>(zwArchCardService.selectTZwArchCardList(queryWrapper));
        return pageTable(page.getList(), page.getTotal());
    }

    @ApiOperation(value = "备查各版", notes = "顺卡号把各版一次翻全，含已退下的旧版")
    @GetMapping("/history")
    @RequiresPermissions("zw:zwArchCard:list")
    @ResponseBody
    public AjaxResult history(String billNo) {
        return AjaxResult.successData(200, zwArchCardService.selectTZwArchCardHistory(billNo));
    }

    @ApiOperation(value = "立卡页跳转", notes = "归属底档只列在用；卡号/层级/校验码系统给，页面不留格子")
    @GetMapping("/add")
    @RequiresPermissions("zw:zwArchCard:add")
    public String add(ModelMap model) {
        model.put("sites", activeSites());
        return prefix + "/add";
    }

    @Log(title = "水源地/水厂档案卡新增", action = "add")
    @ApiOperation(value = "新增", notes = "挂接只凭编号；卡号系统发号，入参私编的号不采用")
    @PostMapping("/add")
    @RequiresPermissions("zw:zwArchCard:add")
    @ResponseBody
    public AjaxResult add(TZwArchCard record) {
        return zwArchCardService.insertTZwArchCard(record) > 0
                ? AjaxResult.success("立卡成功，卡号由系统发出")
                : AjaxResult.error("底档编号对不上或已退役封口，立卡未生效");
    }

    @ApiOperation(value = "重录页跳转", notes = "另起一版；退役底档名下的卡只查不改")
    @GetMapping("/edit/{id}")
    @RequiresPermissions("zw:zwArchCard:edit")
    public String edit(@PathVariable("id") Long id, ModelMap model) {
        model.put("card", zwArchCardService.selectTZwArchCardById(id));
        model.put("sites", activeSites());
        return prefix + "/edit";
    }

    @Log(title = "水源地/水厂档案卡修改", action = "edit")
    @ApiOperation(value = "修改保存", notes = "重录一版：新版落当前位置，旧版退下备查")
    @PostMapping("/edit")
    @RequiresPermissions("zw:zwArchCard:edit")
    @ResponseBody
    public AjaxResult editSave(TZwArchCard record) {
        return zwArchCardService.updateTZwArchCard(record) > 0
                ? AjaxResult.success("已重录一版，旧版退下备查")
                : AjaxResult.error("底档已退役封口或编号对不上，重录未生效");
    }

    @Log(title = "水源地/水厂档案卡删除", action = "remove")
    @ApiOperation(value = "删除", notes = "删除")
    @DeleteMapping("/remove")
    @RequiresPermissions("zw:zwArchCard:remove")
    @ResponseBody
    public AjaxResult remove(String ids) {
        return toAjax(zwArchCardService.deleteTZwArchCardByIds(ids));
    }

    /** 在用水源点/水厂名录：立卡与改挂只许挂到在用底档名下 */
    private List<TZwSource> activeSites() {
        return zwSourceMapper.selectList(new QueryWrapper<TZwSource>()
                .eq("status", 0)
                .eq("del_flag", 0)
                .orderByAsc("site_no"));
    }
}
