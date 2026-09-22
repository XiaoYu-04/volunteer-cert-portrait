package com.vcp.system.dto;

import com.vcp.common.dto.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 用户列表查询条件（GET /api/v1/system/users）。
 *
 * <p>三个筛选都是可选，前端在"重置"时会把它们置为空串，因此 Service 里
 * 一律用 hasText 判断后再拼条件 —— 空串当作"不筛选"，否则会拼出
 * keyword = '' 这种既筛不到人又让人困惑的条件（见待办 B19）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class UserQuery extends PageQuery {

    /** 关键字，同时匹配用户名与姓名 */
    private String keyword;

    /** 角色码，全等匹配 */
    private String role;

    /** 账号状态，前端口径 ACTIVE / DISABLED */
    private String status;
}
