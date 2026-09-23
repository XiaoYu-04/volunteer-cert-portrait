package com.vcp.certification.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 提交服务时长的响应：前端只取 {@code data.ids} 的长度用于提示「已提交 N 条」。
 */
@Data
public class DurationSubmitVO implements Serializable {

    /** 本次提交（含重新提交）的时长记录 id，顺序与请求中的 items 一致 */
    private List<Long> ids;

    /**
     * 构造响应。
     *
     * @param ids 时长记录 id 列表，允许为 null
     * @return 响应对象
     */
    public static DurationSubmitVO of(List<Long> ids) {
        DurationSubmitVO vo = new DurationSubmitVO();
        vo.setIds(ids == null ? List.of() : ids);
        return vo;
    }
}
