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
 * 停供/限供申请签核单对象 t_zw_stop_bill
 *
 * <p>单上不设任何"已落印数"格子：本口过没过、落了几枚名，全由服务层顺着
 * {@link TZwStopSign} 签核台账一笔笔点算回来，屏上摆的与库里数的同出一次计数。
 *
 * @author fuce
 * @date 2026-09-12
 */
@TableName("t_zw_stop_bill")
@ApiModel(value = "TZwStopBill", description = "停供/限供申请签核单")
public class TZwStopBill implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 主键 */
    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    @ApiModelProperty(value = "主键")
    private Long id;

    /** 停供/限供申请签核单号（点位代号-年-当年序） */
    @TableField("bill_no")
    @ApiModelProperty(value = "停供/限供申请签核单号")
    private String billNo;

    /** 水源点代号（只认代号不认名字） */
    @TableField("site_no")
    @ApiModelProperty(value = "水源点代号")
    private String siteNo;

    /** 申请所属自然年（当年序按此归年） */
    @TableField("bill_year")
    @ApiModelProperty(value = "申请所属自然年")
    private Integer billYear;

    /** 同一水源点当年第几份申请（第二份当场单独点名） */
    @TableField("year_seq")
    @ApiModelProperty(value = "同一水源点当年第几份申请")
    private Integer yearSeq;

    /** 停水事由代号 DEPLETED水源枯竭/OVER_LIMIT指标连续超标/REPAIR检修 */
    @TableField("reason")
    @ApiModelProperty(value = "停水事由代号")
    private String reason;

    /** 停水后的替代供水方案 */
    @TableField("alt_plan")
    @ApiModelProperty(value = "停水后的替代供水方案")
    private String altPlan;

    /** 用户公告文案（按事由从名录带出，不经人手抄录） */
    @TableField("notice_text")
    @ApiModelProperty(value = "用户公告文案（名录带出）")
    private String noticeText;

    /** 当前所在道 0业务提 1水质核 2调度评 3主管准 */
    @TableField("node_no")
    @ApiModelProperty(value = "当前所在道 0业务提 1水质核 2调度评 3主管准")
    private Integer nodeNo;

    /** 签核轮次；主管打回一轮旧签核整轮作废，补签另起一轮 */
    @TableField("round_no")
    @ApiModelProperty(value = "签核轮次")
    private Integer roundNo;

    /** 报批情形 0在核 1已核讫 2已终止（不同意/撤回） */
    @TableField("status")
    @ApiModelProperty(value = "报批情形 0在核 1已核讫 2已终止")
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

    public String getBillNo() {
        return billNo;
    }

    public void setBillNo(String billNo) {
        this.billNo = billNo;
    }

    public String getSiteNo() {
        return siteNo;
    }

    public void setSiteNo(String siteNo) {
        this.siteNo = siteNo;
    }

    public Integer getBillYear() {
        return billYear;
    }

    public void setBillYear(Integer billYear) {
        this.billYear = billYear;
    }

    public Integer getYearSeq() {
        return yearSeq;
    }

    public void setYearSeq(Integer yearSeq) {
        this.yearSeq = yearSeq;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getAltPlan() {
        return altPlan;
    }

    public void setAltPlan(String altPlan) {
        this.altPlan = altPlan;
    }

    public String getNoticeText() {
        return noticeText;
    }

    public void setNoticeText(String noticeText) {
        this.noticeText = noticeText;
    }

    public Integer getNodeNo() {
        return nodeNo;
    }

    public void setNodeNo(Integer nodeNo) {
        this.nodeNo = nodeNo;
    }

    public Integer getRoundNo() {
        return roundNo;
    }

    public void setRoundNo(Integer roundNo) {
        this.roundNo = roundNo;
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
