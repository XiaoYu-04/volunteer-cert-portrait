package com.vcp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 高校志愿服务时长认证与公益画像数据分析系统 - 启动类。
 *
 * <p>全工程唯一的可执行入口，对应 vcp-boot/pom.xml 中
 * spring-boot-maven-plugin 的 mainClass 配置。
 */
@SpringBootApplication
public class VcpApplication {

    public static void main(String[] args) {
        SpringApplication.run(VcpApplication.class, args);
    }
}