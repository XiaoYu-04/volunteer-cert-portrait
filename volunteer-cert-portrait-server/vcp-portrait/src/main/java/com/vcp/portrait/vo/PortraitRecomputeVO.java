package com.vcp.portrait.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 画像重算结果。
 *
 * <p>返回两个数而不是空响应：重算是幂等的（内容没变就不写库），
 * 只回「成功」看不出到底有没有生效，管理员需要知道扫了多少人、实际改了多少份。
 */
@Data
public class PortraitRecomputeVO implements Serializable {

    /** 本次扫描的学生数 */
    private Integer scanned;

    /** 实际写入（新建或内容有变化）的画像份数 */
    private Integer updated;

    /**
     * 构造重算结果。
     *
     * @param scanned 扫描的学生数
     * @param updated 实际写入的画像份数
     */
    public PortraitRecomputeVO(Integer scanned, Integer updated) {
        this.scanned = scanned;
        this.updated = updated;
    }
}
