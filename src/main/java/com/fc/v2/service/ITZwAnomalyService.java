package com.fc.v2.service;

import java.util.List;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.fc.v2.model.auto.TZwAnomaly;
import com.fc.v2.model.auto.TZwImpRow;
import com.fc.v2.service.zw.AnomalyView;

/**
 * 水质异常判定登记 Service接口。
 *
 * <p>登记在检测行落账的同一事务里由系统自动带出（{@link #syncOnImport}），人手不填；
 * 点开登记能反查到当日原始检测记录、当天生效的标准线快照与完整判定过程
 * （{@link #buildView}）。判定更正只改本条、走唯一定级出口重算；
 * 已上报/已反映供水户/已结案归档的，任何更正 0 行被拒。
 *
 * @author fuce
 * @date 2026-10-03
 */
public interface ITZwAnomalyService {

    /** 按主键回查登记 */
    TZwAnomaly selectById(Long id);

    /** 台账查询 */
    List<TZwAnomaly> selectList(Wrapper<TZwAnomaly> queryWrapper);

    /** 按原始检测行查登记（一行至多一条） */
    TZwAnomaly findByImpRow(Long impRowId);

    /**
     * 检测数据导入落账的同一事务内调用：旧版行被置删的，其未上报登记随版留痕置删；
     * 新入正式账且判为关注及以上的行，各自动带出一条登记。已上报的登记一根手指不碰。
     */
    void syncOnImport(String batchNo);

    /**
     * 判定更正：以原始检测行当前读数/时间/类别走唯一评价出口重新判定，把本条快照整体改写，
     * correct_times 加一。已上报/已反映/已结案归档一律拒（rejectMsg 带原因）。
     * 重算后不再构成异常（降到合格档）的，登记随本次更正置删留痕。
     */
    TZwAnomaly correct(Long id, String note, String operator, List<String> rejectMsg);

    /** 下钻视图：登记 + 当日原始检测行 + 当天现行（或让位）标准线条文 + 所挂上报单 */
    AnomalyView buildView(Long id);
}
