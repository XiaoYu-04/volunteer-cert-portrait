package com.vcp.system.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.vcp.common.enums.AttachmentBizTypeEnum;
import com.vcp.common.exception.BusinessException;
import com.vcp.common.exception.ErrorCodeEnum;
import com.vcp.system.dto.AttachmentSaveDTO;
import com.vcp.system.entity.Attachment;
import com.vcp.system.mapper.AttachmentMapper;
import com.vcp.system.service.AttachmentService;
import com.vcp.system.vo.AttachmentContentVO;
import com.vcp.system.vo.AttachmentVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.vcp.common.util.StringUtils.isBlank;
import static com.vcp.common.util.StringUtils.trimToNull;

/**
 * 附件服务实现：图片本体以 bytea 存数据库，磁盘不再保留图片文件。
 *
 * <p>上传分两步（插入拿自增 id → 回填 file_url），因为 file_url 里带 id，
 * 只有插入后才知道；绑定图片时按 file_url 解析 id 更新既有行，绝不做「先删后插」，
 * 否则删行就等于删掉图片字节。
 */
@Service
@RequiredArgsConstructor
public class AttachmentServiceImpl implements AttachmentService {

    private static final long MAX_IMAGE_SIZE = 5L * 1024 * 1024;

    /** 防止解压炸弹：按宽高计算的上限 */
    private static final long MAX_IMAGE_PIXELS = 25_000_000L;

    /** 内容接口地址的前后缀：上传拼地址与绑定解析地址共用，避免两处格式漂移 */
    private static final String CONTENT_URL_PREFIX = "/api/v1/attachments/";
    private static final String CONTENT_URL_SUFFIX = "/content";

    /** 只认完整的内容接口地址，防止把别的字符串误当成图片引用 */
    private static final Pattern CONTENT_URL_PATTERN =
            Pattern.compile("^/api/v1/attachments/(\\d+)/content$");

    private final AttachmentMapper attachmentMapper;

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

        byte[] data;
        try {
            data = file.getBytes();
        } catch (IOException e) {
            throw new BusinessException(ErrorCodeEnum.SYSTEM_ERROR, "图片读取失败，请稍后重试");
        }

        // 上传成功但活动最终没保存的图片会一直留在库里（biz_id 为空），
        // 每次上传顺带清掉 24 小时前的孤儿，避免只增不减
        attachmentMapper.delete(Wrappers.<Attachment>lambdaQuery()
                .eq(Attachment::getBizType, AttachmentBizTypeEnum.ACTIVITY.getCode())
                .isNull(Attachment::getBizId)
                .lt(Attachment::getCreateTime, LocalDateTime.now().minusHours(24)));

        Attachment attachment = new Attachment();
        attachment.setBizType(AttachmentBizTypeEnum.ACTIVITY.getCode());
        attachment.setFileName(originalName);
        attachment.setFileSize(file.getSize());
        attachment.setContentType(contentTypeOf(extension));
        attachment.setSortOrder(0);
        attachment.setFileData(data);
        attachment.setSha256(sha256Hex(data));
        attachmentMapper.insert(attachment);

        // 自增 id 只有插入后才有，地址里带 id 才能按行定位二进制，所以先插再回填 file_url
        String fileUrl = CONTENT_URL_PREFIX + attachment.getId() + CONTENT_URL_SUFFIX;
        attachmentMapper.update(null, Wrappers.<Attachment>lambdaUpdate()
                .eq(Attachment::getId, attachment.getId())
                .set(Attachment::getFileUrl, fileUrl));

        AttachmentVO vo = new AttachmentVO();
        vo.setId(attachment.getId());
        vo.setFileUrl(fileUrl);
        vo.setFileName(originalName);
        vo.setFileSize(file.getSize());
        vo.setContentType(attachment.getContentType());
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
        if (attachments.size() > 6) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "活动图片最多 6 张");
        }

        // 先解析出本次要保留的附件 id，再按 id 集合删旧行。
        // 不能沿用「先删后插」：图片字节就存在 attachment 行里，删行等于丢图。
        List<Long> keptIds = new ArrayList<>(attachments.size());
        for (AttachmentSaveDTO dto : attachments) {
            if (dto == null || isBlank(dto.getFileUrl())) {
                throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "图片地址不能为空");
            }
            Long id = parseContentId(dto.getFileUrl().trim());
            if (id == null) {
                throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "图片地址不合法");
            }
            // 行必须存在且已存二进制：上传接口才可能产生这样的地址，顺带挡掉伪造的 id
            Long count = attachmentMapper.selectCount(Wrappers.<Attachment>lambdaQuery()
                    .eq(Attachment::getId, id)
                    .isNotNull(Attachment::getFileData));
            if (count == null || count == 0) {
                throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "图片不存在，请重新上传");
            }
            keptIds.add(id);
        }

        // 本次没保留的行删掉（含「清空」场景：保留集合为空即删该业务全部行）；
        // notIn 的条件式写法在集合为空时自动省略，正好等价于全删
        attachmentMapper.delete(Wrappers.<Attachment>lambdaQuery()
                .eq(Attachment::getBizType, bizType)
                .eq(Attachment::getBizId, bizId)
                .notIn(!keptIds.isEmpty(), Attachment::getId, keptIds));

        for (int i = 0; i < keptIds.size(); i++) {
            // 复用既有行：补齐说明、排序与业务归属，把上传后尚未绑定的行绑到本次业务上；
            // 二进制和 sha256 原样保留，重复保存活动不会复制图片字节
            attachmentMapper.update(null, Wrappers.<Attachment>lambdaUpdate()
                    .eq(Attachment::getId, keptIds.get(i))
                    .set(Attachment::getCaption, limit(trimToNull(attachments.get(i).getCaption()), 255))
                    .set(Attachment::getSortOrder, i)
                    .set(Attachment::getBizType, bizType)
                    .set(Attachment::getBizId, bizId));
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

    @Override
    public AttachmentContentVO readContent(Long id) {
        if (id == null) {
            return null;
        }
        Attachment attachment = attachmentMapper.selectContentById(id);
        if (attachment == null || attachment.getFileData() == null) {
            return null;
        }
        return new AttachmentContentVO(attachment.getId(), attachment.getFileName(),
                attachment.getContentType(), attachment.getSha256(), attachment.getFileData());
    }

    /**
     * 从内容接口地址解析附件 id。
     *
     * @param fileUrl 附件地址，形如 {@code /api/v1/attachments/123/content}
     * @return 附件 id；不是内容接口地址时返回 null
     */
    private static Long parseContentId(String fileUrl) {
        Matcher matcher = CONTENT_URL_PATTERN.matcher(fileUrl);
        if (!matcher.matches()) {
            return null;
        }
        try {
            return Long.valueOf(matcher.group(1));
        } catch (NumberFormatException e) {
            // 理论到不了：正则已限定纯数字。位数超出 long 时按非法地址处理
            return null;
        }
    }

    /**
     * 计算内容的 SHA-256（十六进制小写），读取接口用它作 ETag。
     *
     * <p>为什么对内容哈希而不是用文件名/大小拼：地址里的 id 每次上传都不同，
     * 只有内容级哈希才能表达「字节一样就还能用缓存」；SHA-256 碰撞概率可忽略。
     *
     * @param data 图片字节
     * @return 十六进制小写摘要（64 字符）
     */
    private static String sha256Hex(byte[] data) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(data);
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16));
                hex.append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            // JDK 必须内置 SHA-256，算法缺失属于环境损坏，直接失败而不是给出错误摘要
            throw new BusinessException(ErrorCodeEnum.SYSTEM_ERROR, "图片校验值计算失败，请稍后重试");
        }
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

    private static String limit(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
