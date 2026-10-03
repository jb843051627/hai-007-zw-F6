package com.fc.v2.service.zw;

import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;

/**
 * 检测时间的全站唯一一把尺——页面展示与接口入库共用，不许两处各量各的。
 *
 * <p>Excel 里检测时间一格可能被录成 {@code 2026-09-18 09:00:00}、{@code 2026/9/18 9:00}
 * 或只到天的 {@code 2026-09-18}：入口全部按本组样式逐个试，{@code setLenient(false)}
 * 拒绝 {@code 2026-09-31 25:00:00} 这种自行进位的假时间。解析失败即硬错，整批拦下，
 * 绝不替录入人猜一个时间。
 *
 * <p>回显一律 {@link #DISPLAY}（{@code yyyy-MM-dd HH:mm:ss}）：
 * 明细列表、下钻页、导出 Excel、接口报文里的检测时间同一串字符；
 * 只给到天的原始输入按当天 {@code 00:00:00} 落定，回显也照此补齐，不藏秒位差异。
 *
 * @author fuce
 * @date 2026-10-02
 */
public final class ImpSampleTime {

    /** 页面与接口统一回显样式 */
    public static final String DISPLAY = "yyyy-MM-dd HH:mm:ss";

    /**
     * 全站业务时区钉死 GMT+8：与实体上的 {@code @JsonFormat(timezone="GMT+8")} 同一把尺。
     * 不许用 JVM 默认时区——服务器挪到 UTC 机房时，页面、接口、导出会同时整体错位八小时。
     */
    public static final TimeZone ZONE = TimeZone.getTimeZone("GMT+8");

    /** 入口允许的录法（按序尝试，全不命中才算时间对不上） */
    private static final List<String> ACCEPTED = Collections.unmodifiableList(Arrays.asList(
            "yyyy-MM-dd HH:mm:ss",
            "yyyy/MM/dd HH:mm:ss",
            "yyyy-MM-dd HH:mm",
            "yyyy/MM/dd HH:mm",
            "yyyy-MM-dd",
            "yyyy/MM/dd"
    ));

    private ImpSampleTime() {
    }

    /**
     * 用同一把尺解析检测时间。空白/不合规一律返回 {@code null}，由校验方记硬错；
     * lenient=false，假日期不进位、不通融。
     *
     * <p>用 {@link ParsePosition} 并强制 {@code pos=串长}：{@code SimpleDateFormat.parse} 默认
     * 只吃前缀，{@code "2026-09-18 25:00:00"} 会被「yyyy-MM-dd」截成 9 月 18 日蒙混过关——
     * 不许整串吃干净就等于没解析掉。
     */
    public static Date parse(String text) {
        if (text == null) {
            return null;
        }
        String t = text.trim();
        if (t.isEmpty()) {
            return null;
        }
        for (String p : ACCEPTED) {
            SimpleDateFormat sdf = new SimpleDateFormat(p);
            sdf.setTimeZone(ZONE);
            sdf.setLenient(false);
            ParsePosition pos = new ParsePosition(0);
            Date d = sdf.parse(t, pos);
            if (d != null && pos.getIndex() == t.length()) {
                return d;
            }
        }
        return null;
    }

    /** 统一回显：所有出口（页面/接口/导出）只准用这一串格式、这一个时区 */
    public static String format(Date at) {
        if (at == null) {
            return "";
        }
        SimpleDateFormat sdf = new SimpleDateFormat(DISPLAY);
        sdf.setTimeZone(ZONE);
        return sdf.format(at);
    }

    /** 日期粒度回显（规则生效日等只到天的字段），时区与 {@link #format} 同一把尺 */
    public static String formatDay(Date at) {
        if (at == null) {
            return "";
        }
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        sdf.setTimeZone(ZONE);
        return sdf.format(at);
    }

    /** 入口接受的样式清单（提示文案/文档引用，不得在别处另立清单） */
    public static List<String> acceptedPatterns() {
        return ACCEPTED;
    }
}
