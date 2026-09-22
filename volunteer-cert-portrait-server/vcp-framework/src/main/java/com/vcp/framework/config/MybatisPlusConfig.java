package com.vcp.framework.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus 插件配置：注册分页插件。
 *
 * <p><b>为什么必须显式注册这个 Bean：</b>MyBatis-Plus 3.5.9 起把分页能力从
 * mybatis-plus-extension 拆到了独立的 mybatis-plus-jsqlparser（本模块 pom 已引入该依赖，
 * PaginationInnerInterceptor 就在这个 artifact 里）。但"引了依赖"不等于"分页生效"——
 * 若没有把 PaginationInnerInterceptor 加进 MybatisPlusInterceptor 并注册成 Bean，
 * 分页查询不会真正下推到 SQL：MP 会先把全表查出来，再在内存里按页截取。
 * 数据量小的时候完全看不出问题，量一上来就是全表扫描 + OOM，属于典型的静默失效。
 *
 * <p><b>为什么是 POSTGRE_SQL：</b>分页方言必须与真实数据库一致。本项目用 PostgreSQL 18.6，
 * 其分页语法是 {@code LIMIT ? OFFSET ?}，与 MySQL 的 {@code LIMIT ?, ?} 不同；
 * 方言写错会生成非法 SQL。
 *
 * <p><b>为什么刻意不调 setMaxLimit：</b>该拦截器的 maxLimit 默认为 null（不限制单页条数）。
 * 前端若干页面会主动传 {@code pageSize=500}（如"我的时长"汇总、时长提交候选名单），
 * 一旦在这里设一个偏小的上限，这些页面会被静默截断成前 N 条，表现为"数据缺了几条"，
 * 很难定位。因此保持默认不限制，单页条数的约束交给各接口按业务自行把关。
 *
 * <p>注意：本类只负责插件装配；实体上的 {@code @TableField(fill = ...)} 由
 * {@link com.vcp.framework.mybatis.VcpMetaObjectHandler} 消费，两者配合才完整。
 */
@Configuration
public class MybatisPlusConfig {

    /**
     * 声明 MyBatis-Plus 主拦截器，并挂载分页内部拦截器。
     *
     * @return 已装配分页能力的 MyBatis-Plus 拦截器
     */
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.POSTGRE_SQL));
        return interceptor;
    }
}