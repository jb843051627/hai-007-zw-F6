package com.fc.v2.service.zw;

import java.util.ArrayList;
import java.util.List;

/**
 * 整份文件导入的回值——三种结局必须分得清，页面不许含糊一句「导入完成」：
 * <ul>
 *   <li>{@code fileError != null}：文件级错误（空/坏文件、多批次、超上限、批次已核实锁定），
 *       一笔不写；</li>
 *   <li>{@code blocked=true}：逐行校验出硬错，整批拦在库外，{@code hardErrors} 逐行点名，
 *       正式账写入行数 {@code imported}=0（异常行已全部录进明细台账待处理）；</li>
 *   <li>{@code blocked=false}：全格合格，{@code imported} 行已入正式账，定级三件套钉死。</li>
 * </ul>
 * 同内容重导幂等时 {@code idempotent=true}，不新增任何行。
 *
 * @author fuce
 * @date 2026-10-02
 */
public class ImpImportResult {

    /** 文件级/批次级错误说明；非空表示一笔不写 */
    private String fileError;
    private String batchNo;
    private int total;
    private boolean blocked;
    private boolean idempotent;
    private int imported;
    /** 硬错行点名：元素形如「第3行：采样时间……」 */
    private List<String> hardErrors = new ArrayList<String>();

    public static ImpImportResult fileError(String msg) {
        ImpImportResult r = new ImpImportResult();
        r.fileError = msg;
        return r;
    }

    public String getFileError() {
        return fileError;
    }

    public void setFileError(String fileError) {
        this.fileError = fileError;
    }

    public String getBatchNo() {
        return batchNo;
    }

    public void setBatchNo(String batchNo) {
        this.batchNo = batchNo;
    }

    public int getTotal() {
        return total;
    }

    public void setTotal(int total) {
        this.total = total;
    }

    public boolean isBlocked() {
        return blocked;
    }

    public void setBlocked(boolean blocked) {
        this.blocked = blocked;
    }

    public boolean isIdempotent() {
        return idempotent;
    }

    public void setIdempotent(boolean idempotent) {
        this.idempotent = idempotent;
    }

    public int getImported() {
        return imported;
    }

    public void setImported(int imported) {
        this.imported = imported;
    }

    public List<String> getHardErrors() {
        return hardErrors;
    }

    public void setHardErrors(List<String> hardErrors) {
        this.hardErrors = hardErrors;
    }
}
