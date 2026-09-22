package com.vcp.framework.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 接口文档（OpenAPI）元信息配置。
 *
 * <p>Knife4j 与 springdoc 共用同一份 OpenAPI 描述，这里只覆盖文档标题、描述和版本。
 * springdoc 的 SpringDocConfiguration#openAPIBuilder 以 Optional&lt;OpenAPI&gt; 注入本 Bean
 * 作为文档基底；若不声明，文档头会回退为默认的 "OpenAPI definition / v0"。
 *
 * <p>Knife4j 的增强能力（含 /knife4j/config 接口）受 knife4j.enable 开关控制，
 * 需在 application.yml 中显式设为 true，本类不负责该开关。
 *
 * <p>访问入口：/doc.html（Knife4j UI）；原始描述文档：/v3/api-docs。
 *
 * <p>鉴权声明：额外声明了一个全局的 HTTP Bearer 安全方案，使 /doc.html 右上角的
 * 「Authorize」可以填入 token，之后在文档页调试受保护接口会自动带上
 * {@code Authorization: Bearer <token>} 请求头。该声明<b>只是文档元信息</b>，
 * 让文档页知道该往哪个请求头塞 token；真正的登录校验仍由 Sa-Token 拦截器完成。
 */
@Configuration
public class OpenApiConfig {

    /** 鉴权方案名：文档页的「Authorize」入口与各接口上的小锁图标都以此名关联，不要随意改动 */
    private static final String SECURITY_SCHEME_NAME = "Bearer";

    /**
     * 声明全局 OpenAPI 元信息，供 Knife4j 与 springdoc 共同使用。
     *
     * @return OpenAPI 文档基底对象
     */
    @Bean
    public OpenAPI vcpOpenAPI() {
        // 鉴权方案：HTTP Bearer。与 application.yml 的 sa-token.token-name=Authorization +
        // token-prefix=Bearer 对应 —— 文档页发出的 Authorization: Bearer <token> 正是
        // Sa-Token 能解析的形态，两边对不上则填了 token 也仍然 401。
        // bearerFormat 仅作文档提示：Sa-Token 的 token 实际是 uuid 风格（token-style: uuid）、
        // 并非真正的 JWT，但 HTTP bearer 的标准写法就是 bearerFormat=JWT，不影响实际校验。
        SecurityScheme bearerScheme = new SecurityScheme()
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .bearerFormat("JWT")
                .description("登录接口返回的 token，填入后将以 Authorization: Bearer <token> 发出");

        // 全局安全要求：所有接口默认带上该方案，文档页因此对每个接口显示小锁。
        // 免登录接口（登录、验证码）由 Sa-Token 拦截器放行，文档上多一个小锁不影响调用。
        SecurityRequirement globalRequirement = new SecurityRequirement().addList(SECURITY_SCHEME_NAME);

        return new OpenAPI().info(new Info()
                .title("高校志愿服务时长认证与公益画像数据分析系统")
                .description("后端接口文档")
                .version("1.0.0"))
                .components(new Components().addSecuritySchemes(SECURITY_SCHEME_NAME, bearerScheme))
                .addSecurityItem(globalRequirement);
    }
}