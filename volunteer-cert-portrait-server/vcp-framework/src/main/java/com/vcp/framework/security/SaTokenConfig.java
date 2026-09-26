package com.vcp.framework.security;

import cn.dev33.satoken.interceptor.SaInterceptor;
import cn.dev33.satoken.stp.StpUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import static com.vcp.framework.util.UploadPathUtils.normalizeUploadPrefix;

/**
 * Sa-Token 登录拦截配置：全局校验登录态。
 *
 * <p>注册 Sa-Token 的 {@link SaInterceptor} 并覆盖 {@code /**}，即除放行清单外的所有接口
 * 都必须携带有效 token（请求头 {@code Authorization: Bearer xxx}，见 application.yml），
 * 否则 {@link StpUtil#checkLogin()} 抛出 NotLoginException，由全局异常处理器转成
 * {@code R.fail(20001)}。业务代码因此可以放心地认为「进入 Controller 时已登录」。
 *
 * <p>该拦截器同时保留了 Sa-Token 的注解鉴权能力，后续需要细粒度控制时可直接在方法上写
 * {@code @SaCheckRole} / {@code @SaCheckPermission}，无需再改本类。
 *
 * <p><b>新增放行路径请加到下面的 {@link #EXCLUDE_PATHS} 数组</b>，不要在本类里另起一段
 * exclude 调用——放行清单集中在一处，评审和排查时一眼能看全。
 * 唯一例外是接口文档路径（{@link #DOC_EXCLUDE_PATHS}），它随 {@code knife4j.enable} 联动，见下条。
 *
 * <p><b>文档路径为什么必须放行：</b>拦截器覆盖 {@code /**} 之后，{@code /doc.html} 及其
 * 依赖的静态资源、{@code /v3/api-docs} 的 JSON 描述都会先过登录校验，而浏览器直接打开文档页
 * 时并不带 token，结果就是文档页 401 打不开。这是本项目踩过的坑，四个文档路径缺一不可。
 *
 * <p><b>文档路径已改为与 {@code knife4j.enable} 同一个开关（上线收紧）</b>：
 * 开关为 {@code true} 时四个文档路径照旧并入放行清单（开发期行为不变）；
 * 为 {@code false} 时不再放行，文档页与 OpenAPI JSON 一律要求登录态。
 * 缺省取 {@code false}，与 Knife4j 自身「该属性默认 false 且没有 matchIfMissing」的约定一致，
 * 即本项缺省就是收紧态，不会因为漏配而误开放。
 */
@Configuration
public class SaTokenConfig implements WebMvcConfigurer {

    /**
     * 接口文档放行清单：仅在 {@code knife4j.enable=true} 时并入放行路径，关闭时不放行。
     *
     * <p>{@code /doc.html} 是 Knife4j 文档页，{@code /webjars/**} 是它依赖的
     * 前端静态资源，{@code /v3/api-docs/**} 是 OpenAPI 描述 JSON，
     * {@code /knife4j/**} 是 Knife4j 的增强接口（如 /knife4j/config）。四项缺一即 401。
     */
    private static final String[] DOC_EXCLUDE_PATHS = {
            "/doc.html",
            "/webjars/**",
            "/v3/api-docs/**",
            "/knife4j/**",
    };

    /**
     * 免登录放行清单：认证入口，任何 profile 下都放行。
     *
     * <p>登录与注册本身不能要求先登录，否则永远拿不到第一个 token。
     *
     * <p>学院下拉同样放行：它是注册页的学院选项，而注册页打开时还没有登录态，
     * 走不了被拦截的 {@code /api/v1/system/dicts}。放行后能拿到的只有 sys_dict 里
     * college 类型的中文标签，不含用户数据；注册时对学院的校验仍在后端做，
     * 不受这里放行影响。
     */
    private static final String[] EXCLUDE_PATHS = {
            "/api/v1/auth/login",
            "/api/v1/auth/register",
            "/api/v1/auth/colleges",
    };

    /** 接口文档开关，对应配置项 {@code knife4j.enable}；缺省 false，与 Knife4j 约定一致 */
    private final boolean knife4jEnabled;

    /** 上传资源 URL 匹配模式，跟随 vcp.upload.public-prefix 配置 */
    private final String uploadUrlPattern;

    /**
     * @param knife4jEnabled 配置项 {@code knife4j.enable}，缺省 {@code false}
     */
    public SaTokenConfig(@Value("${knife4j.enable:false}") boolean knife4jEnabled,
                         @Value("${vcp.upload.public-prefix:/uploads}") String uploadPublicPrefix) {
        this.knife4jEnabled = knife4jEnabled;
        this.uploadUrlPattern = normalizeUploadPrefix(uploadPublicPrefix) + "/**";
    }

    /**
     * 注册登录拦截器。
     *
     * @param registry 拦截器注册表，由 Spring MVC 传入
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new SaInterceptor(handle -> StpUtil.checkLogin()))
                .addPathPatterns("/**")
                .excludePathPatterns(excludePaths());
    }

    /**
     * 组装最终放行清单。
     *
     * <p>开关开启时返回「文档路径 + 认证入口」，与改动前写死的清单逐项一致；
     * 关闭时只返回认证入口，文档路径回到登录校验范围内。
     *
     * @return 放行路径数组
     */
    private String[] excludePaths() {
        int docCount = knife4jEnabled ? DOC_EXCLUDE_PATHS.length : 0;
        String[] paths = new String[docCount + EXCLUDE_PATHS.length + 1];
        if (knife4jEnabled) {
            System.arraycopy(DOC_EXCLUDE_PATHS, 0, paths, 0, DOC_EXCLUDE_PATHS.length);
        }
        System.arraycopy(EXCLUDE_PATHS, 0, paths, docCount, EXCLUDE_PATHS.length);
        paths[paths.length - 1] = uploadUrlPattern;
        return paths;
    }
}
