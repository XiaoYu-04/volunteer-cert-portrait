package com.vcp.volunteer.service;

import com.vcp.common.result.PageResult;
import com.vcp.volunteer.dto.ActivityQuery;
import com.vcp.volunteer.dto.ActivitySaveDTO;
import com.vcp.volunteer.dto.ActivityStatusDTO;
import com.vcp.volunteer.vo.ActivityVO;
import com.vcp.volunteer.vo.OrgOverviewVO;

/**
 * 志愿活动服务。
 *
 * <p><b>数据范围一律由登录态决定</b>（见待办 B19）：学生只看得到非草稿的活动，
 * 组织管理员只看得到本组织的活动，学校管理员可看全量。前端传来的 orgId
 * 在组织管理员下会被登录态覆盖，不能作为数据范围的依据。
 */
public interface ActivityService {

    /**
     * 分页查询活动。
     *
     * @param query 筛选条件（keyword / type / status / orgId / onlyOpen）
     * @return 活动分页结果
     */
    PageResult<ActivityVO> listActivities(ActivityQuery query);

    /**
     * 取活动详情。
     *
     * <p>草稿对越权者按「活动不存在」处理，不返回 20003：前端把 20003 当成登录失效
     * 会清 token 跳登录，用它表达数据范围只会把人踢下线。
     *
     * @param id 活动 id
     * @return 活动详情
     * @throws com.vcp.common.exception.BusinessException 活动不存在或不在当前用户可见范围内（30001）
     */
    ActivityVO getActivity(Long id);

    /**
     * 新建活动，落库为草稿。
     *
     * <p>发布组织取登录态里的 orgId，前端传的 orgId / org 一律忽略；
     * 结束时间由「开始时间 + 单人服务时长」推导，不接受前端传值。
     *
     * @param dto 活动内容
     * @return 新活动 id
     * @throws com.vcp.common.exception.BusinessException 必填项缺失或格式不合法（10001）
     */
    Long createActivity(ActivitySaveDTO dto);

    /**
     * 修改活动（传了就更新）。
     *
     * @param id  活动 id
     * @param dto 待更新字段
     * @throws com.vcp.common.exception.BusinessException 活动不存在（30001）、已取消的活动不可修改（10001）
     */
    void updateActivity(Long id, ActivitySaveDTO dto);

    /**
     * 活动状态流转：草稿到已发布、已发布到已结束、草稿或已发布到已取消。
     *
     * <p>转为「已结束」时顺带把确实到场（有签到记录）的报名置为 COMPLETED，
     * 缺勤的报名保持 APPROVED，免得把没到场的学生也算进画像的参与次数。
     *
     * @param id  活动 id
     * @param dto 目标状态
     * @throws com.vcp.common.exception.BusinessException 活动不存在（30001）、流转不允许（10001）
     */
    void updateStatus(Long id, ActivityStatusDTO dto);

    /**
     * 组织端数据概览：组织资料 + 六个指标卡。
     *
     * @param orgId 组织 id；组织管理员下该参数被登录态覆盖，学校管理员必须传
     * @return 概览数据
     * @throws com.vcp.common.exception.BusinessException 组织不存在（10005）、角色无权查看（20003）
     */
    OrgOverviewVO getOrgOverview(Long orgId);
}
