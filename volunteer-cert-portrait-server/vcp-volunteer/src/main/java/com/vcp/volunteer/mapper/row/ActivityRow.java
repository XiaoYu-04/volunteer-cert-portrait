package com.vcp.volunteer.mapper.row;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 活动列表 / 详情查询结果。
 *
 * <p>比实体多出两个跨域字段：type（activity_category.category_name）与 orgName（org_info.org_name）。
 * 活动列表要按分类名筛选、要显示发布组织，这两项只能 JOIN 出来 ——
 * 详见 VolunteerActivityMapper.xml 顶部的说明。
 */
@Data
public class ActivityRow implements Serializable {

    private Long id;

    private String title;

    /** 分类名称 */
    private String type;

    private LocalDateTime startTime;

    private String location;

    private Integer maxCount;

    private Integer signedCount;

    private BigDecimal duration;

    private String status;

    private Long categoryId;

    private Long orgId;

    /** 发布组织名称 */
    private String orgName;

    private LocalDateTime deadline;

    private String contact;

    private String description;
}
