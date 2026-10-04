package com.fc.v2.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.fc.v2.model.auto.TZwArchCard;

import java.util.List;

/**
 * 水源地/水厂档案卡 Service接口
 *
 * <p>两层底册的下层：卡挂在底档下，挂接只凭编号核对；卡号系统发号；
 * 层级系统折算回填；重录另起一版，旧版退下另存备查；校验码钉住才定形、随版本同行。
 *
 * @author fuce
 * @date 2026-09-12
 */
public interface ITZwArchCardService {

    /** 按主键查询（只查当前在用的那一版） */
    TZwArchCard selectTZwArchCardById(Long id);

    /** 按条件查询列表——页面上只显最近那一版（分页由调用方统一处理） */
    List<TZwArchCard> selectTZwArchCardList(Wrapper<TZwArchCard> queryWrapper);

    /** 备查层：顺卡号把一张卡的各版一次翻全（含已退下的旧版），版次从小到大 */
    List<TZwArchCard> selectTZwArchCardHistory(String billNo);

    /** 新增（立卡：系统发号、凭编号挂接、层级折算回填） */
    int insertTZwArchCard(TZwArchCard record);

    /** 修改（重录一版：新版落当前位置，旧版退下备查） */
    int updateTZwArchCard(TZwArchCard record);

    /** 批量删除 */
    int deleteTZwArchCardByIds(String ids);

    /** 按主键删除 */
    int deleteTZwArchCardById(Long id);
}
