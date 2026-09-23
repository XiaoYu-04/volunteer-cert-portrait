package com.vcp.certification.service;

import com.vcp.certification.dto.DurationQuery;
import com.vcp.certification.dto.DurationSubmitDTO;
import com.vcp.certification.vo.DurationVO;
import com.vcp.common.result.PageResult;

import java.util.List;

/**
 * 服务时长服务（学生端「我的时长」、组织端「时长提交」、学校端「时长审核」三处共用）。
 *
 * <p><b>数据范围一律由登录态决定</b>：学生只能看自己的记录、组织管理员只能看本组织的记录、
 * 学校管理员看全校。请求里带的 studentId / orgId 只对学校管理员生效，其余角色一律忽略。
 */
public interface DurationService {

    /**
     * 分页查询服务时长记录。
     *
     * @param query 查询条件；keyword 同时匹配学生姓名与活动名称
     * @return 分页结果
     */
    PageResult<DurationVO> listDurations(DurationQuery query);

    /**
     * 取单条服务时长记录。
     *
     * @param id 时长记录 id
     * @return 记录详情
     * @throws com.vcp.common.exception.BusinessException 记录不存在（40001）或超出数据范围（20003）
     */
    DurationVO getDuration(Long id);

    /**
     * 提交服务时长（支持单条与批量）。
     *
     * <p>signup_id 由 studentId + activityId 反查；反查不到、活动不属于本组织、
     * 已通过审核或已在待审核队列中的记录都会被拒绝，并给出可直接展示的具体文案。
     * 驳回后的记录允许重新提交（状态回到 PENDING_AUDIT），每次提交都会写一条
     * SUBMIT 流水。
     *
     * @param dto 提交内容，单条形状或 {@code {items: [...]}} 批量形状
     * @return 本次提交的时长记录 id 列表，顺序与请求明细一致
     * @throws com.vcp.common.exception.BusinessException 参数不合法、学生/活动/报名不存在或越权（10001/10003/30001/30008/20003）
     */
    List<Long> submitDurations(DurationSubmitDTO dto);
}
