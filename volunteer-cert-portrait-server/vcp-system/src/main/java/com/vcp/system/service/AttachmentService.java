package com.vcp.system.service;

import com.vcp.system.dto.AttachmentSaveDTO;
import com.vcp.system.vo.AttachmentContentVO;
import com.vcp.system.vo.AttachmentVO;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 附件服务。
 *
 * <p>2026-09-27 起图片本体以 bytea 存 PostgreSQL，磁盘不再保留图片文件；
 * 业务模块通过本接口读写附件，不直接访问 AttachmentMapper。
 */
public interface AttachmentService {

    /**
     * 上传一张活动图片。
     *
     * @param file multipart 文件
     * @return 新入库图片的元数据，fileUrl 指向内容读取接口
     */
    AttachmentVO uploadActivityImage(MultipartFile file);

    /**
     * 整体替换某业务的附件列表。
     *
     * <p>按 fileUrl 解析出附件行：不在本次列表里的行删除，列表里的行更新说明、
     * 排序与业务归属（见实现说明）。null 表示不修改；空列表表示清空。
     * 替换动作在业务事务内执行。
     *
     * @param bizType     业务类型，如 ACTIVITY
     * @param bizId       业务 id
     * @param attachments 新附件列表
     */
    void replaceAttachments(String bizType, Long bizId, List<AttachmentSaveDTO> attachments);

    /**
     * 查询某业务的附件列表，按 sort_order、id 升序。
     *
     * @param bizType 业务类型
     * @param bizId   业务 id
     * @return 附件列表；无数据时返回空列表
     */
    List<AttachmentVO> listAttachments(String bizType, Long bizId);

    /**
     * 读取图片二进制内容。
     *
     * @param id 附件 id
     * @return 图片字节与响应头元数据；行不存在或未存过二进制时返回 null
     */
    AttachmentContentVO readContent(Long id);
}
