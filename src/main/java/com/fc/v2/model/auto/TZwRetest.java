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
 * 复测单对象 t_zw_retest。
 *
 * <p>上报后需要复测的由系统自动开出，不由人手建。固定四环（采样复测→化验复核→
 * 水质科判定→主管封卷）；走到第几环、卡没卡停全顺 {@link TZwRetestNode} 台账点算，
 * 单上 current_node 只是冗余镜像，推进以环节台账的条件插入/更新为准。
 *
 * @author fuce
 * @date 2026-10-03
 */
@TableName("t_zw_retest")
@ApiModel(value = "TZwRetest", description = "复测单")
public class TZwRetest implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @TableField("retest_no")
    private String retestNo;

    @TableField("report_id")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long reportId;

    @TableField("anom_id")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long anomId;

    @TableField("site_no")
    private String siteNo;

    @TableField("item_code")
    private String itemCode;

    @TableField("round_no")
    @ApiModelProperty(value = "复测轮次（否决后另起一轮）")
    private Integer roundNo;

    @TableField("node_count")
    private Integer nodeCount;

    @TableField("current_node")
    private Integer currentNode;

    @TableField("status")
    @ApiModelProperty(value = "0待首环 1在办 2已走完 3否决卡停")
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
    public String getRetestNo() { return retestNo; }
    public void setRetestNo(String retestNo) { this.retestNo = retestNo; }
    public Long getReportId() { return reportId; }
    public void setReportId(Long reportId) { this.reportId = reportId; }
    public Long getAnomId() { return anomId; }
    public void setAnomId(Long anomId) { this.anomId = anomId; }
    public String getSiteNo() { return siteNo; }
    public void setSiteNo(String siteNo) { this.siteNo = siteNo; }
    public String getItemCode() { return itemCode; }
    public void setItemCode(String itemCode) { this.itemCode = itemCode; }
    public Integer getRoundNo() { return roundNo; }
    public void setRoundNo(Integer roundNo) { this.roundNo = roundNo; }
    public Integer getNodeCount() { return nodeCount; }
    public void setNodeCount(Integer nodeCount) { this.nodeCount = nodeCount; }
    public Integer getCurrentNode() { return currentNode; }
    public void setCurrentNode(Integer currentNode) { this.currentNode = currentNode; }
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
