package com.vcp.portrait.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 画像维度得分，对应前端 {@code MyPortraitView} 的维度条与柱状图
 * （{@code portrait.dimensions.map(d => ({tag: d.name, count: d.value}))}）。
 *
 * <p>{@code value} 是 0~100 的整数分，前端按满分 100 渲染进度条。
 */
@Data
public class PortraitDimensionVO implements Serializable {

    /** 维度名：服务时长 / 活动场次 / 社区服务 / 环保行动 / 持续性 */
    private String name;

    /** 得分，0~100 */
    private Integer value;

    /**
     * 构造一个维度得分。
     *
     * @param name  维度名
     * @param value 得分，0~100
     */
    public PortraitDimensionVO(String name, Integer value) {
        this.name = name;
        this.value = value;
    }
}
