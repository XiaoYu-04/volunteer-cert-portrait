package com.vcp.analytics.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 服务时长认证流程的一步，对应前端 {@code data.flow} 的四步流程条。
 *
 * <p>字段与 {@code dataset.js} 的 {@code flow} 对齐：{@code {step, desc}}。
 *
 * <p><b>这是固定的业务说明文案，不来自任何表</b>：流程本身由代码里的状态机决定
 * （浏览活动 → 报名审核 → 签到签退 → 时长认证），库里没有对应的配置表，
 * 也没有必要为四行静态文案建表。内容与 {@code mock/data/dataset.js} 保持一致，
 * 改动需两边同时改。
 */
@Data
public class FlowStepVO implements Serializable {

    /** 步骤名，如「报名审核」 */
    private String step;

    /** 步骤说明 */
    private String desc;
}
