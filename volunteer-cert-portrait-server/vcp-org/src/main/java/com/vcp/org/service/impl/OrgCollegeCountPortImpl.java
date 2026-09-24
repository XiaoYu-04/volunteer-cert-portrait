package com.vcp.org.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.vcp.org.entity.OrgInfo;
import com.vcp.org.mapper.OrgInfoMapper;
import com.vcp.system.service.OrgCollegeCountPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 按学院统计挂靠组织数（vcp-system 侧 {@link OrgCollegeCountPort} 的实现）。
 *
 * <p><b>为什么实现落在 vcp-org</b>：{@code org_info} 的实体与 Mapper 在本模块，
 * vcp-system 处在依赖链的更底层拿不到它们（模块方向是 vcp-org → vcp-system），
 * 因此由本模块实现端口、运行时交给 Spring 注入。与 {@link OrgLookupPortImpl} 同一套路。
 *
 * <p><b>为什么是一次 GROUP BY</b>：学院列表页一次要全部学院的组织数，
 * 逐学院 COUNT 就是 N+1；这里一次聚合返回「学院名 → 组织数」，调用方只做 Map 查找。
 *
 * <p><b>为什么没有手写 {@code deleted = 0}</b>：{@code OrgInfo.deleted} 标了
 * {@code @TableLogic}，application.yml 里也全局声明了 {@code logic-delete-field: deleted}，
 * MyBatis-Plus 会给 selectMaps 的 WHERE 自动拼上该条件
 * （见 AbstractMethod#sqlWhereEntityWrapper 的逻辑删除分支），手写只会拼出重复条件。
 */
@Service
@RequiredArgsConstructor
public class OrgCollegeCountPortImpl implements OrgCollegeCountPort {

    private final OrgInfoMapper orgMapper;

    /**
     * 统计每个学院挂靠的组织数。
     *
     * <p>学院名为 null 或空串的组织不参与统计：它们不属于任何学院，
     * 归到某个 key 下会让学院管理页显示出一个并不存在的「学院」。
     *
     * @return 学院名 → 组织数；没有组织的学院不出现在 Map 里
     */
    @Override
    public Map<String, Long> countByCollege() {
        Map<String, Long> result = new HashMap<>();
        List<Map<String, Object>> rows = orgMapper.selectMaps(Wrappers.<OrgInfo>query()
                .select("college", "COUNT(*) AS cnt")
                .isNotNull("college")
                .ne("college", "")
                .groupBy("college"));
        for (Map<String, Object> row : rows) {
            Object college = row.get("college");
            if (college != null) {
                result.put(String.valueOf(college), toLong(row.get("cnt")));
            }
        }
        return result;
    }

    /**
     * 聚合结果转 long。
     *
     * <p>COUNT(*) 在 PostgreSQL 里是 int8，但 selectMaps 取出来的值类型随驱动与列类型变化
     * （可能是 Integer / Long / BigDecimal），统一按 Number 转，兜底再走一次字符串解析。
     *
     * @param value 聚合结果值
     * @return 数值，null 时返回 0
     */
    private static long toLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        return value == null ? 0L : Long.parseLong(String.valueOf(value));
    }
}
