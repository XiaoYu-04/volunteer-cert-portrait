package com.vcp.system.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 附件内容接口地址的构造与解析。
 *
 * <p><b>为什么单独抽一个工具类</b>：图片自 2026-09-27 起存进数据库（{@code attachment.file_data}），
 * 封面与图文说明的地址都变成 {@code /api/v1/attachments/{id}/content}。这套地址的
 * 「生成」在 {@code AttachmentServiceImpl}（上传后回写 file_url），「校验」却在两个地方 ——
 * 图文说明在 {@code AttachmentServiceImpl.replaceAttachments}、封面在
 * {@code ActivityServiceImpl.normalizeCover}。两边各写一份正则的结果就是：改造时只改了前者，
 * 后者仍按旧的 {@code /uploads/} 前缀校验，于是「图文说明能存、封面报『封面地址不合法』」
 * （真机实测踩到）。收敛到这里，只留一份事实来源。
 *
 * <p>只认这一种形状，不接受任意字符串：地址是前端回传的，必须挡掉伪造与误填。
 */
public final class AttachmentUrls {

    /** 内容接口路径前缀。 */
    public static final String CONTENT_URL_PREFIX = "/api/v1/attachments/";

    /** 内容接口路径后缀。 */
    public static final String CONTENT_URL_SUFFIX = "/content";

    /** 严格匹配 {@code /api/v1/attachments/{id}/content}（id 为纯数字）。 */
    private static final Pattern CONTENT_URL_PATTERN =
            Pattern.compile("^/api/v1/attachments/(\\d+)/content$");

    private AttachmentUrls() {
    }

    /**
     * 拼出某附件的读取地址。
     *
     * @param attachmentId 附件 id
     * @return 形如 {@code /api/v1/attachments/123/content}
     */
    public static String contentUrl(Long attachmentId) {
        return CONTENT_URL_PREFIX + attachmentId + CONTENT_URL_SUFFIX;
    }

    /**
     * 从内容接口地址解析附件 id。
     *
     * @param fileUrl 待解析地址；可为 null
     * @return 附件 id；不是合法内容接口地址时返回 null
     */
    public static Long parseContentId(String fileUrl) {
        if (fileUrl == null) {
            return null;
        }
        Matcher matcher = CONTENT_URL_PATTERN.matcher(fileUrl.trim());
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
     * 是否是本系统生成的内容接口地址。
     *
     * @param fileUrl 待判断地址；可为 null
     * @return true 表示形如 {@code /api/v1/attachments/{id}/content}
     */
    public static boolean isContentUrl(String fileUrl) {
        return parseContentId(fileUrl) != null;
    }
}
