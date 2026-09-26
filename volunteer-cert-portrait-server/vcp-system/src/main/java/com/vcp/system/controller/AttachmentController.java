package com.vcp.system.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaMode;
import com.vcp.common.result.R;
import com.vcp.framework.log.OperationLog;
import com.vcp.system.service.AttachmentService;
import com.vcp.system.vo.AttachmentContentVO;
import com.vcp.system.vo.AttachmentVO;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 附件上传与图片内容读取接口。
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

    /**
     * 读取图片二进制内容（免登录，见 SaTokenConfig 放行清单）。
     *
     * <p>刻意返回原生 HTTP 响应而不是 {@link R} 外壳：浏览器 {@code <img>}
     * 会把该地址当图片直接加载，需要真实的 Content-Type、ETag 与 304，
     * 包一层 JSON 就用不了。
     *
     * @param id          附件 id
     * @param ifNoneMatch 浏览器的 If-None-Match 请求头，可为空
     * @return 200 图片字节；内容未变化时 304；行不存在或未存二进制时 404
     */
    @GetMapping("/{id}/content")
    public ResponseEntity<byte[]> readContent(
            @PathVariable Long id,
            @RequestHeader(value = HttpHeaders.IF_NONE_MATCH, required = false) String ifNoneMatch) {
        AttachmentContentVO content = attachmentService.readContent(id);
        if (content == null) {
            return ResponseEntity.notFound().build();
        }
        String etag = "\"" + content.sha256() + "\"";
        if (etagMatched(ifNoneMatch, etag)) {
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED).eTag(etag).build();
        }
        return ResponseEntity.ok()
                .eTag(etag)
                // 地址含自增 id、内容写入后不可变，可以放心让浏览器缓存一年
                .header(HttpHeaders.CACHE_CONTROL, "public, max-age=31536000, immutable")
                .contentType(resolveMediaType(content.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, contentDispositionOf(content.fileName()))
                .body(content.data());
    }

    /**
     * 解析数据库里存的 MIME 类型。
     *
     * @param contentType 行里的 content_type
     * @return 可用的媒体类型；缺失或非法时退化为二进制流
     */
    private static MediaType resolveMediaType(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
        try {
            return MediaType.parseMediaType(contentType);
        } catch (InvalidMediaTypeException e) {
            // 历史数据的 content_type 可能是任意字符串，解析失败不该让读取接口 500
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }

    /**
     * 拼 Content-Disposition 响应头。
     *
     * @param fileName 原始文件名，可为空
     * @return inline 展示；文件名含中文时由 Spring 自动走 RFC 5987 的 filename* 编码
     */
    private static String contentDispositionOf(String fileName) {
        String name = fileName == null || fileName.isBlank() ? "image" : fileName;
        return ContentDisposition.inline().filename(name).build().toString();
    }

    /**
     * 判断请求头的 If-None-Match 是否命中当前 ETag。
     *
     * <p>兼容四种写法：{@code *}、带引号的强 ETag、{@code W/"..."} 弱 ETag，
     * 以及被代理/客户端去掉引号后的裸 sha256。
     *
     * @param ifNoneMatch 请求头原文，可为空
     * @param etag        当前响应的带引号 ETag
     * @return 命中返回 true（调用方回 304）
     */
    private static boolean etagMatched(String ifNoneMatch, String etag) {
        if (ifNoneMatch == null || ifNoneMatch.isBlank()) {
            return false;
        }
        String value = ifNoneMatch.trim();
        if ("*".equals(value)) {
            return true;
        }
        String weakTag = "W/" + etag;
        String bare = etag.substring(1, etag.length() - 1);
        for (String part : value.split(",")) {
            String candidate = part.trim();
            if (candidate.equals(etag) || candidate.equals(weakTag) || candidate.equals(bare)) {
                return true;
            }
        }
        return false;
    }
}
