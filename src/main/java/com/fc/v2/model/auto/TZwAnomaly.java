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
 * 水质异常判定登记对象 t_zw_anomaly。
 *
 * <p>异常一被判出来即在检测行落账的同一事务自动带出一条：点位、检测日、指标、所依标准线、
 * 当天三档界值、检测值、超了多少、关注还是超标、要不要停水/复测，全钉成快照。
 * 更正只改本条；已上报/已反映/已结案归档的，服务层任何写口 0 行被拒。
 *
 * @author fuce
 * @date 2026-10-03
 */
@TableName("t_zw_anomaly")
@ApiModel(value = "TZwAnomaly", description = "水质异常判定登记")
public class TZwAnomaly implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    @ApiModelProperty(value = "主键")
    private Long id;

    @TableField("anom_no")
    @ApiModelProperty(value = "异常登记号")
    private String anomNo;

    @TableField("imp_row_id")
    @JsonSerialize(using = ToStringSerializer.class)
    @ApiModelProperty(value = "原始检测记录行")
    private Long impRowId;

    @TableField("batch_no")
    @ApiModelProperty(value = "检测批次号")
    private String batchNo;

    @TableField("site_no")
    @ApiModelProperty(value = "点位代号")
    private String siteNo;

    @TableField("item_code")
    @ApiModelProperty(value = "指标代号")
    private String itemCode;

    @TableField("sample_type")
    @ApiModelProperty(value = "样本类别")
    private String sampleType;

    @TableField("test_day")
    @JsonFormat(pattern = "yyyy-MM-dd", timezone = "GMT+8")
    @ApiModelProperty(value = "检测归属日")
    private Date testDay;

    @TableField("test_at")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "检测发生时刻")
    private Date testAt;

    @TableField("qty")
    @ApiModelProperty(value = "当天检测值")
    private BigDecimal qty;

    @TableField("grade_level")
    @ApiModelProperty(value = "判定档位 2关注 3超标 4严重超标")
    private Integer gradeLevel;

    @TableField("rule_code")
    @ApiModelProperty(value = "所依标准线代号")
    private String ruleCode;

    @TableField("rule_eff_date")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "标准线生效日")
    private Date ruleEffDate;

    @TableField("rule_name")
    @ApiModelProperty(value = "标准线名快照")
    private String ruleName;

    @TableField("th1_max")
    @ApiModelProperty(value = "关注档上限快照")
    private BigDecimal th1Max;

    @TableField("th2_max")
    @ApiModelProperty(value = "超标档上限快照")
    private BigDecimal th2Max;

    @TableField("th3_max")
    @ApiModelProperty(value = "严重超标档上限快照")
    private BigDecimal th3Max;

    @TableField("over_amt")
    @ApiModelProperty(value = "超了多少")
    private BigDecimal overAmt;

    @TableField("base_th")
    @ApiModelProperty(value = "所越界值")
    private BigDecimal baseTh;

    @TableField("need_retest")
    @ApiModelProperty(value = "要不要复测 1要 0不要")
    private Integer needRetest;

    @TableField("need_stop")
    @ApiModelProperty(value = "要不要停水 1要 0不要")
    private Integer needStop;

    @TableField("judge_text")
    @ApiModelProperty(value = "判定完整过程文本")
    private String judgeText;

    @TableField("report_id")
    @JsonSerialize(using = ToStringSerializer.class)
    @ApiModelProperty(value = "已挂上报单主键")
    private Long reportId;

    @TableField("reported")
    @ApiModelProperty(value = "是否已上报")
    private Integer reported;

    @TableField("reflected")
    @ApiModelProperty(value = "是否已反映供水户")
    private Integer reflected;

    @TableField("closed")
    @ApiModelProperty(value = "是否已结案")
    private Integer closed;

    @TableField("archived")
    @ApiModelProperty(value = "归档标识")
    private Integer archived;

    @TableField("archive_time")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "归档时刻")
    private Date archiveTime;

    @TableField("correct_times")
    @ApiModelProperty(value = "更正轮数")
    private Integer correctTimes;

    @TableField("last_correct_by")
    @ApiModelProperty(value = "最近更正人")
    private String lastCorrectBy;

    @TableField("last_correct_time")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "最近更正时刻")
    private Date lastCorrectTime;

    @TableField("del_flag")
    @ApiModelProperty(value = "删除标记")
    private Integer delFlag;

    @TableField(value = "create_by", fill = FieldFill.INSERT)
    private String createBy;
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date createTime;
    @TableField(value = "update_by", fill = FieldFill.UPDATE)
    private String updateBy;
    @TableField(value = "update_time", fill = FieldFill.UPDATE)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date updateTime;
    @TableField("remark")
    private String remark;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getAnomNo() { return anomNo; }
    public void setAnomNo(String anomNo) { this.anomNo = anomNo; }
    public Long getImpRowId() { return impRowId; }
    public void setImpRowId(Long impRowId) { this.impRowId = impRowId; }
    public String getBatchNo() { return batchNo; }
    public void setBatchNo(String batchNo) { this.batchNo = batchNo; }
    public String getSiteNo() { return siteNo; }
    public void setSiteNo(String siteNo) { this.siteNo = siteNo; }
    public String getItemCode() { return itemCode; }
    public void setItemCode(String itemCode) { this.itemCode = itemCode; }
    public String getSampleType() { return sampleType; }
    public void setSampleType(String sampleType) { this.sampleType = sampleType; }
    public Date getTestDay() { return testDay; }
    public void setTestDay(Date testDay) { this.testDay = testDay; }
    public Date getTestAt() { return testAt; }
    public void setTestAt(Date testAt) { this.testAt = testAt; }
    public BigDecimal getQty() { return qty; }
    public void setQty(BigDecimal qty) { this.qty = qty; }
    public Integer getGradeLevel() { return gradeLevel; }
    public void setGradeLevel(Integer gradeLevel) { this.gradeLevel = gradeLevel; }
    public String getRuleCode() { return ruleCode; }
    public void setRuleCode(String ruleCode) { this.ruleCode = ruleCode; }
    public Date getRuleEffDate() { return ruleEffDate; }
    public void setRuleEffDate(Date ruleEffDate) { this.ruleEffDate = ruleEffDate; }
    public String getRuleName() { return ruleName; }
    public void setRuleName(String ruleName) { this.ruleName = ruleName; }
    public BigDecimal getTh1Max() { return th1Max; }
    public void setTh1Max(BigDecimal th1Max) { this.th1Max = th1Max; }
    public BigDecimal getTh2Max() { return th2Max; }
    public void setTh2Max(BigDecimal th2Max) { this.th2Max = th2Max; }
    public BigDecimal getTh3Max() { return th3Max; }
    public void setTh3Max(BigDecimal th3Max) { this.th3Max = th3Max; }
    public BigDecimal getOverAmt() { return overAmt; }
    public void setOverAmt(BigDecimal overAmt) { this.overAmt = overAmt; }
    public BigDecimal getBaseTh() { return baseTh; }
    public void setBaseTh(BigDecimal baseTh) { this.baseTh = baseTh; }
    public Integer getNeedRetest() { return needRetest; }
    public void setNeedRetest(Integer needRetest) { this.needRetest = needRetest; }
    public Integer getNeedStop() { return needStop; }
    public void setNeedStop(Integer needStop) { this.needStop = needStop; }
    public String getJudgeText() { return judgeText; }
    public void setJudgeText(String judgeText) { this.judgeText = judgeText; }
    public Long getReportId() { return reportId; }
    public void setReportId(Long reportId) { this.reportId = reportId; }
    public Integer getReported() { return reported; }
    public void setReported(Integer reported) { this.reported = reported; }
    public Integer getReflected() { return reflected; }
    public void setReflected(Integer reflected) { this.reflected = reflected; }
    public Integer getClosed() { return closed; }
    public void setClosed(Integer closed) { this.closed = closed; }
    public Integer getArchived() { return archived; }
    public void setArchived(Integer archived) { this.archived = archived; }
    public Date getArchiveTime() { return archiveTime; }
    public void setArchiveTime(Date archiveTime) { this.archiveTime = archiveTime; }
    public Integer getCorrectTimes() { return correctTimes; }
    public void setCorrectTimes(Integer correctTimes) { this.correctTimes = correctTimes; }
    public String getLastCorrectBy() { return lastCorrectBy; }
    public void setLastCorrectBy(String lastCorrectBy) { this.lastCorrectBy = lastCorrectBy; }
    public Date getLastCorrectTime() { return lastCorrectTime; }
    public void setLastCorrectTime(Date lastCorrectTime) { this.lastCorrectTime = lastCorrectTime; }
    public Integer getDelFlag() { return delFlag; }
    public void setDelFlag(Integer delFlag) { this.delFlag = delFlag; }
    public String getCreateBy() { return createBy; }
    public void setCreateBy(String createBy) { this.createBy = createBy; }
    public Date getCreateTime() { return createTime; }
    public void setCreateTime(Date createTime) { this.createTime = createTime; }
    public String getUpdateBy() { return updateBy; }
    public void setUpdateBy(String updateBy) { this.updateBy = updateBy; }
    public Date getUpdateTime() { return updateTime; }
    public void setUpdateTime(Date updateTime) { this.updateTime = updateTime; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}
