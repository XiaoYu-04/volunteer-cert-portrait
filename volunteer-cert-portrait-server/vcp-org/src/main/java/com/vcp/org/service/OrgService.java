package com.vcp.org.service;

import com.vcp.common.result.PageResult;
import com.vcp.org.dto.OrgAuditDTO;
import com.vcp.org.dto.OrgQuery;
import com.vcp.org.dto.OrgStatusDTO;
import com.vcp.org.dto.OrgUpdateDTO;
import com.vcp.org.vo.OrgVO;

/**
 * 志愿组织服务。
 */
public interface OrgService {

    /**
     * 学校管理员分页查询组织。
     *
     * @param query 查询条件
     * @return 组织分页结果
     */
    PageResult<OrgVO> listOrgs(OrgQuery query);

    /**
     * 查询组织详情。
     *
     * <p>学校管理员可查任意组织；组织管理员只能查自己的组织。</p>
     *
     * @param id 组织 id
     * @return 组织详情
     */
    OrgVO getOrg(Long id);

    /**
     * 更新组织资料。
     *
     * <p>组织管理员只能更新自己的组织，学校管理员可维护任意组织。</p>
     *
     * @param id  组织 id
     * @param dto 待更新字段
     */
    void updateOrg(Long id, OrgUpdateDTO dto);

    /**
     * 审核组织资质。
     *
     * @param id  组织 id
     * @param dto 审核动作与备注
     */
    void auditOrg(Long id, OrgAuditDTO dto);

    /**
     * 启用或停用已通过审核的组织。
     *
     * @param id  组织 id
     * @param dto 目标状态
     */
    void updateStatus(Long id, OrgStatusDTO dto);
}
