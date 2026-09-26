package com.vcp.system.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.vcp.common.exception.BusinessException;
import com.vcp.common.exception.ErrorCodeEnum;
import com.vcp.system.dto.AttachmentSaveDTO;
import com.vcp.system.entity.Attachment;
import com.vcp.system.mapper.AttachmentMapper;
import com.vcp.system.service.AttachmentService;
import com.vcp.system.vo.AttachmentVO;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static com.vcp.common.util.StringUtils.isBlank;
import static com.vcp.common.util.StringUtils.trimToNull;

/**
 * 附件服务实现：文件落盘，数据库只存元数据。
 */
@Service
@RequiredArgsConstructor
public class AttachmentServiceImpl implements AttachmentService {

    private static final long MAX_IMAGE_SIZE = 5L * 1024 * 1024;

    /** 防止解压炸弹：按宽高计算的上限 */
    private static final long MAX_IMAGE_PIXELS = 25_000_000L;

    private static final DateTimeFormatter DATE_PATH = DateTimeFormatter.ofPattern("yyyy/MM");

    private final AttachmentMapper attachmentMapper;

    /** 上传根目录，相对于后端启动目录 */
    @Value("${vcp.upload.dir:./uploads}")
    private String uploadDir;

    /** 对外访问前缀 */
    @Value("${vcp.upload.public-prefix:/uploads}")
    private String publicPrefix;

    @Override
    public AttachmentVO uploadActivityImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "请选择要上传的图片");
        }
        if (file.getSize() > MAX_IMAGE_SIZE) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "图片大小不能超过 5MB");
        }

        String originalName = safeOriginalName(file.getOriginalFilename());
        String extension = detectImageExtension(file);
        if (extension == null) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "仅支持 JPG、PNG 图片");
        }

        Path root = Paths.get(uploadDir).toAbsolutePath().normalize();
        String relative = "activities/" + LocalDate.now().format(DATE_PATH) + "/"
                + UUID.randomUUID().toString().replace("-", "") + "." + extension;
        Path target = root.resolve(relative).normalize();
        if (!target.startsWith(root)) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "图片路径不合法");
        }

        try {
            Files.createDirectories(target.getParent());
            try (InputStream input = file.getInputStream()) {
                Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new BusinessException(ErrorCodeEnum.SYSTEM_ERROR, "图片保存失败，请稍后重试");
        }

        AttachmentVO vo = new AttachmentVO();
        vo.setFileUrl(publicPrefix() + "/" + relative.replace('\\', '/'));
        vo.setFileName(originalName);
        vo.setFileSize(file.getSize());
        vo.setContentType(contentTypeOf(extension));
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void replaceAttachments(String bizType, Long bizId, List<AttachmentSaveDTO> attachments) {
        if (bizType == null || bizType.isBlank() || bizId == null) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "附件关联信息不完整");
        }
        if (attachments == null) {
            return;
        }
        attachmentMapper.delete(Wrappers.<Attachment>lambdaQuery()
                .eq(Attachment::getBizType, bizType)
                .eq(Attachment::getBizId, bizId));
        if (attachments.isEmpty()) {
            return;
        }
        if (attachments.size() > 6) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "活动图片最多 6 张");
        }

        int sortOrder = 0;
        for (AttachmentSaveDTO dto : attachments) {
            if (dto == null || isBlank(dto.getFileUrl())) {
                throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "图片地址不能为空");
            }
            String fileUrl = dto.getFileUrl().trim();
            if (!fileUrl.startsWith(publicPrefix() + "/") || fileUrl.length() > 255) {
                throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "图片地址不合法");
            }
            Path root = Paths.get(uploadDir).toAbsolutePath().normalize();
            String relative = fileUrl.substring(publicPrefix().length() + 1);
            Path storedFile = root.resolve(relative).normalize();
            if (!storedFile.startsWith(root) || !Files.isRegularFile(storedFile)) {
                throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "图片文件不存在，请重新上传");
            }

            Attachment attachment = new Attachment();
            attachment.setBizType(bizType);
            attachment.setBizId(bizId);
            attachment.setFileUrl(fileUrl);
            attachment.setFileName(limit(trimToNull(dto.getFileName()), 255));
            attachment.setFileSize(dto.getFileSize() == null || dto.getFileSize() < 0 ? 0L : dto.getFileSize());
            attachment.setContentType(limit(trimToNull(dto.getContentType()), 100));
            attachment.setCaption(limit(trimToNull(dto.getCaption()), 255));
            attachment.setSortOrder(sortOrder++);
            attachmentMapper.insert(attachment);
        }
    }

    @Override
    public List<AttachmentVO> listAttachments(String bizType, Long bizId) {
        if (bizType == null || bizType.isBlank() || bizId == null) {
            return List.of();
        }
        List<Attachment> rows = attachmentMapper.selectList(Wrappers.<Attachment>lambdaQuery()
                .eq(Attachment::getBizType, bizType)
                .eq(Attachment::getBizId, bizId)
                .orderByAsc(Attachment::getSortOrder)
                .orderByAsc(Attachment::getId));
        List<AttachmentVO> result = new ArrayList<>(rows.size());
        for (Attachment row : rows) {
            AttachmentVO vo = new AttachmentVO();
            vo.setId(row.getId());
            vo.setFileUrl(row.getFileUrl());
            vo.setFileName(row.getFileName());
            vo.setFileSize(row.getFileSize());
            vo.setContentType(row.getContentType());
            vo.setCaption(row.getCaption());
            vo.setSortOrder(row.getSortOrder());
            result.add(vo);
        }
        return result;
    }

    /**
     * 通过文件头识别真实图片类型，不能只信扩展名或浏览器传来的 Content-Type。
     *
     * @param file 上传文件
     * @return 识别出的扩展名；不是支持格式时返回 null
     */
    private static String detectImageExtension(MultipartFile file) {
        try (InputStream source = file.getInputStream();
             ImageInputStream input = ImageIO.createImageInputStream(source)) {
            if (input == null) {
                return null;
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                return null;
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width <= 0 || height <= 0) {
                    return null;
                }
                if ((long) width * height > MAX_IMAGE_PIXELS) {
                    throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "图片分辨率过高，请压缩后再上传");
                }
                String format = reader.getFormatName().toLowerCase(Locale.ROOT);
                if (format.contains("jpeg") || format.equals("jpg")) {
                    return "jpg";
                }
                if (format.equals("png")) {
                    return "png";
                }
                return null;
            } finally {
                reader.dispose();
            }
        } catch (IOException e) {
            return null;
        }
    }

    private static String safeOriginalName(String originalName) {
        String name = trimToNull(originalName);
        if (name == null) {
            return "image";
        }
        name = name.replace('\\', '/');
        int slash = name.lastIndexOf('/');
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        return limit(name, 255);
    }

    private static String contentTypeOf(String extension) {
        return switch (extension) {
            case "jpg" -> "image/jpeg";
            case "png" -> "image/png";
            default -> "application/octet-stream";
        };
    }

    private String publicPrefix() {
        String value = trimToNull(publicPrefix);
        if (value == null) {
            return "/uploads";
        }
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        return value;
    }

    private static String limit(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
