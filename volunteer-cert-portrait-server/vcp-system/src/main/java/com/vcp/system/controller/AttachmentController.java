package com.vcp.system.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaMode;
import com.vcp.common.result.R;
import com.vcp.framework.log.OperationLog;
import com.vcp.system.service.AttachmentService;
import com.vcp.system.vo.AttachmentVO;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 附件上传接口。
 */
@RestController
@RequestMapping("/api/v1/attachments")
@RequiredArgsConstructor
public class AttachmentController {

    private final AttachmentService attachmentService;

    /**
     * 上传一张活动图片。
     *
     * @param file 图片文件
     * @return 文件访问地址与元数据
     */
    @PostMapping(value = "/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @SaCheckPermission(value = {"volunteer:activity:create", "volunteer:activity:update"}, mode = SaMode.OR)
    @OperationLog(module = "附件", action = "上传活动图片", params = false)
    public R<AttachmentVO> uploadImage(@RequestParam("file") MultipartFile file) {
        return R.ok(attachmentService.uploadActivityImage(file));
    }
}
