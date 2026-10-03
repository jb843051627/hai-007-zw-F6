package com.fc.v2.service;

import java.util.Date;
import java.util.List;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.fc.v2.model.auto.TZwFollow;
import com.fc.v2.model.auto.TZwFollowTemplate;

/**
 * 盯办 Service接口。盯办记录只有系统能开、也只能系统结——页面没有任何人手开条/撤条/改名的口子。
 *
 * <p>处置步推进时 {@link #openStep} 自动开该步第一条（带上一次盯的时间）；经办通过
 * {@link #finish} 登记本次盯办结果，系统同事务原样开下一条（联系不上也算盯完，周期另算）；
 * {@link #runDue} 跑批把到期未盯的自动结成"漏盯"并续开。周期与盯办人只照
 * t_zw_follow_template，反映供水户那一步盯得最紧。
 *
 * @author fuce
 * @date 2026-10-03
 */
public interface ITZwFollowService {

    /** 按步次取现行模板（无模板返回 null，该步即不开盯办） */
    TZwFollowTemplate templateOfStep(int stepNo);

    /**
     * 系统在处置步推进到 stepNo 时调用：结掉上报单上所有待盯旧条（换步留痕，记"步次推进"），
     * 再照该步模板开第一条盯办（prev_follow_at 取本单上一条已盯/漏盯的时刻）。
     * 无模板则只结旧条、不开新条（结案步之后不再盯）。
     */
    void openStep(Long reportId, int stepNo, Date now);

    /**
     * 经办登记本次盯办结果：只能结本步模板绑给当前登录名的待盯条。
     * reached=false 即联系不上（仍算盯完，续开下一条，盯得紧的周期不变）。
     * 结条与续开同一事务；后段写法带 id+status=0 条件，倒改不了前段。
     */
    TZwFollow finish(Long followId, boolean reached, String result, String operator, List<String> rejectMsg);

    /**
     * 跑批（定时任务/手动同一口）：扫描 due_at 已到仍待盯的条，系统结成"漏盯"（finish_by=system），
     * 并在同一步原样续开下一条。返回本次处理条数。
     */
    int runDue(Date now);

    /** 结案收口：本单所有待盯条由系统结掉，此后不再续开（只结不开） */
    void closeAll(Long reportId, Date now);

    /** 本单全部盯办流水（按步次、序号正序——隔几个月也一条条翻得出） */
    List<TZwFollow> listOfReport(Long reportId);

    /** 台账查询（漏盯报表用） */
    List<TZwFollow> selectList(Wrapper<TZwFollow> queryWrapper);
}
