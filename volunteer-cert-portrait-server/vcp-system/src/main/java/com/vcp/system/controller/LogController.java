package com.vcp.system.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.vcp.common.result.PageResult;
import com.vcp.common.result.R;
import com.vcp.system.dto.LogQuery;
import com.vcp.system.service.LogService;
import com.vcp.system.vo.OperationLogVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 操作日志接口，只读。
 *
 * <p>没有写方法是有意的：能改能删的日志不能作为审计依据。日志的写入方是
 * {@code com.vcp.system.event.OperationLogListener}，不经过本 Controller。
 */
@RestController
@RequestMapping("/api/v1/system/logs")
@RequiredArgsConstructor
public class LogController {

    private final LogService logService;

    /**
     * 分页查询操作日志。
     *
     * @param query 查询条件，module 为中文全等匹配
     * @return 分页结果
     */
    @GetMapping
    @SaCheckPermission("system:log:list")
    public R<PageResult<OperationLogVO>> list(LogQuery query) {
        return R.ok(logService.listLogs(query));
    }
}
