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
 * 停供申请签核台账（只追加）对象 t_zw_stop_sign
 *
 * <p>每过一道口、每落一枚名都是独立一笔：两名评估人即使同一分钟签完也是两笔，绝不并成一笔；
 * 代签不另落笔，当作没签。主管打回后旧轮各笔原样留痕（轮次不覆盖、不删除），
 * 补签另起新一轮，作废的与补签的不摆在一起。任何关口结论都顺着本表点算，没有旁路口径。
 *
 * @author fuce
 * @date 2026-09-30
 */
@TableName("t_zw_stop_sign")
@ApiModel(value = "TZwStopSign", description = "停供申请签核台账（只追加）")
public class TZwStopSign implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 主键 */
    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    @ApiModelProperty(value = "主键")
    private Long id;

    /** 所属签核单 */
    @TableField("bill_id")
    @ApiModelProperty(value = "所属签核单")
    private Long billId;

    /** 落印轮次（作废轮原样留痕，计数只数当前轮） */
    @TableField("round_no")
    @ApiModelProperty(value = "落印轮次")
    private Integer roundNo;

    /** 关口 0业务提 1水质核 2调度评 3主管准 */
    @TableField("node_no")
    @ApiModelProperty(value = "关口 0业务提 1水质核 2调度评 3主管准")
    private Integer nodeNo;

    /** 落名口 BUSINESS经办/WATER_QUA水质核实/DISP_QTY水量调配评估/DISP_NET管网影响评估/DISP_CHIEF调度主管/APPLICANT申请人 */
    @TableField("sign_role")
    @ApiModelProperty(value = "落名口")
    private String signRole;

    /** 落名人（各评各的，互不替签） */
    @TableField("signer")
    @ApiModelProperty(value = "落名人")
    private String signer;

    /** 落印动作 AGREE同意/DISAGREE不同意/WITHDRAW撤回/SENDBACK主管打回 */
    @TableField("action")
    @ApiModelProperty(value = "落印动作")
    private String action;

    /** 签核意见 */
    @TableField("opinion")
    @ApiModelProperty(value = "签核意见")
    private String opinion;

    /** 落印时刻（同一分钟两枚仍是两笔） */
    @TableField("sign_time")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "落印时刻")
    private Date signTime;

    /** 删除标记 0正常（只追加，不置删） */
    @TableField("del_flag")
    @ApiModelProperty(value = "删除标记")
    private Integer delFlag;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getBillId() {
        return billId;
    }

    public void setBillId(Long billId) {
        this.billId = billId;
    }

    public Integer getRoundNo() {
        return roundNo;
    }

    public void setRoundNo(Integer roundNo) {
        this.roundNo = roundNo;
    }

    public Integer getNodeNo() {
        return nodeNo;
    }

    public void setNodeNo(Integer nodeNo) {
        this.nodeNo = nodeNo;
    }

    public String getSignRole() {
        return signRole;
    }

    public void setSignRole(String signRole) {
        this.signRole = signRole;
    }

    public String getSigner() {
        return signer;
    }

    public void setSigner(String signer) {
        this.signer = signer;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getOpinion() {
        return opinion;
    }

    public void setOpinion(String opinion) {
        this.opinion = opinion;
    }

    public Date getSignTime() {
        return signTime;
    }

    public void setSignTime(Date signTime) {
        this.signTime = signTime;
    }

    public Integer getDelFlag() {
        return delFlag;
    }

    public void setDelFlag(Integer delFlag) {
        this.delFlag = delFlag;
    }
}
