package com.fc.v2.service;

import java.util.List;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.model.auto.TZwImpBatch;
import com.fc.v2.model.auto.TZwImpRow;
import com.fc.v2.service.zw.BatchDiffer;
import com.fc.v2.service.zw.ImpImportResult;
import com.fc.v2.service.zw.ImpRowInspector;

/**
 * 检测数据导入核对行 Service接口。
 *
 * <p>一条主线：<b>整批导入校验 → 异常确认 → 一批一次核实 → 归档锁死 → 报表照台账生成</b>。
 * 行表 {@code t_zw_imp_row} 是唯一准据的明细表，批次头 {@code t_zw_imp_batch} 记一批一次的定论；
 * 报表不另存数据，只从这两张表照抄。
 *
 * @author fuce
 * @date 2026-09-14
 */
public interface ITZwImpRowService {

    /** 按主键回查单行（下钻：原始行/校验/复核/核实并排看） */
    TZwImpRow selectTZwImpRowById(Long id);

    /** 按批次号回查批次头 */
    TZwImpBatch selectBatchByNo(String batchNo);

    /** 按主键回查批次头 */
    TZwImpBatch selectBatchById(Long id);

    /** 明细台账查询（条件由调用方组，批次/点位/有效/复核/校验结论/归档皆可筛） */
    List<TZwImpRow> selectTZwImpRowList(QueryWrapper<TZwImpRow> queryWrapper);

    /** 批次下钻：该批全部当前版本行，按原表行次升序（整批拦下的异常行也在） */
    List<TZwImpRow> listBatchRows(String batchNo);

    /** 台账上的批次头列表（核实结论/核实人/核实时间/归档标识只读展示） */
    List<TZwImpBatch> selectBatchList(QueryWrapper<TZwImpBatch> queryWrapper);

    /**
     * 整份文件导入：一份文件只准一个采样批次。
     * <ul>
     *   <li>空文件/多批次/超单批上限：文件级错误，一笔不写，回值 {@code fileError} 点明；</li>
     *   <li>逐行过 {@link ImpRowInspector}：读数/点位/采样时间/申报规则代号任一硬错，
     *       <b>整批拦在库外</b>——异常行全部录进明细台账，正式账一行不写；</li>
     *   <li>全格合格才落正式账，定级三件套（等级/规则代号/规则生效日）由唯一评价出口钉死；</li>
     *   <li>已核实（或已归档）的批次重导一律拒绝，已核实的行不会被翻出来重复核；
     *       未核实批次同内容重导幂等、原样返回，改后内容重导以旧行置删留痕、新版入账。</li>
     * </ul>
     */
    ImpImportResult importRows(String fileName, List<ImpRowInspector.Input> rows, String operator);

    /**
     * 异常确认：只有校验异常（check_status=2）且未归档的行可确认，记确认人/时刻。
     * 重复确认幂等（返回既有状态），归档行拒绝。
     */
    boolean confirmAnomaly(Long rowId, String operator);

    /**
     * 一批一次核实，核实过（无论成或不成）即归只读，同批重导也不会翻出来重复核。
     * {@code result}=1 核实成（整批归档、原样锁死）；{@code result}=2 核实不成（必填原因，同样是终态，
     * 纠正后的数据须以新批次号重新导入，不许在原批次上翻案）。
     * 批次内尚有异常未确认时不许核实；已核实/已归档再提一律拒绝。
     *
     * @return 落定后的批次头；被拒返回 null，拒绝原因写进 {@code rejectMsg} 单元素出口
     */
    TZwImpBatch verifyBatch(String batchNo, int result, String reason, String operator);

    /** 与 {@link #verifyBatch} 同，但把被拒原因写进 {@code rejectMsg} 单元素出口，免得页面只收到一个 null */
    TZwImpBatch verifyBatch(String batchNo, int result, String reason, String operator, List<String> rejectMsg);

    /**
     * 报表取数（也是导出的唯一取数口，页面报表与导出同源）：只取正式账（status=1）当前版本行，
     * 按点位代号/是否有效/是否已复核筛，顺序钉死 批次号升序+原表行次升序。
     */
    List<TZwImpRow> reportRows(String siteNo, Integer validFlag, Integer reviewedFlag);

    /**
     * 本期与上期差异：按<b>同一个批次口径</b>（batch_no 归堆、row_no 对齐），
     * 差异点名到行与两边原始行原文。批次号清单由调用方给出（本期选了哪几批、上期对照哪几批）。
     */
    List<BatchDiffer.BatchDiff> periodDiff(List<String> currentBatchNos, List<String> previousBatchNos);
}
