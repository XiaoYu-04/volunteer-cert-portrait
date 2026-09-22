package com.vcp.common.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 分页查询基类：各模块的列表查询 DTO 继承它即可获得分页参数。
 *
 * <p><b>字段名必须是 page / pageSize</b>：前端 useTable 组合式函数统一发这两个键
 * （见 volunteer-cert-portrait-web/src/composables/useTable.js），
 * 改成 pageNum / size 之类会让所有列表页拿不到数据。
 *
 * <p>取值在 getter 里做归一化，业务代码直接调用即可，不必各自校验：
 * page 小于 1 时按 1 处理，pageSize 缺省为 10、上限为 {@link #MAX_PAGE_SIZE}。
 *
 * <p><b>上限为什么是 1000 而不是 100</b>：前端若干页面会主动传 pageSize=500
 * （「我的时长」汇总、时长提交候选名单），它们靠一次取全量再在前端算汇总。
 * 卡到 100 会让这些页面静默缺数据，表现为"少了几条"，很难定位（见待办 B19）。
 * 同时保留上限是为了挡住 pageSize=100000 这类把全表拉进内存的请求。
 *
 * <p>本类只描述参数，不依赖 MyBatis-Plus —— IPage 的构造放在
 * vcp-framework 的 PageUtils，避免 vcp-common 被拖进 ORM 依赖。
 */
@Data
public class PageQuery implements Serializable {

    /** 单页条数上限 */
    public static final int MAX_PAGE_SIZE = 1000;

    /** 单页条数缺省值，与前端 useTable 的默认值一致 */
    public static final int DEFAULT_PAGE_SIZE = 10;

    /** 页码，从 1 开始 */
    private Integer page;

    /** 每页条数 */
    private Integer pageSize;

    /**
     * 取归一化后的页码。
     *
     * @return 页码，小于 1 或未传时返回 1
     */
    public int getPage() {
        return page == null || page < 1 ? 1 : page;
    }

    /**
     * 取归一化后的每页条数。
     *
     * @return 每页条数，未传时为 10，超过上限时截到上限
     */
    public int getPageSize() {
        if (pageSize == null || pageSize < 1) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(pageSize, MAX_PAGE_SIZE);
    }
}
