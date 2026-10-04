package com.fc.v2.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.common.base.BaseController;
import com.fc.v2.common.domain.AjaxResult;
import com.fc.v2.common.domain.ResultTable;
import com.fc.v2.common.log.Log;
import com.fc.v2.model.auto.TZwSource;
import com.fc.v2.service.ITZwSourceService;
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
 * 水源地/水厂底档 Controller
 *
 * <p>一档一家：底档编号只由系统发，表单上没有手填格；退役当场封口，封口后只剩查询。
 *
 * @author fuce
 * @date 2026-10-04
 */
@Api(value = "水源地/水厂底档")
@Controller
@RequestMapping("/zwSource")
public class ZwSourceController extends BaseController {

    private final String prefix = "admin/zwSource";

    @Autowired
    private ITZwSourceService zwSourceService;

    @ApiOperation(value = "底档台账跳转", notes = "底档台账跳转")
    @GetMapping("/view")
    @RequiresPermissions("zw:zwSource:view")
    public String view(ModelMap model) {
        return prefix + "/list";
    }

    @Log(title = "水源地/水厂底档台账查询", action = "list")
    @ApiOperation(value = "分页查询", notes = "在用、已退役都翻得到，退役的只是改不动")
    @GetMapping("/list")
    @RequiresPermissions("zw:zwSource:list")
    @ResponseBody
    public ResultTable list(TZwSource record) {
        QueryWrapper<TZwSource> queryWrapper = new QueryWrapper<TZwSource>();
        queryWrapper.like(StringUtils.isNotEmpty(record.getSiteNo()), "site_no", record.getSiteNo());
        queryWrapper.like(StringUtils.isNotEmpty(record.getSiteName()), "site_name", record.getSiteName());
        queryWrapper.eq(StringUtils.isNotEmpty(record.getSiteType()), "site_type", record.getSiteType());
        queryWrapper.eq(StringUtils.isNotNull(record.getStatus()), "status", record.getStatus());
        queryWrapper.orderByAsc("site_no");
        startPage();
        PageInfo<TZwSource> page =
                new PageInfo<TZwSource>(zwSourceService.selectTZwSourceList(queryWrapper));
        return pageTable(page.getList(), page.getTotal());
    }

    @ApiOperation(value = "立档页跳转", notes = "编号由系统发，本页没有编号格子")
    @GetMapping("/add")
    @RequiresPermissions("zw:zwSource:add")
    public String add(ModelMap model) {
        return prefix + "/add";
    }

    @Log(title = "水源地/水厂底档立档", action = "add")
    @ApiOperation(value = "立底档", notes = "名称/类别/省—市—区—取水口/三道上限缺一不立；编号系统发")
    @PostMapping("/add")
    @RequiresPermissions("zw:zwSource:add")
    @ResponseBody
    public AjaxResult add(TZwSource record) {
        TZwSource saved = zwSourceService.createSource(record);
        return saved == null
                ? AjaxResult.error("名称、类别、省—市—区—取水口行政区划、三道递增上限缺一不立；请核对后重立")
                : AjaxResult.successData(200, saved);
    }

    @ApiOperation(value = "订正页跳转", notes = "退役底档不进订正页，只可翻看")
    @GetMapping("/edit/{id}")
    @RequiresPermissions("zw:zwSource:edit")
    public String editPage(@PathVariable("id") Long id, ModelMap model) {
        TZwSource source = zwSourceService.selectTZwSourceById(id);
        if (source == null || (source.getStatus() != null && source.getStatus() != 0)) {
            return prefix + "/list";
        }
        model.put("source", source);
        return prefix + "/edit";
    }

    @Log(title = "水源地/水厂底档订正", action = "edit")
    @ApiOperation(value = "订正保存", notes = "只订正名称/区划/三道上限；编号与类别不动，退役封口后不收")
    @PostMapping("/edit")
    @RequiresPermissions("zw:zwSource:edit")
    @ResponseBody
    public AjaxResult editSave(TZwSource record) {
        TZwSource saved = zwSourceService.updateSource(record);
        return saved == null
                ? AjaxResult.error("底档不存在或已退役封口；三道上限要改须一起改成递增")
                : AjaxResult.successData(200, saved);
    }

    @Log(title = "水源地/水厂底档退役封口", action = "retire")
    @ApiOperation(value = "退役封口", notes = "水源枯竭/达使用年限当场封口：新卡禁挂，旧卡可查不可改")
    @PostMapping("/retire")
    @RequiresPermissions("zw:zwSource:retire")
    @ResponseBody
    public AjaxResult retire(@RequestParam Long id) {
        TZwSource source = zwSourceService.retire(id);
        return source == null
                ? AjaxResult.error("只有在用底档能退役封口")
                : AjaxResult.successData(200, source);
    }
}
