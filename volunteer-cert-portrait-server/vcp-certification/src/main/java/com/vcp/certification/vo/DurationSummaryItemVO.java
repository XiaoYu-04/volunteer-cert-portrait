package com.vcp.certification.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 时长审核三态统计中的单项，对应前端 mock 的 {@code { name, value, tone }}。
 *
 * <p>tone 是标签色调（ok 绿 / warn 橙 / bad 红），与 sys_dict.tone、前端 StatusTag 取值一致。
 */
@Data
public class DurationSummaryItemVO implements Serializable {

    /** 中文名称，如「已通过」 */
    private String name;

    /** 条数 */
    private long value;

    /** 色调：ok / warn / bad */
    private String tone;

    /**
     * 构造单项。
     *
     * @param name  中文名称
     * @param value 条数
     * @param tone  色调
     * @return 统计项
     */
    public static DurationSummaryItemVO of(String name, long value, String tone) {
        DurationSummaryItemVO item = new DurationSummaryItemVO();
        item.setName(name);
        item.setValue(value);
        item.setTone(tone);
        return item;
    }
}
