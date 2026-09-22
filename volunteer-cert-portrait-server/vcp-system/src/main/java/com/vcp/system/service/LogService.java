package com.vcp.system.service;

import com.vcp.common.result.PageResult;
import com.vcp.system.dto.LogQuery;
import com.vcp.system.vo.OperationLogVO;

/**
 * 操作日志服务，只读。
 *
 * <p>日志按只追加设计，没有修改与删除接口 —— 能改的日志等于没有日志。
 * 清理历史数据将来应由运维侧的定时任务按时间分区处理，不走业务接口。
 */
public interface LogService {

    /**
     * 分页查询操作日志。
     *
     * @param query 查询条件，module 为中文全等匹配
     * @return 分页结果，最新的排在最前
     */
    PageResult<OperationLogVO> listLogs(LogQuery query);
}
