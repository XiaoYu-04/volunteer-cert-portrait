package com.vcp.certification.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 批量审核的响应：前端读 {@code data.count} 提示「已通过 N 条」。
 *
 * <p>count 是**实际处理**的条数，不是请求里的 id 个数：已审核过的记录会被跳过
 * （批量操作面向的是当前页待审核列表，列表可能已经过期，为一条过期数据整批失败不合理）。
 */
@Data
public class DurationBatchAuditVO implements Serializable {

    /** 实际处理的条数 */
    private int count;

    /**
     * 构造响应。
     *
     * @param count 实际处理条数
     * @return 响应对象
     */
    public static DurationBatchAuditVO of(int count) {
        DurationBatchAuditVO vo = new DurationBatchAuditVO();
        vo.setCount(count);
        return vo;
    }
}
