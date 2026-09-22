package com.vcp.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.vcp.common.result.PageResult;
import com.vcp.common.util.DateTimeUtils;
import com.vcp.framework.util.PageUtils;
import com.vcp.system.dto.LogQuery;
import com.vcp.system.entity.OperationLog;
import com.vcp.system.entity.SysRole;
import com.vcp.system.mapper.OperationLogMapper;
import com.vcp.system.service.LogService;
import com.vcp.system.vo.OperationLogVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 操作日志服务实现。
 *
 * <p><b>module 必须是全等匹配</b>：日志页的模块下拉是固定七个中文名，
 * 用 like 会让"时长认证"误命中"时长认证xxx"这类脏数据，用户在页面上看到
 * 的条数与所选模块对不上。库里 module 的取值由 {@code @OperationLog(module = ...)}
 * 保证，写入方见 {@code com.vcp.system.event.OperationLogListener}。
 *
 * <p><b>role 是推导出来的</b>：{@code operation_log} 没有 role 列，由 user_id
 * 经 {@link RoleResolver} 实时关联。这样管理员调整角色后，历史日志的角色列会
 * 跟着更新，不会停留在旧角色上 —— 冗余存一列看起来省事，但角色一改就失真。
 */
@Service
@RequiredArgsConstructor
public class LogServiceImpl implements LogService {

    private final OperationLogMapper operationLogMapper;

    private final RoleResolver roleResolver;

    /**
     * 分页查询操作日志。
     *
     * @param query 查询条件
     * @return 分页结果
     */
    @Override
    public PageResult<OperationLogVO> listLogs(LogQuery query) {
        LogQuery condition = query == null ? new LogQuery() : query;
        LambdaQueryWrapper<OperationLog> wrapper = Wrappers.lambdaQuery();

        if (hasText(condition.getKeyword())) {
            String keyword = condition.getKeyword().trim();
            // 与用户列表同理：两个 or 必须用 and(...) 括起来，
            // 否则 or 会与后面的 module 条件平级，模块筛选被绕过。
            wrapper.and(w -> w.like(OperationLog::getUsername, keyword)
                    .or().like(OperationLog::getTarget, keyword));
        }

        if (hasText(condition.getModule())) {
            wrapper.eq(OperationLog::getModule, condition.getModule().trim());
        }

        // 日志是时间序数据，最新在前。id 作为次级排序键：同一秒内写入的多条
        // 靠 create_time 分不出先后，加上 id 才能保证翻页时顺序稳定不重复。
        wrapper.orderByDesc(OperationLog::getCreateTime).orderByDesc(OperationLog::getId);

        Page<OperationLog> page = operationLogMapper.selectPage(PageUtils.toPage(condition), wrapper);
        Map<Long, SysRole> roleByUser = roleResolver.byUserIds(userIdsOf(page.getRecords()));
        return PageUtils.page(page, log -> toVO(log, roleByUser.get(log.getUserId())));
    }

    /**
     * 日志实体转响应对象。
     *
     * @param log  日志实体
     * @param role 操作人当前的角色，允许为 null（用户已删除或没有角色）
     * @return 响应对象
     */
    private OperationLogVO toVO(OperationLog log, SysRole role) {
        OperationLogVO vo = new OperationLogVO();
        vo.setId(log.getId());
        // 格式化成字符串：日志页直接渲染 {{ row.time }}，返回 LocalDateTime 会带上 ISO 的 T
        vo.setTime(DateTimeUtils.formatDateTime(log.getCreateTime()));
        vo.setOperator(log.getUsername());
        vo.setRole(role == null ? null : role.getRoleName());
        vo.setAction(log.getAction());
        vo.setModule(log.getModule());
        vo.setTarget(log.getTarget());
        vo.setIp(log.getIp());
        vo.setResult(log.getResult());
        return vo;
    }

    private List<Long> userIdsOf(List<OperationLog> logs) {
        if (logs == null) {
            return List.of();
        }
        return logs.stream()
                .map(OperationLog::getUserId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
