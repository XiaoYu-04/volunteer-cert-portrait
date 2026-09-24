package com.vcp.system.service;

import java.util.Map;

/**
 * 组织学院分布端口：按学院名统计挂靠组织数。
 *
 * <p>与 {@link OrgLookupPort} 同一套路 —— vcp-system 在依赖链的更底层，
 * 不能反向依赖 vcp-org，因此定义端口、由 vcp-org 提供实现。
 *
 * <p>调用方用 ObjectProvider 取，没有实现时返回空 Map，学院列表里 orgCount 一律为 0，
 * 不阻断页面（缺这一列只影响展示，不影响学院本身的增删）。
 */
public interface OrgCollegeCountPort {

    /**
     * 统计每个学院挂靠的组织数。
     *
     * @return 学院名 → 组织数；没有数据的学院不出现在 Map 里
     */
    Map<String, Long> countByCollege();
}
