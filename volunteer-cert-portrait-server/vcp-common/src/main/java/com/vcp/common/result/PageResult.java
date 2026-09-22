package com.vcp.common.result;

import lombok.Data;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;

/**
 * 分页结果。
 *
 * <p>字段名与前端约定一致：{@code {total, list}}。前端所有列表页都按这两个键取值，
 * 不要改成 records / rows / items 之类。
 *
 * <p>注意 {@code total} 是**总记录数**而非总页数，前端据此自己算分页条。
 *
 * <p>与 MyBatis-Plus {@code IPage} 的转换放在 vcp-framework 的 PageUtils ——
 * 本模块不引入 ORM 依赖，只提供数据结构。
 *
 * @param <T> 列表元素类型
 */
@Data
public class PageResult<T> implements Serializable {

    /** 总记录数 */
    private long total;

    /** 当前页数据 */
    private List<T> list;

    private PageResult(long total, List<T> list) {
        this.total = total;
        this.list = list;
    }

    /**
     * 构造分页结果。
     *
     * @param total 总记录数
     * @param list  当前页数据，为 null 时替换为空列表
     * @param <T>   列表元素类型
     * @return 分页结果
     */
    public static <T> PageResult<T> of(long total, List<T> list) {
        return new PageResult<>(total, list == null ? Collections.emptyList() : list);
    }

    /**
     * 空结果。查询条件不匹配任何记录时用它，避免向前端返回 null 导致列表页报错。
     *
     * @param <T> 列表元素类型
     * @return 总数为 0 的分页结果
     */
    public static <T> PageResult<T> empty() {
        return new PageResult<>(0L, Collections.emptyList());
    }
}