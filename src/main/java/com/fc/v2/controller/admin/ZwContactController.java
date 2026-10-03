package com.fc.v2.controller.admin;

import java.util.ArrayList;
import java.util.List;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.common.base.BaseController;
import com.fc.v2.common.domain.AjaxResult;
import com.fc.v2.common.domain.ResultTable;
import com.fc.v2.common.log.Log;
import com.fc.v2.model.auto.TZwContact;
import com.fc.v2.service.ITZwContactService;
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
 * 供水户联系人名册 Controller。订正只改名册，不动任何上报单上已钉的联系人快照。
 *
 * @author fuce
 * @date 2026-10-03
 */
@Api(value = "供水户联系人名册")
@Controller
@RequestMapping("/zwContact")
public class ZwContactController extends BaseController {

    @Autowired
    private ITZwContactService zwContactService;

    @GetMapping("/view")
    @RequiresPermissions("zw:zwContact:edit")
    public String view(ModelMap model) {
        model.put("contacts", zwContactService.selectList(new QueryWrapper<TZwContact>()
                .eq("del_flag", 0).orderByAsc("site_no", "account_no")));
        return "admin/zwContact/list";
    }

    @Log(title = "联系人名册查询", action = "list")
    @GetMapping("/listData")
    @RequiresPermissions("zw:zwContact:edit")
    @ResponseBody
    public ResultTable listData(TZwContact query) {
        QueryWrapper<TZwContact> qw = new QueryWrapper<TZwContact>().eq("del_flag", 0);
        qw.like(StringUtils.isNotEmpty(query.getSiteNo()), "site_no", query.getSiteNo());
        qw.eq(StringUtils.isNotNull(query.getStatus()), "status", query.getStatus());
        qw.orderByAsc("site_no", "account_no");
        startPage();
        PageInfo<TZwContact> page = new PageInfo<TZwContact>(zwContactService.selectList(qw));
        return pageTable(page.getList(), page.getTotal());
    }

    @Log(title = "联系人新增", action = "add")
    @PostMapping("/add")
    @RequiresPermissions("zw:zwContact:edit")
    @ResponseBody
    public AjaxResult add(TZwContact contact) {
        List<String> msg = new ArrayList<String>();
        TZwContact c = zwContactService.add(contact, ShiroUtils.getLoginName(), msg);
        return c == null ? AjaxResult.error(msg.isEmpty() ? "新增未收受" : msg.get(0))
                : AjaxResult.successData(200, c);
    }

    @Log(title = "联系人订正", action = "edit")
    @PostMapping("/edit")
    @RequiresPermissions("zw:zwContact:edit")
    @ResponseBody
    public AjaxResult edit(TZwContact contact) {
        List<String> msg = new ArrayList<String>();
        TZwContact c = zwContactService.edit(contact, ShiroUtils.getLoginName(), msg);
        return c == null ? AjaxResult.error(msg.isEmpty() ? "订正未收受" : msg.get(0))
                : AjaxResult.successData(200, c);
    }

    @Log(title = "联系人停用", action = "disable")
    @PostMapping("/disable")
    @RequiresPermissions("zw:zwContact:edit")
    @ResponseBody
    public AjaxResult disable(Long id) {
        return zwContactService.disable(id, ShiroUtils.getLoginName())
                ? AjaxResult.success("已停用留痕（不删除，旧单快照不受影响）")
                : AjaxResult.error("停用未生效（不存在或已停用）");
    }
}
