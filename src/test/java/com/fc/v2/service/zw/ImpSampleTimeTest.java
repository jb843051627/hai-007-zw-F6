package com.fc.v2.service.zw;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.text.SimpleDateFormat;
import java.util.Date;

import org.junit.jupiter.api.Test;

/**
 * 检测时间统一尺：入口多样式同尺收、假时间不进位、回显只有一串格式。
 */
public class ImpSampleTimeTest {

    /** 期望值一律按业务时区 GMT+8 造，不跟 JVM 默认时区走（本测机默认 UTC，正好逼出这一点） */
    private static SimpleDateFormat businessFmt(String pattern) {
        SimpleDateFormat sdf = new SimpleDateFormat(pattern);
        sdf.setTimeZone(ImpSampleTime.ZONE);
        return sdf;
    }

    @Test
    public void acceptsEveryDocumentedShapeWithSameInstant() throws Exception {
        Date anchor = businessFmt("yyyy-MM-dd HH:mm:ss").parse("2026-09-18 09:00:00");
        // 横杠/斜杠、带秒/不带秒、只到天（按当天 00:00:00 落定）都同一把尺
        assertEquals(anchor.getTime(), ImpSampleTime.parse("2026-09-18 09:00:00").getTime());
        assertEquals(anchor.getTime(), ImpSampleTime.parse("2026/09/18 09:00:00").getTime());
        assertEquals(anchor.getTime(), ImpSampleTime.parse("2026-09-18 09:00").getTime());
        assertEquals(anchor.getTime(), ImpSampleTime.parse("2026/9/18 9:00").getTime());

        Date dayStart = businessFmt("yyyy-MM-dd HH:mm:ss").parse("2026-09-18 00:00:00");
        assertEquals(dayStart.getTime(), ImpSampleTime.parse("2026-09-18").getTime());
        assertEquals(dayStart.getTime(), ImpSampleTime.parse("2026/09/18").getTime());
    }

    @Test
    public void bogusDatesAreRejectedNotShifted() {
        // lenient=false：9 月没有 31 号、25 点不存在，一律拒绝，不替录入人进位猜时
        assertNull(ImpSampleTime.parse("2026-09-31 09:00:00"));
        assertNull(ImpSampleTime.parse("2026-02-29 12:00"));
        assertNull(ImpSampleTime.parse("2026-09-18 25:00:00"));
        assertNull(ImpSampleTime.parse("not a date"));
        assertNull(ImpSampleTime.parse(""));
        assertNull(ImpSampleTime.parse(null));
    }

    @Test
    public void pageAndApiAndExportShareOneDisplay() throws Exception {
        // 08:30:05 GMT+8 这个时刻，JVM 是 UTC 也必须回显 08:30:05（同一把尺）
        Date at = businessFmt("yyyy-MM-dd HH:mm:ss").parse("2026-10-02 08:30:05");
        assertEquals("2026-10-02 08:30:05", ImpSampleTime.format(at));
        assertEquals("", ImpSampleTime.format(null));
        assertTrue(ImpSampleTime.acceptedPatterns().contains("yyyy-MM-dd HH:mm:ss"));
    }
}
