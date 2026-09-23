package com.vcp.certification.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * 提交服务时长的请求体，<b>同时支持两种形状</b>（前端 submitDurations 的注释写明了这一点）：
 *
 * <pre>
 * 单条： { "studentId": 1, "activityId": 2, "hours": 4 }
 * 批量： { "items": [ { "studentId": 1, ... }, { "studentId": 2, ... } ] }
 * </pre>
 *
 * <p>之所以用「继承单条 DTO + 一个 items 字段」而不是用 Map 接：批量形状下顶层的
 * studentId 等字段为空、单条形状下 items 为空，两者由 {@link #resolveItems()} 归一，
 * 不需要在 Controller 里做类型判断，Knife4j 也能正常展示字段。
 *
 * <p>本类刻意不加 {@code @Valid} 与字段级 {@code @NotNull}：批量形状下顶层字段本来就是空的，
 * 加了校验注解会让「{items:[...]}」这种合法请求直接被参数校验拦掉。逐条校验在 Service 里做，
 * 报错文案能带上「第 N 条」的定位信息。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class DurationSubmitDTO extends DurationItemDTO {

    /** 批量提交的明细；单条提交时为空 */
    private List<DurationItemDTO> items;

    /**
     * 归一成待处理的明细列表。
     *
     * @return 批量形状返回 items，单条形状返回只含自身的单元素列表；两者都为空时返回空列表
     */
    public List<DurationItemDTO> resolveItems() {
        if (items != null && !items.isEmpty()) {
            return items;
        }
        return List.<DurationItemDTO>of(this);
    }
}
