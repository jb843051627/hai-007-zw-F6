package com.fc.v2.common.quartz.task;

import org.springframework.stereotype.Component;

import com.fc.v2.service.ITZwFollowService;

/**
 * 水质异常盯办跑批：到期未盯的条由系统自动结成"漏盯"并原样续开下一条。
 *
 * <p>在定时任务里配 bean 名 zwFollowTask、方法 runDue 即可（与 v2Task 同套调用口径）；
 * 盯办记录仍只有系统自己开、自己结，本任务是唯一的跑批入口。
 *
 * @author fuce
 * @date 2026-10-03
 */
@Component("zwFollowTask")
public class ZwFollowTask {

    @org.springframework.beans.factory.annotation.Autowired
    private ITZwFollowService zwFollowService;

    /** 定时调用：扫描并处理所有到期待盯条 */
    public void runDue() {
        int n = zwFollowService.runDue(new java.util.Date());
        System.out.println("[zwFollowTask] 本次漏盯结条并续开 " + n + " 条");
    }
}
