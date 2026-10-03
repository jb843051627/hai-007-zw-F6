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
import java.math.BigDecimal;
import java.util.Date;

/**
 * 检测数据导入核对行对象 t_zw_imp_row
 *
 * @author fuce
 * @date 2026-09-12
 */
@TableName("t_zw_imp_row")
@ApiModel(value = "TZwImpRow", description = "检测数据导入核对行")
public class TZwImpRow implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 主键 */
    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    @ApiModelProperty(value = "主键")
    private Long id;

    /** 检测数据上传批次号 */
    @TableField("batch_no")
    @ApiModelProperty(value = "检测数据上传批次号")
    private String batchNo;

    /** 原表行次 */
    @TableField("row_no")
    @ApiModelProperty(value = "原表行次")
    private Integer rowNo;

    /** 采样点位代号（随样本入账，水质核实只认代号） */
    @TableField("site_no")
    @ApiModelProperty(value = "采样点位代号")
    private String siteNo;

    /** 被校验出的数据项代号 */
    @TableField("item_code")
    @ApiModelProperty(value = "被校验出的数据项代号")
    private String itemCode;

    /** 本行检测读数 */
    @TableField("qty")
    @ApiModelProperty(value = "本行检测读数")
    private BigDecimal qty;

    /** 样本类别(水源水/出厂水/管网末梢) */
    @TableField("sample_type")
    @ApiModelProperty(value = "样本类别(水源水/出厂水/管网末梢)")
    private String sampleType;

    /** 检测发生那天——用哪条线只看这一天，不看入库的今天 */
    @TableField("test_at")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "检测发生那天")
    private Date testAt;

    /** 评价等级 1合格 2关注 3超标 4严重超标；空=未定级（无可用标准） */
    @TableField("grade_level")
    @ApiModelProperty(value = "评价等级 1合格 2关注 3超标 4严重超标")
    private Integer gradeLevel;

    /** 定级所依标准线代号（服务层回值钉死，写过不再改） */
    @TableField("rule_code")
    @ApiModelProperty(value = "定级所依标准线代号")
    private String ruleCode;

    /** 所依标准的生效那天（随定级一并钉死，历史复核只认这个编号上下文） */
    @TableField("rule_eff_date")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "所依标准的生效那天")
    private Date ruleEffDate;

    /** Excel 里申报的规则代号（库里钉死的线代号与之对不上即整批拦截） */
    @TableField("declared_rule_code")
    @ApiModelProperty(value = "申报规则代号")
    private String declaredRuleCode;

    /** 原始行原文（按原列序逐格拼接，下钻与归档都只看这条原样） */
    @TableField("raw_line")
    @ApiModelProperty(value = "原始行原文")
    private String rawLine;

    /** 是否有效（Excel 随单申报）1有效 0无效 */
    @TableField("valid_flag")
    @ApiModelProperty(value = "是否有效 1有效 0无效")
    private Integer validFlag;

    /** 是否已复核（Excel 随单申报）1已复核 0未复核 */
    @TableField("reviewed_flag")
    @ApiModelProperty(value = "是否已复核 1已复核 0未复核")
    private Integer reviewedFlag;

    /** 校验结论 0待校 1格式合格 2异常 */
    @TableField("check_status")
    @ApiModelProperty(value = "校验结论 0待校 1格式合格 2异常")
    private Integer checkStatus;

    /** 校验说明（每条都说清楚能不能过、为什么） */
    @TableField("check_msg")
    @ApiModelProperty(value = "校验说明")
    private String checkMsg;

    /** 异常确认人 */
    @TableField("confirm_by")
    @ApiModelProperty(value = "异常确认人")
    private String confirmBy;

    /** 异常确认时刻 */
    @TableField("confirm_time")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "异常确认时刻")
    private Date confirmTime;

    /** 核实结论 1核实成 2核实不成（随批次那一次回写） */
    @TableField("verify_result")
    @ApiModelProperty(value = "核实结论 1核实成 2核实不成")
    private Integer verifyResult;

    /** 核实不成原因（随批次回写） */
    @TableField("verify_reason")
    @ApiModelProperty(value = "核实不成原因")
    private String verifyReason;

    /** 核实人（随批次回写，以那次为准） */
    @TableField("verify_by")
    @ApiModelProperty(value = "核实人")
    private String verifyBy;

    /** 核实时间（随批次回写） */
    @TableField("verify_time")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "核实时间")
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

    /** 行落地情形 0未入正式账(整批拦在库外) 1已入库 */
    @TableField("status")
    @ApiModelProperty(value = "行落地情形 0未入正式账 1已入库")
    private Integer status;

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

    public Integer getRowNo() {
        return rowNo;
    }

    public void setRowNo(Integer rowNo) {
        this.rowNo = rowNo;
    }

    public String getSiteNo() {
        return siteNo;
    }

    public void setSiteNo(String siteNo) {
        this.siteNo = siteNo;
    }

    public String getItemCode() {
        return itemCode;
    }

    public void setItemCode(String itemCode) {
        this.itemCode = itemCode;
    }

    public BigDecimal getQty() {
        return qty;
    }

    public void setQty(BigDecimal qty) {
        this.qty = qty;
    }

    public String getSampleType() {
        return sampleType;
    }

    public void setSampleType(String sampleType) {
        this.sampleType = sampleType;
    }

    public Date getTestAt() {
        return testAt;
    }

    public void setTestAt(Date testAt) {
        this.testAt = testAt;
    }

    public Integer getGradeLevel() {
        return gradeLevel;
    }

    public void setGradeLevel(Integer gradeLevel) {
        this.gradeLevel = gradeLevel;
    }

    public String getRuleCode() {
        return ruleCode;
    }

    public void setRuleCode(String ruleCode) {
        this.ruleCode = ruleCode;
    }

    public Date getRuleEffDate() {
        return ruleEffDate;
    }

    public void setRuleEffDate(Date ruleEffDate) {
        this.ruleEffDate = ruleEffDate;
    }

    public String getDeclaredRuleCode() {
        return declaredRuleCode;
    }

    public void setDeclaredRuleCode(String declaredRuleCode) {
        this.declaredRuleCode = declaredRuleCode;
    }

    public String getRawLine() {
        return rawLine;
    }

    public void setRawLine(String rawLine) {
        this.rawLine = rawLine;
    }

    public Integer getValidFlag() {
        return validFlag;
    }

    public void setValidFlag(Integer validFlag) {
        this.validFlag = validFlag;
    }

    public Integer getReviewedFlag() {
        return reviewedFlag;
    }

    public void setReviewedFlag(Integer reviewedFlag) {
        this.reviewedFlag = reviewedFlag;
    }

    public Integer getCheckStatus() {
        return checkStatus;
    }

    public void setCheckStatus(Integer checkStatus) {
        this.checkStatus = checkStatus;
    }

    public String getCheckMsg() {
        return checkMsg;
    }

    public void setCheckMsg(String checkMsg) {
        this.checkMsg = checkMsg;
    }

    public String getConfirmBy() {
        return confirmBy;
    }

    public void setConfirmBy(String confirmBy) {
        this.confirmBy = confirmBy;
    }

    public Date getConfirmTime() {
        return confirmTime;
    }

    public void setConfirmTime(Date confirmTime) {
        this.confirmTime = confirmTime;
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

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
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
