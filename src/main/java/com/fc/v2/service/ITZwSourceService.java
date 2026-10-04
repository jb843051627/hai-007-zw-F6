package com.fc.v2.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.fc.v2.model.auto.TZwSource;

import java.util.List;

/**
 * 水源地/水厂底档 Service接口
 *
 * @author fuce
 * @date 2026-10-04
 */
public interface ITZwSourceService {

    /** 按主键查询（退役底档照样查得到） */
    TZwSource selectTZwSourceById(Long id);

    /** 按条件查询列表（分页由调用方统一处理） */
    List<TZwSource> selectTZwSourceList(Wrapper<TZwSource> queryWrapper);

    /**
     * 立底档。底档编号由系统发号，入参里夹带的 siteNo 一律不认；
     * 名称、类别、省—市—区—取水口行政区划、三道上限缺一不立。
     */
    TZwSource createSource(TZwSource record);

    /**
     * 订正底档。只有在用底档能改；已退役的当场封口，谁回头改都不收。
     * 编号、类别挂着的卡都认，编号不在服务层改名之列。
     */
    TZwSource updateSource(TZwSource record);

    /**
     * 退役封口（水源枯竭/达使用年限）。封口后新卡不许再挂到它名下，
     * 先前挂过的卡仍可查询、但同样回头改不得。
     */
    TZwSource retire(Long id);
}
