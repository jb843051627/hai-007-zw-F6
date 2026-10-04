package com.fc.v2.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.fc.v2.model.auto.TZwArchCard;

import java.util.List;

/**
 * 水源地/水厂档案卡 Service接口
 *
 * @author fuce
 * @date 2026-09-12
 */
public interface ITZwArchCardService {

    /** 按行主键查某一版（退役底档名下的旧版照样查得到） */
    TZwArchCard selectTZwArchCardById(Long id);

    /** 台账查询：每张卡只回当前在用的最新一版，旧版另走 {@link #listVersions} 备查 */
    List<TZwArchCard> selectTZwArchCardList(Wrapper<TZwArchCard> queryWrapper);

    /** 同一张卡（同一 bill_no）的各版留痕，按版次序正序——旧版一律在，只许翻不许改 */
    List<TZwArchCard> listVersions(String billNo);

    /**
     * 立卡挂底档。卡号由系统发，入参夹带的 billNo 不拦、但一律不用；
     * 挂接只凭底档编号核对，名字像不认；底档已退役的新卡不许挂。
     */
    TZwArchCard insertTZwArchCard(TZwArchCard record);

    /**
     * 重录一版。旧版整行原样留痕退下来备查，另插 nodeNo+1 的新版：
     * 规模有增减、或卡改挂别的底档，层级照新口径重折；新版从"待核对"重新走起，
     * 已压死旧版上的校验串一字不动。底档已封口的，旧卡也一律回头改不得。
     */
    TZwArchCard revise(TZwArchCard record);

    /** 进展推一步：待核对(0) → 已核对(1) */
    TZwArchCard confirm(Long id);

    /**
     * 进展再推一步：已核对(1) → 已压死(2)。校验串到这一步才定形；
     * 定形之后该行谁也改它不得，要改只能另录新版。
     */
    TZwArchCard pin(Long id);

    /** 软删整张卡（各版一并置删，行不物理清除，留着备查） */
    int deleteTZwArchCardByIds(String ids);
}
