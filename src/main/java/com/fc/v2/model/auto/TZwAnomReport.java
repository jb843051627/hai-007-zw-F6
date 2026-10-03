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
 * 水质异常上报单对象 t_zw_anom_report。
 *
 * <p>一笔异常只能挂一个上报单（uk_zw_report_anom），同点位同日同指标第二单整单不成立
 * （uk_zw_report_site_day_item）。报过之后只读——没有改、撤、改名的口子；
 * 处置步只由系统事件推进（反映/复测回程/结案），结案可退回上一步，退回与结案都只追加台账。
 *
 * @author fuce
 * @date 2026-10-03
 */
@TableName("t_zw_anom_report")
@ApiModel(value = "TZwAnomReport", description = "水质异常上报单")
public class TZwAnomReport implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @TableField("report_no")
    private String reportNo;

    @TableField("anom_id")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long anomId;

    @TableField("site_no")
    private String siteNo;

    @TableField("item_code")
    private String itemCode;

    @TableField("test_day")
    @JsonFormat(pattern = "yyyy-MM-dd", timezone = "GMT+8")
    private Date testDay;

    @TableField("bill_year")
    private Integer billYear;

    @TableField("year_seq")
    private Integer yearSeq;

    @TableField("grade_level")
    private Integer gradeLevel;

    @TableField("need_retest")
    private Integer needRetest;

    @TableField("need_stop")
    private Integer needStop;

    @TableField("step_no")
    private Integer stepNo;

    @TableField("account_no")
    private String accountNo;

    @TableField("account_name")
    private String accountName;

    @TableField("contact_id")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long contactId;

    @TableField("contact_name")
    private String contactName;

    @TableField("contact_phone")
    private String contactPhone;

    @TableField("reflect_by")
    private String reflectBy;

    @TableField("reflect_time")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date reflectTime;

    @TableField("retest_id")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long retestId;

    @TableField("close_opinion")
    private String closeOpinion;

    @TableField("closed_by")
    private String closedBy;

    @TableField("closed_time")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date closedTime;

    @TableField("status")
    @ApiModelProperty(value = "0在办 1已结案归档 2已退回")
    private Integer status;

    @TableField("del_flag")
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
    public String getReportNo() { return reportNo; }
    public void setReportNo(String reportNo) { this.reportNo = reportNo; }
    public Long getAnomId() { return anomId; }
    public void setAnomId(Long anomId) { this.anomId = anomId; }
    public String getSiteNo() { return siteNo; }
    public void setSiteNo(String siteNo) { this.siteNo = siteNo; }
    public String getItemCode() { return itemCode; }
    public void setItemCode(String itemCode) { this.itemCode = itemCode; }
    public Date getTestDay() { return testDay; }
    public void setTestDay(Date testDay) { this.testDay = testDay; }
    public Integer getBillYear() { return billYear; }
    public void setBillYear(Integer billYear) { this.billYear = billYear; }
    public Integer getYearSeq() { return yearSeq; }
    public void setYearSeq(Integer yearSeq) { this.yearSeq = yearSeq; }
    public Integer getGradeLevel() { return gradeLevel; }
    public void setGradeLevel(Integer gradeLevel) { this.gradeLevel = gradeLevel; }
    public Integer getNeedRetest() { return needRetest; }
    public void setNeedRetest(Integer needRetest) { this.needRetest = needRetest; }
    public Integer getNeedStop() { return needStop; }
    public void setNeedStop(Integer needStop) { this.needStop = needStop; }
    public Integer getStepNo() { return stepNo; }
    public void setStepNo(Integer stepNo) { this.stepNo = stepNo; }
    public String getAccountNo() { return accountNo; }
    public void setAccountNo(String accountNo) { this.accountNo = accountNo; }
    public String getAccountName() { return accountName; }
    public void setAccountName(String accountName) { this.accountName = accountName; }
    public Long getContactId() { return contactId; }
    public void setContactId(Long contactId) { this.contactId = contactId; }
    public String getContactName() { return contactName; }
    public void setContactName(String contactName) { this.contactName = contactName; }
    public String getContactPhone() { return contactPhone; }
    public void setContactPhone(String contactPhone) { this.contactPhone = contactPhone; }
    public String getReflectBy() { return reflectBy; }
    public void setReflectBy(String reflectBy) { this.reflectBy = reflectBy; }
    public Date getReflectTime() { return reflectTime; }
    public void setReflectTime(Date reflectTime) { this.reflectTime = reflectTime; }
    public Long getRetestId() { return retestId; }
    public void setRetestId(Long retestId) { this.retestId = retestId; }
    public String getCloseOpinion() { return closeOpinion; }
    public void setCloseOpinion(String closeOpinion) { this.closeOpinion = closeOpinion; }
    public String getClosedBy() { return closedBy; }
    public void setClosedBy(String closedBy) { this.closedBy = closedBy; }
    public Date getClosedTime() { return closedTime; }
    public void setClosedTime(Date closedTime) { this.closedTime = closedTime; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
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
