package com.vcp.certification.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.vcp.certification.entity.ServiceDuration;
import org.apache.ibatis.annotations.Mapper;

/**
 * 服务时长 Mapper。
 *
 * <p>状态筛选、按 signup_id 反查单条、按状态计数都用 LambdaQueryWrapper 表达，
 * 不需要自定义 SQL；需要跨表拼出前端要的字段（学生姓名/学号/学院、活动名称/类型、
 * 组织名称、审核人姓名）的列表与详情查询在 {@link DurationRefMapper}。
 */
@Mapper
public interface ServiceDurationMapper extends BaseMapper<ServiceDuration> {
}
