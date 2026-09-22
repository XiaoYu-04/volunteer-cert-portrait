package com.vcp.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.vcp.system.entity.Attachment;
import org.apache.ibatis.annotations.Mapper;

/**
 * 附件 Mapper。
 *
 * <p>本轮只落库与查询，不实现文件上传下载 —— 前端还没有上传组件
 * （见待办 C9），没有组件就无从验证，先不做。
 */
@Mapper
public interface AttachmentMapper extends BaseMapper<Attachment> {
}
