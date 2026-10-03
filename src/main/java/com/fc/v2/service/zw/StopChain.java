package com.fc.v2.service.zw;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fc.v2.model.auto.TZwImpRow;
import com.fc.v2.model.auto.TZwStopSign;

/**
 * 停供申请三道（实为四口：业务提／水质核／调度评／主管准）签核链的唯一口径。
 *
 * <p>关口次序、每口应落名枚数、角色归属、停水事由集合、"指标连续超标"与近月检测数据如何对照，
 * 全在本类定死，服务层与页面视图共用这一把尺，谁也不许另写一套。本类无状态、不落库，
 * 落名枚数由调用方把签核台账递进来现数（{@link #countAgree}），不接受任何手填数字。
 *
 * @author fuce
 * @date 2026-09-30
 */
public final class StopChain {

    private StopChain() {
    }

    /** 关口：0业务科提出 / 1水质科核实 / 2调度中心评估 / 3调度主管批准 */
    public static final int NODE_BUSINESS = 0;
    public static final int NODE_WATER = 1;
    public static final int NODE_DISPATCH = 2;
    public static final int NODE_CHIEF = 3;
    public static final int NODE_COUNT = 4;

    /** 报批情形 0在核 1已核讫 2已终止（不同意/撤回） */
    public static final int STATUS_RUNNING = 0;
    public static final int STATUS_PASS = 1;
    public static final int STATUS_STOPPED = 2;

    /** 落名口 */
    public static final String ROLE_BUSINESS = "BUSINESS";
    public static final String ROLE_WATER_QUA = "WATER_QUA";
    public static final String ROLE_DISP_QTY = "DISP_QTY";
    public static final String ROLE_DISP_NET = "DISP_NET";
    public static final String ROLE_DISP_CHIEF = "DISP_CHIEF";
    public static final String ROLE_APPLICANT = "APPLICANT";

    /** 落印动作 */
    public static final String ACT_AGREE = "AGREE";
    public static final String ACT_DISAGREE = "DISAGREE";
    public static final String ACT_WITHDRAW = "WITHDRAW";
    public static final String ACT_SENDBACK = "SENDBACK";

    /** 停水事由：水源枯竭 / 指标连续超标 / 水厂检修 */
    public static final String REASON_DEPLETED = "DEPLETED";
    public static final String REASON_OVER_LIMIT = "OVER_LIMIT";
    public static final String REASON_REPAIR = "REPAIR";

    /** 超标档（含严重超标）等级下限 */
    private static final int GRADE_OVER_LIMIT = 3;

    private static final Map<Integer, String> NODE_NAMES = new HashMap<Integer, String>();
    private static final Map<Integer, List<String>> NODE_ROLES = new HashMap<Integer, List<String>>();
    private static final int[] NEED_COUNTS = new int[NODE_COUNT];

    static {
        NODE_NAMES.put(NODE_BUSINESS, "业务科提出");
        NODE_NAMES.put(NODE_WATER, "水质科核实");
        NODE_NAMES.put(NODE_DISPATCH, "调度中心评估");
        NODE_NAMES.put(NODE_CHIEF, "调度主管批准");

        NODE_ROLES.put(NODE_BUSINESS, Collections.singletonList(ROLE_BUSINESS));
        NODE_ROLES.put(NODE_WATER, Collections.singletonList(ROLE_WATER_QUA));
        // 一人看水量调配、一人看管网影响，各评各的、互不替签
        NODE_ROLES.put(NODE_DISPATCH, java.util.Arrays.asList(ROLE_DISP_QTY, ROLE_DISP_NET));
        NODE_ROLES.put(NODE_CHIEF, Collections.singletonList(ROLE_DISP_CHIEF));

        NEED_COUNTS[NODE_BUSINESS] = 1;
        NEED_COUNTS[NODE_WATER] = 1;
        NEED_COUNTS[NODE_DISPATCH] = 2;
        NEED_COUNTS[NODE_CHIEF] = 1;
    }

    /** 关口名（屏上只许出这套名） */
    public static String nodeName(int node) {
        String n = NODE_NAMES.get(node);
        return n == null ? "未知关口" : n;
    }

    /** 该口应落名枚数：调度评估口两名点齐，其余各一枚 */
    public static int needCount(int node) {
        return node >= 0 && node < NODE_COUNT ? NEED_COUNTS[node] : 0;
    }

    /** 该口的落名口（有序） */
    public static List<String> rolesOf(int node) {
        List<String> roles = NODE_ROLES.get(node);
        return roles == null ? Collections.<String>emptyList() : roles;
    }

    /** 角色是否归属于该关口——调度两枚名各有专属口，不许跨口替签 */
    public static boolean roleAllowedAt(int node, String role) {
        return rolesOf(node).contains(role);
    }

    /** 停水事由只认这三个代号，页面自由文不算数 */
    public static boolean validReason(String reason) {
        return REASON_DEPLETED.equals(reason)
                || REASON_OVER_LIMIT.equals(reason)
                || REASON_REPAIR.equals(reason);
    }

    /**
     * 顺着签核台账点算当前轮该口已落同意名的枚数：一条 AGREE 算一枚，
     * 同一分钟两枚仍是两笔；作废轮、不同意/撤回/打回笔一律不数。
     * 台账里没有任何"代填枚数"的格子，数出来是几就是几。
     */
    public static int countAgree(List<TZwStopSign> lines) {
        int n = 0;
        if (lines != null) {
            for (TZwStopSign line : lines) {
                if (ACT_AGREE.equals(line.getAction())) {
                    n++;
                }
            }
        }
        return n;
    }

    /** 该口当前轮是否已点满 */
    public static boolean gateFull(int node, List<TZwStopSign> lines) {
        return countAgree(lines) >= needCount(node);
    }

    /**
     * 第二道核实：拿近月检测数据与申请事由对照，只认点位代号（数据行已按代号捞齐）。
     * <ul>
     *   <li>事由写"指标连续超标"：近月须有相邻两个月都落在超标档，否则属理由写错，不通过；</li>
     *   <li>事由写枯竭/检修：近月若明明有相邻两月超标，同样判理由与数据不符，不通过；</li>
     *   <li>其余情形数据不构成反证，予以通过。</li>
     * </ul>
     */
    public static boolean reasonMatchesData(String reason, List<TZwImpRow> recentRows) {
        boolean consecutive = hasConsecutiveOverLimitMonths(recentRows);
        if (REASON_OVER_LIMIT.equals(reason)) {
            return consecutive;
        }
        // 枯竭/检修却挂着连续超标的账，理由与数据对不上
        return !consecutive;
    }

    /** 近月数据中是否存在相邻两个自然月都有超标（含严重超标）检测行 */
    public static boolean hasConsecutiveOverLimitMonths(List<TZwImpRow> rows) {
        if (rows == null || rows.isEmpty()) {
            return false;
        }
        List<Integer> months = new ArrayList<Integer>();
        for (TZwImpRow r : rows) {
            if (r.getTestAt() == null || r.getGradeLevel() == null) {
                continue;
            }
            if (r.getGradeLevel().intValue() >= GRADE_OVER_LIMIT) {
                months.add(monthKey(r.getTestAt()));
            }
        }
        if (months.size() < 2) {
            return false;
        }
        Collections.sort(months);
        for (int i = 1; i < months.size(); i++) {
            if (adjacentMonthKeys(months.get(i - 1), months.get(i))) {
                return true;
            }
        }
        return false;
    }

    /** 两个 yyyyMM 月键是否相邻自然月，含跨年（202512→202601） */
    private static boolean adjacentMonthKeys(int earlier, int later) {
        int ey = earlier / 100;
        int em = earlier % 100;
        int ly = later / 100;
        int lm = later % 100;
        if (ey == ly) {
            return lm - em == 1;
        }
        return ly - ey == 1 && em == 12 && lm == 1;
    }

    /** yyyyMM 连成整数，是否相邻交由 {@link #adjacentMonthKeys} 判（含跨年） */
    private static int monthKey(Date d) {
        Calendar c = Calendar.getInstance();
        c.setTime(d);
        return c.get(Calendar.YEAR) * 100 + (c.get(Calendar.MONTH) + 1);
    }
}
