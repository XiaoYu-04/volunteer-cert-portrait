package com.vcp.framework.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
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
 */
@Configuration
public class OpenApiConfig {

    /**
     * 声明全局 OpenAPI 元信息，供 Knife4j 与 springdoc 共同使用。
     *
     * @return OpenAPI 文档基底对象
     */
    @Bean
    public OpenAPI vcpOpenAPI() {
        return new OpenAPI().info(new Info()
                .title("高校志愿服务时长认证与公益画像数据分析系统")
                .description("后端接口文档")
                .version("1.0.0"));
    }
}