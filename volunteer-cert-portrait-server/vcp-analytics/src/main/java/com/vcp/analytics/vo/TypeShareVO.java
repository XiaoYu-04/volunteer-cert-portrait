package com.vcp.analytics.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 活动类型占比的一项，对应前端 {@code charts.typePie} / {@code charts.typeBar}。
 *
 * <p>字段与 {@code dataset.js} 的 {@code types} 对齐：{@code {name, value}}。
 * name 是活动分类名（环形图的图例与扇区标签都用它），value 是该分类的活动场次。
 */
@Data
public class TypeShareVO implements Serializable {

    /** 活动分类名称 */
    private String name;

    /** 该分类下的活动场次 */
    private Long value;
}
