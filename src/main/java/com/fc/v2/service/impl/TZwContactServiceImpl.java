package com.fc.v2.service.impl;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.fc.v2.mapper.auto.TZwContactMapper;
import com.fc.v2.model.auto.TZwContact;
import com.fc.v2.service.ITZwContactService;

/**
 * 供水户联系人名册 Service业务层处理。订正只改名册本身，不动任何已落上报单快照。
 *
 * @author fuce
 * @date 2026-10-03
 */
@Service
public class TZwContactServiceImpl implements ITZwContactService {

    private static final int USABLE = 0;

    @javax.annotation.Resource
    private TZwContactMapper zwContactMapper;

    @Override
    public List<TZwContact> selectList(Wrapper<TZwContact> queryWrapper) {
        return this.zwContactMapper.selectList(queryWrapper);
    }

    @Override
    public TZwContact selectById(Long id) {
        return this.zwContactMapper.selectById(id);
    }

    @Override
    public List<TZwContact> listUsable(String siteNo) {
        return this.zwContactMapper.selectList(new QueryWrapper<TZwContact>()
                .eq("del_flag", 0)
                .eq("status", USABLE)
                .eq(notEmpty(siteNo), "site_no", siteNo == null ? null : siteNo.trim())
                .orderByAsc("site_no", "account_no"));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TZwContact add(TZwContact contact, String operator, List<String> rejectMsg) {
        if (contact == null || !valid(contact)) {
            return reject(rejectMsg, "点位、供水户、联系人、电话缺一不可");
        }
        Integer dup = this.zwContactMapper.selectCount(new QueryWrapper<TZwContact>()
                .eq("site_no", contact.getSiteNo().trim())
                .eq("account_no", contact.getAccountNo().trim())
                .eq("del_flag", 0));
        if (dup != null && dup.intValue() > 0) {
            return reject(rejectMsg, "同点位同供水户编号已有名册行，请订正原行而不是再录");
        }
        contact.setId(null);
        contact.setSiteNo(contact.getSiteNo().trim());
        contact.setAccountNo(contact.getAccountNo().trim());
        contact.setStatus(USABLE);
        contact.setDelFlag(0);
        this.zwContactMapper.insert(contact);
        return contact;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TZwContact edit(TZwContact contact, String operator, List<String> rejectMsg) {
        if (contact == null || contact.getId() == null || !valid(contact)) {
            return reject(rejectMsg, "点位、供水户、联系人、电话缺一不可");
        }
        TZwContact old = this.zwContactMapper.selectById(contact.getId());
        if (old == null || int1(old.getDelFlag())) {
            return reject(rejectMsg, "名册行不存在");
        }
        int n = this.zwContactMapper.update(null, new UpdateWrapper<TZwContact>()
                .set("site_no", contact.getSiteNo().trim())
                .set("account_no", contact.getAccountNo().trim())
                .set("account_name", contact.getAccountName().trim())
                .set("contact_name", contact.getContactName().trim())
                .set("contact_phone", contact.getContactPhone().trim())
                .set("status", contact.getStatus() == null ? USABLE : contact.getStatus())
                .set("remark", contact.getRemark())
                .eq("id", contact.getId())
                .eq("del_flag", 0));
        return n == 0 ? reject(rejectMsg, "名册行已被并发改动") : this.zwContactMapper.selectById(contact.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean disable(Long id, String operator) {
        return this.zwContactMapper.update(null, new UpdateWrapper<TZwContact>()
                .set("status", 1)
                .eq("id", id)
                .eq("status", 0)
                .eq("del_flag", 0)) > 0;
    }

    private static boolean notEmpty(String v) {
        return v != null && !v.trim().isEmpty();
    }

    private static boolean valid(TZwContact c) {
        return notEmpty(c.getSiteNo()) && notEmpty(c.getAccountNo())
                && notEmpty(c.getAccountName()) && notEmpty(c.getContactName())
                && notEmpty(c.getContactPhone());
    }

    private static boolean int1(Integer v) {
        return v != null && v.intValue() == 1;
    }

    private static TZwContact reject(List<String> rejectMsg, String msg) {
        if (rejectMsg != null) {
            rejectMsg.clear();
            rejectMsg.add(msg);
        }
        return null;
    }
}
