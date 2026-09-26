package com.vcp.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.vcp.system.entity.Attachment;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 附件 Mapper。
 *
 * <p>附件行的插入、替换与查询都在
 * {@code com.vcp.system.service.impl.AttachmentServiceImpl}，本接口只有 BaseMapper
 * 的通用 CRUD，外加一个按 id 单查图片二进制的方法。组织资质、用户头像等剩余
 * 上传场景见待办 C9（活动图片上传已完成）。
 */
@Mapper
public interface AttachmentMapper extends BaseMapper<Attachment> {

    /**
     * 按 id 读取图片二进制及响应头所需的元数据。
     *
     * <p>为什么不复用 {@code selectById}：实体的 {@code fileData} 标了
     * {@code select = false}，通用查询不会带出 bytea 大字段；只有本方法显式列出
     * {@code file_data}，保证「取图片字节」这条路径唯一且可控。
     *
     * @param id 附件 id
     * @return 附件行；不存在时返回 null
     */
    @Select("SELECT id, file_name, content_type, sha256, file_data FROM attachment WHERE id = #{id}")
    Attachment selectContentById(@Param("id") Long id);
}
