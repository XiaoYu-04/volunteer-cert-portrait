package com.vcp.framework.util;

/**
 * 上传资源访问前缀归一化工具。
 *
 * <p><b>为什么必须共用同一个函数</b>：同一份 {@code vcp.upload.public-prefix} 配置同时决定
 * 两件事 —— {@code WebMvcConfig} 把上传目录暴露成静态资源的 URL 匹配模式，
 * {@code SaTokenConfig} 把同一前缀加进免登录放行清单。两处各写一份归一化实现，一旦口径漂移
 * （例如一边补前导斜杠、一边不补），就会出现「文件能上传、访问却被 401 拦掉」或
 * 「静态映射与数据库里存的地址对不上」这类跨配置不一致。
 *
 * <p>归一化口径（与合并前 WebMvcConfig / SaTokenConfig 的私有实现逐字一致，默认值不变）：
 * 空白（null / 空串 / 全空白）回退为 {@code /uploads}；非空则去首尾空白，缺前导斜杠时补上，
 * 并去掉末尾的全部斜杠。
 *
 * <p><b>刻意不做的两件事</b>：
 * <ul>
 *   <li>不校验「归一化后为空」的极端配置（如 {@code "/"}）：与合并前行为一致，
 *       改它会连带改变静态资源映射范围与免登录放行范围；</li>
 *   <li>不兼容「不带前导斜杠」的变体（AttachmentServiceImpl / ActivityServiceImpl 各有一份）：
 *       那些变体与本工具只在配置值缺失前导斜杠时产生差异，属于行为差异，未强行合并。</li>
 * </ul>
 *
 * <p>本类是纯函数工具，不可实例化。
 */
public final class UploadPathUtils {

    /** 上传资源前缀的缺省值，与 vcp.upload.public-prefix 的配置默认值一致 */
    private static final String DEFAULT_UPLOAD_PREFIX = "/uploads";

    private UploadPathUtils() {
    }

    /**
     * 归一化上传资源访问前缀。
     *
     * @param prefix 配置项 {@code vcp.upload.public-prefix} 的原始值，允许为 null
     * @return 去空白、带前导斜杠、无末尾斜杠的前缀；空白值回退为 {@code /uploads}
     */
    public static String normalizeUploadPrefix(String prefix) {
        String value = prefix == null || prefix.isBlank() ? DEFAULT_UPLOAD_PREFIX : prefix.trim();
        if (!value.startsWith("/")) {
            value = "/" + value;
        }
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        return value;
    }
}
