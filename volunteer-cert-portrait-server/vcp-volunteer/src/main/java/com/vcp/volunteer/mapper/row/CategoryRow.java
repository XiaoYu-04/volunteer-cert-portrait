package com.vcp.volunteer.mapper.row;

import lombok.Data;

import java.io.Serializable;

/**
 * 活动分类查询结果，比实体多一个 activityCount（该分类下的活动场次）。
 *
 * <p>status 保持库里的 SMALLINT 原值（1 / 0），翻译成 ACTIVE / DISABLED 是 VO 层的事。
 */
@Data
public class CategoryRow implements Serializable {

    private Long id;

    private String categoryName;

    private String code;

    private Integer sort;

    private Integer status;

    /** 该分类下的活动场次 */
    private Integer activityCount;
}
