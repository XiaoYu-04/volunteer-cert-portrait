package com.vcp.framework.util;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.vcp.common.result.PageResult;

import java.util.List;
import java.util.function.Function;

/**
 * MyBatis-Plus 分页对象到 {@link PageResult} 的转换工具。
 *
 * <p>为什么单独有这一层：{@code PageResult} 定义在 vcp-common，
 * 而 vcp-common 刻意不引入 ORM 依赖（只放纯数据结构，任何模块都能引用），
 * 所以「{@code IPage} → {@code PageResult}」这段必然依赖 MyBatis-Plus 的转换
 * 只能落在 vcp-framework。各业务模块的 Service 调这里即可，
 * 不必每个分页接口都手写一遍 {@code setTotal/setList}。
 *
 * <p>带 mapper 的重载用于「查出实体、返回 VO」的常见场景，
 * 避免在 Service 里再套一层 stream 转换。
 *
 * <p>本类是纯函数工具，不可实例化。
 */
public final class PageUtils {

    private PageUtils() {
    }

    /**
     * 直接转换：查询结果与返回给前端的类型一致时使用。
     *
     * @param page MyBatis-Plus 分页对象
     * @param <T>  列表元素类型
     * @return 分页结果，records 为 null 时 list 为空列表
     */
    public static <T> PageResult<T> page(IPage<T> page) {
        return PageResult.of(page.getTotal(), page.getRecords());
    }

    /**
     * 转换并逐条映射：查出的是实体、返回给前端的是 VO 时使用。
     *
     * @param page   MyBatis-Plus 分页对象
     * @param mapper 实体到 VO 的转换函数，如 {@code ActivityVo::from}
     * @param <P>    查询结果类型（实体）
     * @param <T>    返回给前端的类型（VO）
     * @return 分页结果，total 取原始总记录数而非映射后条数
     */
    public static <P, T> PageResult<T> page(IPage<P> page, Function<P, T> mapper) {
        List<P> records = page.getRecords();
        List<T> list = records == null ? List.of() : records.stream().map(mapper).toList();
        return PageResult.of(page.getTotal(), list);
    }
}