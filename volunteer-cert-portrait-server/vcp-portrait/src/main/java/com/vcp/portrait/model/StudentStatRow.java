package com.vcp.portrait.model;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 学生档案中与画像相关的两列投影：累计有效时长（权威值）与现有公益等级。
 *
 * <p>重算时先一次性取回，用于「算等级」与「判断是否需要写回等级」两件事 ——
 * 有了现有的等级值就不必在写之前再查一次库。
 */
@Data
public class StudentStatRow implements Serializable {

    /** 学生档案 id */
    private Long studentId;

    /** 累计有效志愿时长（小时），权威值 */
    private BigDecimal totalDuration;

    /** 现有公益等级（中文等级名），未算过时为 null */
    private String publicWelfareLevel;
}
