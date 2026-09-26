package com.vcp.framework.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;

import static com.vcp.framework.util.UploadPathUtils.normalizeUploadPrefix;

/**
 * Web MVC 配置：目前只负责跨域。
 *
 * <p>前后端分离部署，前端 Vite dev server（默认 5173）直连后端 8080，
 * 浏览器会先发 OPTIONS 预检，没有跨域配置则所有接口在浏览器侧直接被拦下。
 *
 * <p>注册后 Spring MVC 会把跨域拦截器插到执行链首位：预检请求在这一步就被应答并中止，
 * 不会继续走到 Sa-Token 的鉴权拦截器，因此未登录状态下的预检也能通过；
 * 普通请求则先由它写入 Access-Control-Allow-Origin 等响应头再进鉴权，
 * 这意味着即使鉴权失败返回 20001，前端也能正常读到响应体并跳登录页。
 *
 * <p><b>允许来源已改由配置项 {@code vcp.cors.allowed-origin-patterns} 控制</b>（逗号分隔可填多个），
 * 配置项缺失时回退为 {@code *}，与改动前写死的取值一致，因此默认（不激活 prod）行为不变。
 * 正式部署由 application-prod.yml 收紧为实际前端域名白名单，见该文件顶部说明。
 * 收紧不影响计划中的部署：方案是 Nginx 同源反代 {@code /api}，
 * 同源请求本就不发预检、不需要 CORS 响应头。
 *
 * <p>Sa-Token 的拦截器注册在 {@code security/SaTokenConfig}，同属 WebMvcConfigurer，
 * 与本类并存、职责互不重叠。
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    /** 允许的跨域来源模式；缺省 {@code *}，与改动前的硬编码取值一致 */
    private final String[] allowedOriginPatterns;

    /** 上传文件根目录，静态资源映射到 /uploads/** */
    private final Path uploadRoot;

    /** 上传资源 URL 匹配模式，跟随 vcp.upload.public-prefix 配置 */
    private final String uploadUrlPattern;

    /**
     * @param allowedOriginPatterns 配置项 {@code vcp.cors.allowed-origin-patterns}，逗号分隔，缺省 {@code *}
     * @throws IllegalStateException 配置值为空（或只有逗号、空白）时抛出，避免退化成无效的跨域配置
     */
    public WebMvcConfig(@Value("${vcp.cors.allowed-origin-patterns:*}") String allowedOriginPatterns,
                        @Value("${vcp.upload.dir:./uploads}") String uploadDir,
                        @Value("${vcp.upload.public-prefix:/uploads}") String uploadPublicPrefix) {
        this.allowedOriginPatterns = splitOriginPatterns(allowedOriginPatterns);
        this.uploadRoot = Paths.get(uploadDir).toAbsolutePath().normalize();
        this.uploadUrlPattern = normalizeUploadPrefix(uploadPublicPrefix) + "/**";
        if (this.allowedOriginPatterns.length == 0) {
            throw new IllegalStateException("配置项 vcp.cors.allowed-origin-patterns 不能为空："
                    + "留空会让 Spring 退回 allowedOrigins=[\"*\"]，与 allowCredentials(true) 冲突，"
                    + "表现为每次跨域请求都抛 IllegalArgumentException。"
                    + "请填入前端域名（逗号分隔），或显式写 \"*\" 表示放开所有来源。");
        }
    }

    /**
     * 注册全局跨域规则。
     *
     * <p>三处细节：
     * <ul>
     *   <li>用 {@code allowedOriginPatterns} 而不是 {@code allowedOrigins}：
     *       后者填 {@code "*"} 时与 {@code allowCredentials(true)} 冲突，
     *       Spring 会在请求进来时抛 IllegalArgumentException。</li>
     *   <li>请求头放开为 {@code "*"}：前端除 {@code Authorization: Bearer xxx} 外，
     *       还固定发送 {@code Content-Type: application/json}，而它不属于 CORS 安全头，
     *       同样要出现在预检的允许列表里，否则业务请求会以跨域失败告终。</li>
     *   <li>来源模式取自配置项，默认 {@code *}；若配置成空串，构造期即失败（见构造函数），
     *       不会带着一份无效的跨域配置把应用起起来。</li>
     * </ul>
     *
     * @param registry 跨域规则注册器
     */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOriginPatterns(allowedOriginPatterns)
                .allowedHeaders("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowCredentials(true);
    }

    /**
     * 把上传目录暴露为只读静态资源。
     *
     * <p><b>2026-09-27 起新图片一律走 DB，不再落盘</b>：活动图片上传后二进制存
     * {@code attachment.file_data}，由 {@code GET /api/v1/attachments/{id}/content}
     * 读取。本映射只为老数据（file_url 仍是 {@code /uploads/...} 的历史行）保留兼容，
     * 不要删；新上传的地址已不再指向这里。
     *
     * <p>开发环境和生产环境都由后端直接提供这些历史图片文件；
     * 正式部署也可以在 Nginx 层直接 alias 该目录以获得更高吞吐。
     *
     * @param registry 静态资源注册器
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String location = uploadRoot.toUri().toString();
        if (!location.endsWith("/")) {
            location += "/";
        }
        registry.addResourceHandler(uploadUrlPattern)
                .addResourceLocations(location)
                .setCachePeriod(3600);
    }

    /**
     * 把逗号分隔的来源模式解析成数组，去掉首尾空白与空项。
     *
     * <p>{@code "*"} → {@code ["*"]}，与改动前的硬编码行为等价；
     * {@code "https://a,https://b"} → {@code ["https://a", "https://b"]}。
     *
     * @param raw 原始配置值
     * @return 来源模式数组，可能为空数组（由构造函数判为非法）
     */
    private static String[] splitOriginPatterns(String raw) {
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(pattern -> !pattern.isEmpty())
                .toArray(String[]::new);
    }
}
