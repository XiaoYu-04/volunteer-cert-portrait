package com.vcp.framework.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

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
 * <p><b>开发期放开所有来源，上线前必须收紧。</b>
 * 当前允许任意来源且允许携带凭证，等同于对全网开放；
 * 正式部署时应把 {@code allowedOriginPatterns} 改成实际的前端域名白名单
 * （如 {@code https://vcp.example.edu.cn}），并按需收窄允许的方法与请求头。
 *
 * <p>Sa-Token 的拦截器注册在 {@code security/SaTokenConfig}，同属 WebMvcConfigurer，
 * 与本类并存、职责互不重叠。
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    /**
     * 注册全局跨域规则。
     *
     * <p>两处细节：
     * <ul>
     *   <li>用 {@code allowedOriginPatterns} 而不是 {@code allowedOrigins}：
     *       后者填 {@code "*"} 时与 {@code allowCredentials(true)} 冲突，
     *       Spring 会在请求进来时抛 IllegalArgumentException。</li>
     *   <li>请求头放开为 {@code "*"}：前端除 {@code Authorization: Bearer xxx} 外，
     *       还固定发送 {@code Content-Type: application/json}，而它不属于 CORS 安全头，
     *       同样要出现在预检的允许列表里，否则业务请求会以跨域失败告终。</li>
     * </ul>
     *
     * @param registry 跨域规则注册器
     */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOriginPatterns("*")
                .allowedHeaders("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowCredentials(true);
    }
}