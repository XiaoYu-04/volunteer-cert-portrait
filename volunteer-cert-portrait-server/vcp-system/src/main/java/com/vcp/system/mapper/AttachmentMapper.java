package com.vcp.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.vcp.system.entity.Attachment;
import org.apache.ibatis.annotations.Mapper;

/**
 * 附件 Mapper。
 *
 * <p>附件行只存元数据；上传（文件落盘与校验）及附件行替换/查询都在
 * {@code com.vcp.system.service.impl.AttachmentServiceImpl}，本接口保持空壳，
 * 只有 BaseMapper 的通用 CRUD。组织资质、用户头像等剩余上传场景见待办 C9
 * （活动图片上传已完成）。
 */
@Mapper
public interface AttachmentMapper extends BaseMapper<Attachment> {
}
