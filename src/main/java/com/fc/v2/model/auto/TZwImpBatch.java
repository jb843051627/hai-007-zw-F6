package com.fc.v2.model.auto;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

import java.io.Serializable;
import java.util.Date;

/**
 * 检测数据导入批次头对象 t_zw_imp_batch
 *
 * <p>一批一次定论：整批拦截/放行挂在 {@code blocked}，核实只走一次，
 * 核实成则 {@code archived=1} 整批归档只读，核实不成写明原因。
 *
 * @author fuce
 * @date 2026-10-02
 */
@TableName("t_zw_imp_batch")
@ApiModel(value = "TZwImpBatch", description = "检测数据导入批次头")
public class TZwImpBatch implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 主键 */
    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    @ApiModelProperty(value = "主键")
    private Long id;

    /** 检测数据上传批次号（全表唯一） */
    @TableField("batch_no")
    @ApiModelProperty(value = "检测数据上传批次号")
    private String batchNo;

    /** 原始 Excel 文件名 */
    @TableField("file_name")
    @ApiModelProperty(value = "原始 Excel 文件名")
    private String fileName;

    /** 本批数据行数 */
    @TableField("total_rows")
    @ApiModelProperty(value = "本批数据行数")
    private Integer totalRows;

    /** 整批拦截 0全格放行已入库 1存在硬错整批拦在库外 */
    @TableField("blocked")
    @ApiModelProperty(value = "整批拦截 0放行 1拦截")
    private Integer blocked;

    /** 拦截说明（点名第几行什么错） */
    @TableField("block_msg")
    @ApiModelProperty(value = "拦截说明")
    private String blockMsg;

    /** 本批原始行内容指印（同内容重导幂等放行） */
    @TableField("content_hash")
    @ApiModelProperty(value = "本批原始行内容指印")
    private String contentHash;

    /** 核实结论 1核实成 2核实不成；空=尚未核实 */
    @TableField("verify_result")
    @ApiModelProperty(value = "核实结论 1核实成 2核实不成")
    private Integer verifyResult;

    /** 核实不成原因（核实成时记归档说明，可空） */
    @TableField("verify_reason")
    @ApiModelProperty(value = "核实不成原因")
    private String verifyReason;

    /** 核实人（以核实那一次写下的为准） */
    @TableField("verify_by")
    @ApiModelProperty(value = "核实人")
    private String verifyBy;

    /** 核实时刻（系统日志同刻可翻到是哪一次写的） */
    @TableField("verify_time")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "核实时刻")
    private Date verifyTime;

    /** 归档标识 0未归档 1已归档只读 */
    @TableField("archived")
    @ApiModelProperty(value = "归档标识 0未归档 1已归档只读")
    private Integer archived;

    /** 归档时刻 */
    @TableField("archive_time")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "归档时刻")
    private Date archiveTime;

    /** 删除标记 0正常 1删除 */
    @TableField("del_flag")
    @ApiModelProperty(value = "删除标记 0正常 1删除")
    private Integer delFlag;

    /** 创建者 */
    @TableField(value = "create_by", fill = FieldFill.INSERT)
    @ApiModelProperty(value = "创建者")
    private String createBy;

    /** 创建时间 */
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "创建时间")
    private Date createTime;

    /** 更新者 */
    @TableField(value = "update_by", fill = FieldFill.UPDATE)
    @ApiModelProperty(value = "更新者")
    private String updateBy;

    /** 更新时间 */
    @TableField(value = "update_time", fill = FieldFill.UPDATE)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "更新时间")
    private Date updateTime;

    /** 备注 */
    @TableField("remark")
    @ApiModelProperty(value = "备注")
    private String remark;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getBatchNo() {
        return batchNo;
    }

    public void setBatchNo(String batchNo) {
        this.batchNo = batchNo;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public Integer getTotalRows() {
        return totalRows;
    }

    public void setTotalRows(Integer totalRows) {
        this.totalRows = totalRows;
    }

    public Integer getBlocked() {
        return blocked;
    }

    public void setBlocked(Integer blocked) {
        this.blocked = blocked;
    }

    public String getBlockMsg() {
        return blockMsg;
    }

    public void setBlockMsg(String blockMsg) {
        this.blockMsg = blockMsg;
    }

    public String getContentHash() {
        return contentHash;
    }

    public void setContentHash(String contentHash) {
        this.contentHash = contentHash;
    }

    public Integer getVerifyResult() {
        return verifyResult;
    }

    public void setVerifyResult(Integer verifyResult) {
        this.verifyResult = verifyResult;
    }

    public String getVerifyReason() {
        return verifyReason;
    }

    public void setVerifyReason(String verifyReason) {
        this.verifyReason = verifyReason;
    }

    public String getVerifyBy() {
        return verifyBy;
    }

    public void setVerifyBy(String verifyBy) {
        this.verifyBy = verifyBy;
    }

    public Date getVerifyTime() {
        return verifyTime;
    }

    public void setVerifyTime(Date verifyTime) {
        this.verifyTime = verifyTime;
    }

    public Integer getArchived() {
        return archived;
    }

    public void setArchived(Integer archived) {
        this.archived = archived;
    }

    public Date getArchiveTime() {
        return archiveTime;
    }

    public void setArchiveTime(Date archiveTime) {
        this.archiveTime = archiveTime;
    }

    public Integer getDelFlag() {
        return delFlag;
    }

    public void setDelFlag(Integer delFlag) {
        this.delFlag = delFlag;
    }

    public String getCreateBy() {
        return createBy;
    }

    public void setCreateBy(String createBy) {
        this.createBy = createBy;
    }

    public Date getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Date createTime) {
        this.createTime = createTime;
    }

    public String getUpdateBy() {
        return updateBy;
    }

    public void setUpdateBy(String updateBy) {
        this.updateBy = updateBy;
    }

    public Date getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(Date updateTime) {
        this.updateTime = updateTime;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }
}
