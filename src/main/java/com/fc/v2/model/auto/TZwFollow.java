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
 * 盯办流水对象 t_zw_follow。
 *
 * <p>只追加，且只有系统能开、能结：步次推进时开、经办登记盯办结果时结、跑批发现到期未盯
 * 自动结一条"漏盯"并原样开下一条。每条结果都带着上一次盯的时间；隔几个月、盯七八次
 * 都顺着 seq_no 一条条翻得出。任何后段写法都倒改不了前段（更新一律带 id+status=0 条件）。
 *
 * @author fuce
 * @date 2026-10-03
 */
@TableName("t_zw_follow")
@ApiModel(value = "TZwFollow", description = "盯办流水")
public class TZwFollow implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @TableField("report_id")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long reportId;

    @TableField("step_no")
    private Integer stepNo;

    @TableField("seq_no")
    private Integer seqNo;

    @TableField("owner_login")
    private String ownerLogin;

    @TableField("due_at")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date dueAt;

    @TableField("target_account_name")
    private String targetAccountName;

    @TableField("target_contact_name")
    private String targetContactName;

    @TableField("target_contact_phone")
    private String targetContactPhone;

    @TableField("prev_follow_at")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date prevFollowAt;

    @TableField("result")
    private String result;

    @TableField("status")
    @ApiModelProperty(value = "0待盯 1已盯 2联系不上 3漏盯")
    private Integer status;

    @TableField("finish_by")
    private String finishBy;

    @TableField("finish_time")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date finishTime;

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
    public Long getReportId() { return reportId; }
    public void setReportId(Long reportId) { this.reportId = reportId; }
    public Integer getStepNo() { return stepNo; }
    public void setStepNo(Integer stepNo) { this.stepNo = stepNo; }
    public Integer getSeqNo() { return seqNo; }
    public void setSeqNo(Integer seqNo) { this.seqNo = seqNo; }
    public String getOwnerLogin() { return ownerLogin; }
    public void setOwnerLogin(String ownerLogin) { this.ownerLogin = ownerLogin; }
    public Date getDueAt() { return dueAt; }
    public void setDueAt(Date dueAt) { this.dueAt = dueAt; }
    public String getTargetAccountName() { return targetAccountName; }
    public void setTargetAccountName(String targetAccountName) { this.targetAccountName = targetAccountName; }
    public String getTargetContactName() { return targetContactName; }
    public void setTargetContactName(String targetContactName) { this.targetContactName = targetContactName; }
    public String getTargetContactPhone() { return targetContactPhone; }
    public void setTargetContactPhone(String targetContactPhone) { this.targetContactPhone = targetContactPhone; }
    public Date getPrevFollowAt() { return prevFollowAt; }
    public void setPrevFollowAt(Date prevFollowAt) { this.prevFollowAt = prevFollowAt; }
    public String getResult() { return result; }
    public void setResult(String result) { this.result = result; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
    public String getFinishBy() { return finishBy; }
    public void setFinishBy(String finishBy) { this.finishBy = finishBy; }
    public Date getFinishTime() { return finishTime; }
    public void setFinishTime(Date finishTime) { this.finishTime = finishTime; }
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
