package com.fc.v2.service;

import java.util.List;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.fc.v2.model.auto.TZwContact;

/**
 * 供水户联系人名册 Service接口。
 *
 * <p>名册可随时订正（新增、改名改号、停用留痕）；已反映到上报单的联系人快照不受名册订正影响——
 * "当时取的是哪个号"以单上快照为准。停用不删除（取错/失联作废也要查得到）。
 *
 * @author fuce
 * @date 2026-10-03
 */
public interface ITZwContactService {

    List<TZwContact> selectList(Wrapper<TZwContact> queryWrapper);

    TZwContact selectById(Long id);

    /** 该点位当前可用联系人（供反映页下拉） */
    List<TZwContact> listUsable(String siteNo);

    /** 新增联系人（同点位同供水户编号已存在则拒） */
    TZwContact add(TZwContact contact, String operator, List<String> rejectMsg);

    /** 改名改号订正（已停用行也允许改回信息并重新启用） */
    TZwContact edit(TZwContact contact, String operator, List<String> rejectMsg);

    /** 停用留痕（不删除） */
    boolean disable(Long id, String operator);
}
