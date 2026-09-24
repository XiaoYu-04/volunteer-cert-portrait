package com.vcp.system.service;

import com.vcp.system.dto.AttachmentSaveDTO;
import com.vcp.system.vo.AttachmentVO;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 附件服务。
 *
 * <p>文件本体落盘，PostgreSQL 只保存访问地址与元数据。业务模块通过本接口
 * 读写附件，不直接访问 AttachmentMapper。
 */
public interface AttachmentService {

    /**
     * 上传一张活动图片。
     *
     * @param file multipart 文件
     * @return 已落盘文件的元数据
     */
    AttachmentVO uploadActivityImage(MultipartFile file);

    /**
     * 整体替换某业务的附件列表。
     *
     * <p>null 表示不修改；空列表表示清空。替换动作在业务事务内执行。
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
}
