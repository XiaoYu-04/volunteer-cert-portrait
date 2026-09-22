package com.vcp.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.vcp.system.entity.Notification;
import org.apache.ibatis.annotations.Mapper;

/**
 * 通知 Mapper。
 *
 * <p>列表查询要按「置顶优先、时间倒序」排序，用 LambdaQueryWrapper 的
 * orderByDesc 表达即可。删除群发公告时按 batch_no 批量删，
 * 用 MyBatis-Plus 的 LambdaUpdateWrapper 或 delete(wrapper) 完成，不需要自定义 SQL。
 */
@Mapper
public interface NotificationMapper extends BaseMapper<Notification> {
}
