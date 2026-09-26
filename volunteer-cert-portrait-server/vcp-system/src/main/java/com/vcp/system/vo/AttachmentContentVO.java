package com.vcp.system.vo;

/**
 * 图片二进制读取结果：接口层据此拼响应头（Content-Type / ETag / Content-Disposition）。
 *
 * <p>用 record 而不是复用 {@code Attachment} 实体：读取接口只需要「字节 + 元数据」这几项，
 * 独立类型可避免实体上的 {@code fileData} 顺着其它序列化路径漏出去。
 */
public record AttachmentContentVO(Long id, String fileName, String contentType, String sha256, byte[] data) {
}
