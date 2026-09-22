package com.vcp.framework.security;

import cn.dev33.satoken.interceptor.SaInterceptor;
import cn.dev33.satoken.stp.StpUtil;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

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
 *
 * <p><b>文档路径为什么必须放行：</b>拦截器覆盖 {@code /**} 之后，{@code /doc.html} 及其
 * 依赖的静态资源、{@code /v3/api-docs} 的 JSON 描述都会先过登录校验，而浏览器直接打开文档页
 * 时并不带 token，结果就是文档页 401 打不开。这是本项目踩过的坑，四个文档路径缺一不可。
 */
@Configuration
public class SaTokenConfig implements WebMvcConfigurer {

    /**
     * 免登录放行清单。
     *
     * <p>分两类：
     * <ul>
     *   <li>接口文档：{@code /doc.html} 是 Knife4j 文档页，{@code /webjars/**} 是它依赖的
     *       前端静态资源，{@code /v3/api-docs/**} 是 OpenAPI 描述 JSON，
     *       {@code /knife4j/**} 是 Knife4j 的增强接口（如 /knife4j/config）。</li>
     *   <li>认证入口：登录与注册本身不能要求先登录，否则永远拿不到第一个 token。</li>
     * </ul>
     */
    private static final String[] EXCLUDE_PATHS = {
            // ---- 接口文档（缺一即 401，详见类注释）----
            "/doc.html",
            "/webjars/**",
            "/v3/api-docs/**",
            "/knife4j/**",
            // ---- 认证入口 ----
            "/api/v1/auth/login",
            "/api/v1/auth/register",
    };

    /**
     * 注册登录拦截器。
     *
     * @param registry 拦截器注册表，由 Spring MVC 传入
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new SaInterceptor(handle -> StpUtil.checkLogin()))
                .addPathPatterns("/**")
                .excludePathPatterns(EXCLUDE_PATHS);
    }
}