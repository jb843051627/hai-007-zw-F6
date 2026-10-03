package com.fc.v2.service.impl;

import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.fc.v2.mapper.auto.TZwImpBatchMapper;
import com.fc.v2.mapper.auto.TZwImpRowMapper;
import com.fc.v2.mapper.auto.TZwSourceMapper;
import com.fc.v2.model.auto.TZwImpBatch;
import com.fc.v2.model.auto.TZwImpRow;
import com.fc.v2.model.auto.TZwSource;
import com.fc.v2.service.ITZwImpRowService;
import com.fc.v2.service.ITZwLimRuleService;
import com.fc.v2.service.zw.BatchDiffer;
import com.fc.v2.service.zw.ImpImportResult;
import com.fc.v2.service.zw.ImpRowInspector;
import com.fc.v2.service.zw.ImpSampleTime;
import com.fc.v2.service.zw.WaterVerdict;

/**
 * 检测数据导入核对行 Service业务层处理。
 *
 * <p><b>整批口径</b>：一份文件一个采样批次。逐行过 {@link ImpRowInspector}，
 * 读数（水位）、地点、采样时间、申报规则代号任一对不上都是硬错——<b>整批拦在库外</b>，
 * 异常行逐行录进明细台账（{@code status=0}，正式账一行不写），点名到人处理；
 * 只有整份全格合格才落正式账（{@code status=1}），定级三件套（等级/规则代号/规则生效日）
 * 由唯一评价出口 {@link ITZwLimRuleService#evaluate} 当场钉死，库里存的代号与检测当天现行线逐字一致。
 *
 * <p><b>一批一次核实</b>：批次头 {@code t_zw_imp_batch} 的核实结论只准写一次——
 * 核实成整批 {@code archived=1} 原样锁死（行/头两级条件更新，归档后任何写口 0 行被拒）；
 * 核实不成写明原因、不归档，处置后可再次核实。已核实批次重导整份拒绝，
 * 已核实的行不会被翻出来重复核；未核实批次改后重导，旧版行 {@code del_flag=1} 留痕、新版入账。
 *
 * <p><b>明细表是唯一准据</b>：异常的生成、确认、核实、归档全部逐段落 {@code t_zw_imp_row}；
 * 报表/导出只从正式账行照抄，不另存数，报表改了不算数。
 *
 * @author fuce
 * @date 2026-09-14
 */
@Service
public class TZwImpRowServiceImpl implements ITZwImpRowService {

    private static final int MAX_ROWS = 500;

    /** 行落地情形 */
    private static final int ROW_NOT_LEDGER = 0;
    private static final int ROW_LEDGER = 1;
    /** 校验结论 */
    private static final int CHECK_PENDING = 0;
    private static final int CHECK_OK = 1;
    private static final int CHECK_HARD = 2;
    /** 核实结论 */
    private static final int VERIFY_PASS = 1;
    private static final int VERIFY_FAIL = 2;

    @javax.annotation.Resource
    private TZwImpRowMapper zwImpRowMapper;
    @javax.annotation.Resource
    private TZwImpBatchMapper zwImpBatchMapper;
    @javax.annotation.Resource
    private TZwSourceMapper zwSourceMapper;
    @javax.annotation.Resource
    private ITZwLimRuleService zwLimRuleService;
    @javax.annotation.Resource
    private com.fc.v2.service.ITZwAnomalyService zwAnomalyService;

    @Override
    public TZwImpRow selectTZwImpRowById(Long id) {
        return this.zwImpRowMapper.selectById(id);
    }

    @Override
    public TZwImpBatch selectBatchByNo(String batchNo) {
        return this.zwImpBatchMapper.selectOne(new QueryWrapper<TZwImpBatch>()
                .eq("batch_no", batchNo).eq("del_flag", 0));
    }

    @Override
    public TZwImpBatch selectBatchById(Long id) {
        return this.zwImpBatchMapper.selectById(id);
    }

    @Override
    public List<TZwImpRow> selectTZwImpRowList(QueryWrapper<TZwImpRow> queryWrapper) {
        return this.zwImpRowMapper.selectList(queryWrapper);
    }

    @Override
    public List<TZwImpRow> listBatchRows(String batchNo) {
        return this.zwImpRowMapper.selectList(new QueryWrapper<TZwImpRow>()
                .eq("batch_no", batchNo)
                .eq("del_flag", 0)
                .orderByAsc("row_no"));
    }

    @Override
    public List<TZwImpBatch> selectBatchList(QueryWrapper<TZwImpBatch> queryWrapper) {
        return this.zwImpBatchMapper.selectList(queryWrapper);
    }

    /**
     * 整份导入（一个事务）：文件级错误一笔不写；硬错整批拦在库外但异常全量入台账；
     * 全格合格才入正式账。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public ImpImportResult importRows(String fileName, List<ImpRowInspector.Input> rows, String operator) {
        ImpImportResult result = new ImpImportResult();
        if (rows == null || rows.isEmpty()) {
            return ImpImportResult.fileError("文件里没有数据行");
        }
        if (rows.size() > MAX_ROWS) {
            return ImpImportResult.fileError("单批 " + rows.size() + " 行超出上限 " + MAX_ROWS + " 行，整份不入库");
        }
        // 一份文件只准一个采样批次：逐格认批次号，空号/多个号都是文件级错误
        String batchNo = null;
        for (ImpRowInspector.Input in : rows) {
            String no = in.batchNo == null ? "" : in.batchNo.trim();
            if (no.isEmpty()) {
                return ImpImportResult.fileError("存在没有采样批次号的行，一份文件必须同属一个批次");
            }
            if (batchNo == null) {
                batchNo = no;
            } else if (!batchNo.equals(no)) {
                return ImpImportResult.fileError("一份文件出现多个采样批次号（「" + batchNo + "」与「" + no
                        + "」），请拆成多份分别导入");
            }
        }
        result.setBatchNo(batchNo);
        result.setTotal(rows.size());

        TZwImpBatch batch = selectBatchByNo(batchNo);
        if (batch != null && (isVerified(batch) || int1(batch.getArchived()))) {
            // 已核实/已归档：整份拒绝，已核实的几条绝不翻出来重复核
            return ImpImportResult.fileError("批次「" + batchNo + "」已核实"
                    + (int1(batch.getArchived()) ? "并归档（只读）" : "") + "，不可重新导入");
        }

        String hash = contentHash(rows);
        if (batch != null && hash.equals(batch.getContentHash())) {
            // 同内容重导：幂等放行，不新增一行、不动任何既有结论
            result.setIdempotent(true);
            result.setBlocked(int1(batch.getBlocked()));
            result.setImported(countLedger(batchNo));
            return result;
        }

        // 未核实批次、内容有改动：旧版行整版置删留痕（差异/日志仍翻得到旧账），以新版重跑
        if (batch != null) {
            this.zwImpRowMapper.update(null, new UpdateWrapper<TZwImpRow>()
                    .set("del_flag", 1)
                    .eq("batch_no", batchNo)
                    .eq("del_flag", 0));
        } else {
            batch = new TZwImpBatch();
            batch.setBatchNo(batchNo);
            batch.setDelFlag(0);
        }

        ImpRowInspector.SiteDirectory sites = new ImpRowInspector.SiteDirectory() {
            @Override
            public boolean exists(String siteNo) {
                // 含已退役点：退役是业务状态，编号仍对得上底档；已删除底档才算查无此号
                Integer cnt = zwSourceMapper.selectCount(new QueryWrapper<TZwSource>()
                        .eq("site_no", siteNo).eq("del_flag", 0));
                return cnt != null && cnt.intValue() > 0;
            }
        };
        ImpRowInspector.RuleLookup rules = new ImpRowInspector.RuleLookup() {
            @Override
            public WaterVerdict evaluate(java.math.BigDecimal qty, Date testAt, String sampleType) {
                // 与手工逐条录入同一个唯一评价出口，导入不许旁路另算
                return zwLimRuleService.evaluate(qty, testAt, sampleType);
            }
        };

        List<String> hardErrors = new ArrayList<String>();
        List<TZwImpRow> entities = new ArrayList<TZwImpRow>();
        for (int i = 0; i < rows.size(); i++) {
            ImpRowInspector.Input in = rows.get(i);
            int rowNo = i + 1;
            ImpRowInspector.Check check = ImpRowInspector.inspect(in, sites, rules);
            TZwImpRow r = new TZwImpRow();
            r.setBatchNo(batchNo);
            r.setRowNo(Integer.valueOf(rowNo));
            r.setSiteNo(trim(in.siteNo));
            r.setItemCode(trim(in.itemCode));
            r.setSampleType(trim(in.sampleType));
            r.setDeclaredRuleCode(trim(in.ruleCode));
            r.setRawLine(in.rawLine);
            r.setDelFlag(0);
            r.setCheckStatus(CHECK_PENDING);
            if (check.hardError) {
                // 解析掉的字段能留则留（时间/读数解析失败自然为空），供下钻对照
                r.setQty(check.qty);
                r.setTestAt(check.testAt);
                r.setCheckStatus(CHECK_HARD);
                r.setCheckMsg(check.message);
                r.setStatus(ROW_NOT_LEDGER);
                hardErrors.add("第" + rowNo + "行：" + check.message + "（原始行：" + in.rawLine + "）");
            } else {
                r.setQty(check.qty);
                r.setTestAt(check.testAt);
                r.setValidFlag(Integer.valueOf(check.validFlag));
                r.setReviewedFlag(Integer.valueOf(check.reviewedFlag));
                r.setCheckStatus(CHECK_OK);
                r.setCheckMsg("格式合格");
                // 只有全格合格才准碰正式账；定级三件套以唯一出口回值钉死
                r.setStatus(ROW_LEDGER);
                if (check.verdict.getKind() == WaterVerdict.Kind.GRADED) {
                    r.setGradeLevel(Integer.valueOf(check.verdict.getGrade()));
                    r.setRuleCode(check.verdict.getRuleCode());
                    r.setRuleEffDate(check.verdict.getRuleEffDate());
                }
            }
            entities.add(r);
        }

        boolean blocked = !hardErrors.isEmpty();
        if (blocked) {
            // 整批拦下：即便本行格式合格，也不许进正式账——status 一律压成 0（check_status 保留本行自己的判定）。
            // 报表/导出只认 status=1，被拦批的任何一行都混不进去。
            for (TZwImpRow r : entities) {
                r.setStatus(ROW_NOT_LEDGER);
            }
        }
        for (TZwImpRow r : entities) {
            this.zwImpRowMapper.insert(r);
        }

        // 批次头随本批定论落账（重导即刷新拦截结论；核实字段一根手指都不碰）。
        // 更新走显式 UpdateWrapper：先拦后改合格重导时 block_msg 必须能清成 NULL（updateById 会跳过 null）。
        String blockMsg = blocked ? joinMessages(hardErrors) : null;
        UpdateWrapper<TZwImpBatch> headSet = new UpdateWrapper<TZwImpBatch>()
                .set("file_name", fileName)
                .set("total_rows", rows.size())
                .set("blocked", blocked ? 1 : 0)
                .set("block_msg", blockMsg)
                .set("content_hash", hash)
                .eq("del_flag", 0);
        if (batch.getId() == null) {
            batch.setFileName(fileName);
            batch.setTotalRows(Integer.valueOf(rows.size()));
            batch.setBlocked(blocked ? 1 : 0);
            batch.setBlockMsg(blockMsg);
            batch.setContentHash(hash);
            this.zwImpBatchMapper.insert(batch);
        } else {
            headSet.eq("id", batch.getId());
            this.zwImpBatchMapper.update(null, headSet);
        }

        result.setBlocked(blocked);
        result.setHardErrors(hardErrors);
        result.setImported(blocked ? 0 : entities.size());

        // 异常判定自动登记与导入同事务：整批拦在库外（无正式账行）则一笔不登记
        this.zwAnomalyService.syncOnImport(batchNo);
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean confirmAnomaly(Long rowId, String operator) {
        TZwImpRow r = this.zwImpRowMapper.selectById(rowId);
        if (r == null || int1(r.getDelFlag())) {
            return false;
        }
        if (int1(r.getArchived()) || r.getVerifyResult() != null) {
            // 归档原样锁死；批次只要核实过（成或不成）即只读，确认口也不开
            return false;
        }
        if (r.getCheckStatus() == null || r.getCheckStatus().intValue() != CHECK_HARD) {
            // 只确认异常行，合格行不需要这一道
            return false;
        }
        if (r.getConfirmBy() != null && !r.getConfirmBy().isEmpty()) {
            // 已确认幂等：不覆盖确认人与时刻
            return true;
        }
        int rows = this.zwImpRowMapper.update(null, new UpdateWrapper<TZwImpRow>()
                .set("confirm_by", operator)
                .set("confirm_time", new Date())
                .eq("id", rowId)
                .eq("check_status", CHECK_HARD)
                .eq("archived", 0)
                .isNull("verify_result")
                .isNull("confirm_by")
                .eq("del_flag", 0));
        return rows > 0;
    }

    @Override
    public TZwImpBatch verifyBatch(String batchNo, int result, String reason, String operator) {
        return verifyBatch(batchNo, result, reason, operator, null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TZwImpBatch verifyBatch(String batchNo, int result, String reason, String operator,
                                   List<String> rejectMsg) {
        TZwImpBatch batch = selectBatchByNo(batchNo);
        if (batch == null) {
            return reject(rejectMsg, "批次「" + batchNo + "」不存在");
        }
        if (isVerified(batch) || int1(batch.getArchived())) {
            // 一批只准核实一次：核实过即只读，第二请求当场拒绝
            return reject(rejectMsg, "批次「" + batchNo + "」已核实（"
                    + (batch.getVerifyResult() != null && batch.getVerifyResult().intValue() == VERIFY_PASS
                            ? "核实成并归档" : "核实不成") + "），不可再次核实");
        }
        if (result != VERIFY_PASS && result != VERIFY_FAIL) {
            return reject(rejectMsg, "核实结论只能是 核实成(1)/核实不成(2)");
        }
        String reasonTrimmed = trim(reason);
        if (result == VERIFY_FAIL && reasonTrimmed.isEmpty()) {
            // 核实不成必须写明原因
            return reject(rejectMsg, "核实不成必须写明原因");
        }

        List<TZwImpRow> rows = listBatchRows(batchNo);
        if (rows.isEmpty()) {
            return reject(rejectMsg, "批次「" + batchNo + "」没有可核实的明细行");
        }
        // 异常行（整批拦截的批全靠这道）必须先逐条确认，才许进入批次核实
        List<String> unconfirmed = new ArrayList<String>();
        for (TZwImpRow r : rows) {
            if (r.getCheckStatus() != null && r.getCheckStatus().intValue() == CHECK_HARD
                    && (r.getConfirmBy() == null || r.getConfirmBy().isEmpty())) {
                unconfirmed.add("第" + r.getRowNo() + "行");
            }
        }
        if (!unconfirmed.isEmpty()) {
            return reject(rejectMsg, "以下异常行尚未确认，不能核实：" + joinMessages(unconfirmed));
        }

        Date now = new Date();
        boolean pass = result == VERIFY_PASS;

        // 行级先落（条件带 archived=0/verify_result IS NULL：并发下后到者 0 行）
        for (TZwImpRow r : rows) {
            UpdateWrapper<TZwImpRow> uw = new UpdateWrapper<TZwImpRow>()
                    .set("verify_result", Integer.valueOf(result))
                    .set("verify_reason", pass ? null : reasonTrimmed)
                    .set("verify_by", operator)
                    .set("verify_time", now)
                    .eq("id", r.getId())
                    .eq("archived", 0)
                    .isNull("verify_result")
                    .eq("del_flag", 0);
            if (pass) {
                // 核实成：逐行归档，原样锁住（原始行/定级字段一根不动，只打归档印）
                uw.set("archived", 1).set("archive_time", now);
            }
            int n = this.zwImpRowMapper.update(null, uw);
            if (n == 0) {
                // 竞态：已有一次核实抢先——整批拒，事务回滚
                return reject(rejectMsg, "批次「" + batchNo + "」已被另一次核实抢先落定，请刷新后查看");
            }
        }

        // 头级定论：条件更新保证「一批一次」，0 行即重复核实，整笔回滚
        UpdateWrapper<TZwImpBatch> bw = new UpdateWrapper<TZwImpBatch>()
                .set("verify_result", Integer.valueOf(result))
                .set("verify_reason", pass ? (reasonTrimmed.isEmpty() ? null : reasonTrimmed) : reasonTrimmed)
                .set("verify_by", operator)
                .set("verify_time", now)
                .eq("id", batch.getId())
                .eq("archived", 0)
                .isNull("verify_result")
                .eq("del_flag", 0);
        if (pass) {
            bw.set("archived", 1).set("archive_time", now);
        }
        int n = this.zwImpBatchMapper.update(null, bw);
        if (n == 0) {
            return reject(rejectMsg, "批次「" + batchNo + "」已被另一次核实抢先落定，请刷新后查看");
        }
        return this.zwImpBatchMapper.selectById(batch.getId());
    }

    @Override
    public List<TZwImpRow> reportRows(String siteNo, Integer validFlag, Integer reviewedFlag) {
        QueryWrapper<TZwImpRow> qw = new QueryWrapper<TZwImpRow>()
                .eq("status", ROW_LEDGER)
                .eq("del_flag", 0);
        if (siteNo != null && !siteNo.trim().isEmpty()) {
            qw.eq("site_no", siteNo.trim());
        }
        if (validFlag != null) {
            qw.eq("valid_flag", validFlag);
        }
        if (reviewedFlag != null) {
            qw.eq("reviewed_flag", reviewedFlag);
        }
        qw.orderByAsc("batch_no", "row_no");
        return this.zwImpRowMapper.selectList(qw);
    }

    @Override
    public List<BatchDiffer.BatchDiff> periodDiff(List<String> currentBatchNos, List<String> previousBatchNos) {
        return BatchDiffer.diff(loadLines(currentBatchNos), loadLines(previousBatchNos));
    }

    /** 差异取数只取这些批次当前版本行的原始行（异常行也是明细的一部分，一并入比） */
    private List<BatchDiffer.Line> loadLines(List<String> batchNos) {
        List<BatchDiffer.Line> lines = new ArrayList<BatchDiffer.Line>();
        if (batchNos == null || batchNos.isEmpty()) {
            return lines;
        }
        List<TZwImpRow> rows = this.zwImpRowMapper.selectList(new QueryWrapper<TZwImpRow>()
                .in("batch_no", batchNos)
                .eq("del_flag", 0)
                .orderByAsc("batch_no", "row_no"));
        for (TZwImpRow r : rows) {
            lines.add(new BatchDiffer.Line(r.getBatchNo(),
                    r.getRowNo() == null ? 0 : r.getRowNo().intValue(), r.getRawLine()));
        }
        return lines;
    }

    private int countLedger(String batchNo) {
        Integer c = this.zwImpRowMapper.selectCount(new QueryWrapper<TZwImpRow>()
                .eq("batch_no", batchNo)
                .eq("status", ROW_LEDGER)
                .eq("del_flag", 0));
        return c == null ? 0 : c.intValue();
    }

    private static boolean isVerified(TZwImpBatch b) {
        return b.getVerifyResult() != null;
    }

    private static boolean int1(Integer v) {
        return v != null && v.intValue() == 1;
    }

    private static String trim(String s) {
        return s == null ? "" : s.trim();
    }

    private static TZwImpBatch reject(List<String> rejectMsg, String msg) {
        if (rejectMsg != null) {
            rejectMsg.clear();
            rejectMsg.add(msg);
        }
        return null;
    }

    private static String joinMessages(List<String> msgs) {
        StringBuilder sb = new StringBuilder();
        for (String m : msgs) {
            if (sb.length() > 0) {
                sb.append("；");
            }
            sb.append(m);
            if (sb.length() > 480) {
                sb.append("……");
                break;
            }
        }
        return sb.length() > 500 ? sb.substring(0, 500) : sb.toString();
    }

    /** 本批原始行内容指印：逐行原文按行序喂 SHA-256，同内容重导逐字相同 */
    private static String contentHash(List<ImpRowInspector.Input> rows) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            for (ImpRowInspector.Input in : rows) {
                String s = in.rawLine == null ? "" : in.rawLine;
                md.update(s.getBytes("UTF-8"));
                md.update((byte) '\n');
            }
            byte[] digest = md.digest();
            StringBuilder hex = new StringBuilder();
            for (byte b : digest) {
                hex.append(String.format("%02x", b & 0xff));
            }
            return hex.toString();
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }
}
