package com.vcp.portrait.support;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 开启 Spring 定时任务支持。
 *
 * <p>Spring Boot 不会因为出现了 {@code @Scheduled} 就自动开启调度 —— 没有
 * {@code @EnableScheduling} 时定时方法<b>静默不执行</b>，不报错、不打日志，
 * 排查起来只能靠"画像怎么一直不更新"这种业务现象反推。
 * 因此把开关与任务放在同一个模块里（{@link PortraitRecomputeJob}），
 * 别处再要加定时任务时不必重复开这个开关。
 */
@Configuration
@EnableScheduling
public class PortraitSchedulingConfig {
}
