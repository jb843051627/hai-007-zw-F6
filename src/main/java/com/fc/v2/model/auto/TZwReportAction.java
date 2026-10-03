package com.fc.v2.model.auto;

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
 * 上报处置动作台账对象 t_zw_report_action（只追加）。
 *
 * <p>上报、反映供水户、复测开出/走完/否决、结案归档、结案回退逐笔落账。
 * 已结案的来龙去脉从最后一笔倒着翻到第一笔，逐笔与当时经办屏对得上；
 * 本表没有任何 update/delete 写口，后段动作倒改不了前段。
 *
 * @author fuce
 * @date 2026-10-03
 */
@TableName("t_zw_report_action")
@ApiModel(value = "TZwReportAction", description = "上报处置动作台账")
public class TZwReportAction implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @TableField("report_id")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long reportId;

    @TableField("seq_no")
    private Integer seqNo;

    @TableField("action")
    @ApiModelProperty(value = "REPORT/REFLECT/RETEST_OPEN/RETEST_DONE/RETEST_VETO/CLOSE/CLOSE_ROLLBACK")
    private String action;

    @TableField("step_no")
    private Integer stepNo;

    @TableField("detail")
    private String detail;

    @TableField("actor")
    private String actor;

    @TableField("act_time")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date actTime;

    @TableField("del_flag")
    private Integer delFlag;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getReportId() { return reportId; }
    public void setReportId(Long reportId) { this.reportId = reportId; }
    public Integer getSeqNo() { return seqNo; }
    public void setSeqNo(Integer seqNo) { this.seqNo = seqNo; }
    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
    public Integer getStepNo() { return stepNo; }
    public void setStepNo(Integer stepNo) { this.stepNo = stepNo; }
    public String getDetail() { return detail; }
    public void setDetail(String detail) { this.detail = detail; }
    public String getActor() { return actor; }
    public void setActor(String actor) { this.actor = actor; }
    public Date getActTime() { return actTime; }
    public void setActTime(Date actTime) { this.actTime = actTime; }
    public Integer getDelFlag() { return delFlag; }
    public void setDelFlag(Integer delFlag) { this.delFlag = delFlag; }
}
