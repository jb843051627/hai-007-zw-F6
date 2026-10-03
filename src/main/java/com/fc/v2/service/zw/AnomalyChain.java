package com.fc.v2.service.zw;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.TimeZone;

/**
 * 异常登记→上报→复测→盯办→结案这一条链的全站唯一口径（纯领域、无 Spring、无库）。
 *
 * <p>档位沿用 {@link WaterGrade}（2关注/3超标/4严重超标）；本类只定这条链自己的规矩：
 * 哪一档要复测、哪一档要停水、超了多少怎么算、处置步与复测环的次序和名目、
 * 检测归属日怎么归（GMT+8 自然日，判重只认这一天）。服务层与页面共用这一把尺。
 *
 * @author fuce
 * @date 2026-10-03
 */
public final class AnomalyChain {

    private AnomalyChain() {
    }

    // ---- 上报单处置步 ----------------------------------------------------
    /** 0内部处置（上报刚落账，盯内部处置岗） */
    public static final int STEP_INTERNAL = 0;
    /** 1已反映供水户（盯联系人，周期最短） */
    public static final int STEP_REFLECTED = 1;
    /** 2复测回程（复测四环走完回到调度） */
    public static final int STEP_RETEST_BACK = 2;
    /** 3待结案（最后一步，在此归档） */
    public static final int STEP_TO_CLOSE = 3;
    public static final int STEP_COUNT = 4;

    /** 上报单情形 0在办 1已结案归档（结案可退回上一步，退回后落回 0 并在动作台账留一笔） */
    public static final int REPORT_RUNNING = 0;
    public static final int REPORT_CLOSED = 1;

    // ---- 复测四环 --------------------------------------------------------
    public static final int NODE_SAMPLE = 0;
    public static final int NODE_LAB = 1;
    public static final int NODE_WATER_JUDGE = 2;
    public static final int NODE_CHIEF_SEAL = 3;
    public static final int NODE_COUNT = 4;

    /** 复测情形 0待首环 1在办 2已全环走完 3否决卡停 */
    public static final int RETEST_WAIT_FIRST = 0;
    public static final int RETEST_RUNNING = 1;
    public static final int RETEST_DONE = 2;
    public static final int RETEST_VETOED = 3;

    /** 环节结论 */
    public static final String CON_PASS = "PASS";
    public static final String CON_FAIL = "FAIL";

    /** 环节台账情形 0待填 1已提交只读 2否决 */
    public static final int NODE_PENDING = 0;
    public static final int NODE_SUBMITTED = 1;
    public static final int NODE_VETOED = 2;

    // ---- 盯办流水 --------------------------------------------------------
    /** 0待盯 1已盯 2联系不上 3漏盯（系统自动结） */
    public static final int FOLLOW_WAIT = 0;
    public static final int FOLLOW_DONE = 1;
    public static final int FOLLOW_UNREACHABLE = 2;
    public static final int FOLLOW_MISSED = 3;

    /** 系统自己开结时的落名 */
    public static final String SYSTEM = "system";

    /** 上报处置动作台账的动作集合（屏上只许出这套名） */
    public static final String ACT_REPORT = "REPORT";
    public static final String ACT_REFLECT = "REFLECT";
    public static final String ACT_ADVANCE = "ADVANCE";
    public static final String ACT_RETEST_OPEN = "RETEST_OPEN";
    public static final String ACT_RETEST_DONE = "RETEST_DONE";
    public static final String ACT_RETEST_VETO = "RETEST_VETO";
    public static final String ACT_CLOSE = "CLOSE";
    public static final String ACT_CLOSE_ROLLBACK = "CLOSE_ROLLBACK";
    public static final String ACT_STEP_BACK = "STEP_BACK";

    /** 环节经办人绑定字典（dict_value=环节序，remark=登录名） */
    public static final String DICT_NODE_OWNER = "zw_retest_node_owner";

    private static final Map<Integer, String> STEP_NAMES = new HashMap<Integer, String>();
    private static final Map<Integer, String> NODE_NAMES = new HashMap<Integer, String>();

    static {
        STEP_NAMES.put(STEP_INTERNAL, "内部处置");
        STEP_NAMES.put(STEP_REFLECTED, "已反映供水户");
        STEP_NAMES.put(STEP_RETEST_BACK, "复测回程");
        STEP_NAMES.put(STEP_TO_CLOSE, "待结案");

        NODE_NAMES.put(NODE_SAMPLE, "采样复测");
        NODE_NAMES.put(NODE_LAB, "化验复核");
        NODE_NAMES.put(NODE_WATER_JUDGE, "水质科判定");
        NODE_NAMES.put(NODE_CHIEF_SEAL, "主管封卷");
    }

    /** 要不要复测：超标档起要复测（关注档并入下次检测，不开复测单） */
    public static boolean needRetest(int grade) {
        return grade >= WaterGrade.OVER;
    }

    /** 要不要停水：严重超标档才钉"要停水"，其余不开停水 */
    public static boolean needStop(int grade) {
        return grade >= WaterGrade.SEVERE;
    }

    /**
     * 超了多少：读数越过本档界值的那段。所各界值取该档对应的在设界值
     * （关注→th1，超标→th2，严重→th3）；界值空缺按"空档不设"向下取到在设的那一档界值。
     * 返回 [所越界值, 超额]；关注以下或给不出界值返回 null（登记文本就不写超额）。
     */
    public static BigDecimal[] overAmount(BigDecimal qty, int grade,
                                          BigDecimal th1, BigDecimal th2, BigDecimal th3) {
        BigDecimal base = null;
        if (grade == WaterGrade.SEVERE) {
            base = th3 != null ? th3 : (th2 != null ? th2 : th1);
        } else if (grade == WaterGrade.OVER) {
            base = th2 != null ? th2 : th1;
        } else if (grade == WaterGrade.WATCH) {
            base = th1;
        }
        if (base == null || qty == null) {
            return null;
        }
        return new BigDecimal[]{base, qty.subtract(base)};
    }

    /**
     * 判定完整过程成句：取的哪条线、当天三档界值、读数落在第几档、超了多少、处置口径。
     * 日后打开登记看到的就是这一句，不另行追算。
     */
    public static String judgeText(String ruleCode, Date ruleEffDate, String sampleType,
                                   BigDecimal qty, int grade,
                                   BigDecimal th1, BigDecimal th2, BigDecimal th3) {
        StringBuilder sb = new StringBuilder();
        sb.append("检测值").append(qty == null ? "" : qty.toPlainString());
        sb.append("（").append(sampleType == null ? "" : sampleType).append("）");
        sb.append("依 ").append(ruleCode);
        if (ruleEffDate != null) {
            sb.append("（").append(day(ruleEffDate)).append("起现行）");
        }
        sb.append("判定为").append(WaterGrade.gradeText(grade));
        sb.append("，当天界值 关注≤").append(th(th1))
          .append(" 超标≤").append(th(th2))
          .append(" 严重≤").append(th(th3));
        BigDecimal[] over = overAmount(qty, grade, th1, th2, th3);
        if (over != null) {
            sb.append("；越过").append(over[0].toPlainString())
              .append("，超").append(over[1].toPlainString());
        }
        sb.append("；处置：").append(WaterGrade.actionText(grade));
        if (needStop(grade)) {
            sb.append("，并须停水");
        }
        return sb.toString();
    }

    /** 把检测时刻归到 GMT+8 自然日 00:00:00——同点位同日同指标判重只认这一天 */
    public static Date truncateToDay(Date at) {
        if (at == null) {
            return null;
        }
        Calendar c = Calendar.getInstance(TimeZone.getTimeZone("GMT+8"));
        c.setTime(at);
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTime();
    }

    /** GMT+8 归日成 yyyyMMdd（登记号/上报号用） */
    public static String dayKey(Date at) {
        if (at == null) {
            return "";
        }
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd");
        sdf.setTimeZone(TimeZone.getTimeZone("GMT+8"));
        return sdf.format(at);
    }

    /** GMT+8 取自然年（上报当年序按此归年） */
    public static int yearOf(Date at) {
        Calendar c = Calendar.getInstance(TimeZone.getTimeZone("GMT+8"));
        c.setTime(at);
        return c.get(Calendar.YEAR);
    }

    /** 此刻加 days 个自然日 */
    public static Date plusDays(Date from, int days) {
        Calendar c = Calendar.getInstance(TimeZone.getTimeZone("GMT+8"));
        c.setTime(from);
        c.add(Calendar.DAY_OF_MONTH, days);
        return c.getTime();
    }

    public static String stepName(int step) {
        String n = STEP_NAMES.get(step);
        return n == null ? "未知步次" : n;
    }

    public static String nodeName(int node) {
        String n = NODE_NAMES.get(node);
        return n == null ? "未知环节" : n;
    }

    public static String conclusionText(String c) {
        if (CON_PASS.equals(c)) {
            return "确认通过";
        }
        if (CON_FAIL.equals(c)) {
            return "否决";
        }
        return "";
    }

    public static String followStatusText(int status) {
        switch (status) {
            case FOLLOW_DONE:
                return "已盯";
            case FOLLOW_UNREACHABLE:
                return "联系不上";
            case FOLLOW_MISSED:
                return "漏盯";
            default:
                return "待盯";
        }
    }

    private static String th(BigDecimal v) {
        return v == null ? "不设" : v.toPlainString();
    }

    private static String day(Date d) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        sdf.setTimeZone(TimeZone.getTimeZone("GMT+8"));
        return sdf.format(d);
    }
}
