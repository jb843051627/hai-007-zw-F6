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
 * 复测环节台账对象 t_zw_retest_node。
 *
 * <p>只追加。每环一行：首环实录四件套（项目/数值/时间/结论），后环必须把上一环结果
 * 再填一遍，服务端逐字比对通过才收；提交后本环只读。上一环没填完或结论否决，下一环不开。
 *
 * @author fuce
 * @date 2026-10-03
 */
@TableName("t_zw_retest_node")
@ApiModel(value = "TZwRetestNode", description = "复测环节台账")
public class TZwRetestNode implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @TableField("retest_id")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long retestId;

    @TableField("node_no")
    private Integer nodeNo;

    @TableField("owner_login")
    private String ownerLogin;

    @TableField("test_item")
    private String testItem;

    @TableField("test_qty")
    private BigDecimal testQty;

    @TableField("test_at")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date testAt;

    @TableField("conclusion")
    @ApiModelProperty(value = "PASS/FAIL")
    private String conclusion;

    @TableField("opinion")
    private String opinion;

    @TableField("prev_item")
    private String prevItem;

    @TableField("prev_qty")
    private BigDecimal prevQty;

    @TableField("prev_test_at")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date prevTestAt;

    @TableField("prev_conclusion")
    private String prevConclusion;

    @TableField("status")
    @ApiModelProperty(value = "0待填 1已提交只读 2否决")
    private Integer status;

    @TableField("fill_by")
    private String fillBy;

    @TableField("fill_time")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date fillTime;

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
    public Long getRetestId() { return retestId; }
    public void setRetestId(Long retestId) { this.retestId = retestId; }
    public Integer getNodeNo() { return nodeNo; }
    public void setNodeNo(Integer nodeNo) { this.nodeNo = nodeNo; }
    public String getOwnerLogin() { return ownerLogin; }
    public void setOwnerLogin(String ownerLogin) { this.ownerLogin = ownerLogin; }
    public String getTestItem() { return testItem; }
    public void setTestItem(String testItem) { this.testItem = testItem; }
    public BigDecimal getTestQty() { return testQty; }
    public void setTestQty(BigDecimal testQty) { this.testQty = testQty; }
    public Date getTestAt() { return testAt; }
    public void setTestAt(Date testAt) { this.testAt = testAt; }
    public String getConclusion() { return conclusion; }
    public void setConclusion(String conclusion) { this.conclusion = conclusion; }
    public String getOpinion() { return opinion; }
    public void setOpinion(String opinion) { this.opinion = opinion; }
    public String getPrevItem() { return prevItem; }
    public void setPrevItem(String prevItem) { this.prevItem = prevItem; }
    public BigDecimal getPrevQty() { return prevQty; }
    public void setPrevQty(BigDecimal prevQty) { this.prevQty = prevQty; }
    public Date getPrevTestAt() { return prevTestAt; }
    public void setPrevTestAt(Date prevTestAt) { this.prevTestAt = prevTestAt; }
    public String getPrevConclusion() { return prevConclusion; }
    public void setPrevConclusion(String prevConclusion) { this.prevConclusion = prevConclusion; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
    public String getFillBy() { return fillBy; }
    public void setFillBy(String fillBy) { this.fillBy = fillBy; }
    public Date getFillTime() { return fillTime; }
    public void setFillTime(Date fillTime) { this.fillTime = fillTime; }
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
