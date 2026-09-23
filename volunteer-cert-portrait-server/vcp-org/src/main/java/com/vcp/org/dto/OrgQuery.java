package com.vcp.org.dto;

import com.vcp.common.dto.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 组织列表查询条件。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class OrgQuery extends PageQuery {

    /** 关键字，匹配组织名称 */
    private String keyword;

    /** 资质状态 */
    private String status;

    /** 挂靠学院 */
    private String college;
}
