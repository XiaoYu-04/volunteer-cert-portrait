package com.vcp.volunteer.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.vcp.volunteer.dto.ActivityQuery;
import com.vcp.volunteer.entity.VolunteerActivity;
import com.vcp.volunteer.mapper.row.ActivityRow;
import com.vcp.volunteer.mapper.row.OrgOverviewRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 志愿活动 Mapper。
 *
 * <p>列表与详情用 XML（要 JOIN 分类名与组织名，见对应 XML 的说明）；
 * 报名人数的增减用注解 SQL —— 它是并发控制的关键路径，写在方法上比藏在 XML 里更容易被看到。
 */
@Mapper
public interface VolunteerActivityMapper extends BaseMapper<VolunteerActivity> {

    /**
     * 原子占一个名额。
     *
     * <p><b>待办 A3 拍板的口径</b>：不先查后写，直接
     * {@code UPDATE ... WHERE signed_count < max_count}，靠数据库的行锁保证同一活动
     * 的并发报名不会超卖。影响行数为 0 即「名额已满」。
     *
     * <p>{@code max_count <= 0} 视为不限名额（建表脚本的约定），此时不参与比较 ——
     * 若写成 {@code signed_count < max_count}，不限名额的活动会永远报满。
     *
     * @param id 活动 id
     * @return 影响行数：1 表示占位成功，0 表示已满或活动不存在
     */
    @Update("UPDATE volunteer_activity SET signed_count = signed_count + 1, update_time = NOW() "
            + "WHERE id = #{id} AND deleted = 0 AND (max_count <= 0 OR signed_count < max_count)")
    int increaseSignedCount(@Param("id") Long id);

    /**
     * 释放一个名额（报名被驳回或学生取消报名时调用）。
     *
     * <p>用 GREATEST(..., 0) 兜底：计数是冗余列，历史上若有漂移，
     * 宁可停在 0 也不要出现负数 —— 负数会让「已报名 46 / 60」显示成 -1。
     *
     * @param id 活动 id
     * @return 影响行数
     */
    @Update("UPDATE volunteer_activity SET signed_count = GREATEST(signed_count - 1, 0), update_time = NOW() "
            + "WHERE id = #{id} AND deleted = 0")
    int decreaseSignedCount(@Param("id") Long id);

    /**
     * 分页查询活动。
     *
     * @param page        分页对象，由 PageUtils.toPage 构造
     * @param query       筛选条件
     * @param scopeOrgId  数据范围：非空时只返回该组织的活动（组织管理员）
     * @param excludeDraft 是否排除草稿（学生端可见范围）
     * @return 分页结果
     */
    IPage<ActivityRow> selectActivityPage(IPage<ActivityRow> page,
                                          @Param("q") ActivityQuery query,
                                          @Param("scopeOrgId") Long scopeOrgId,
                                          @Param("excludeDraft") boolean excludeDraft);

    /**
     * 取活动详情。
     *
     * @param id 活动 id
     * @return 活动行；不存在或已删除时返回 null
     */
    ActivityRow selectActivityDetail(@Param("id") Long id);

    /**
     * 取组织端概览的聚合计数。
     *
     * @param orgId 组织 id
     * @return 各计数；组织没有活动时各项为 0
     */
    OrgOverviewRow selectOrgOverview(@Param("orgId") Long orgId);
}
