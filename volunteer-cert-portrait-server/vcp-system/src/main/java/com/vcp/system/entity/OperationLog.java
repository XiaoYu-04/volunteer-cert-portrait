package com.vcp.system.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 操作日志实体，对应 operation_log 表。
 *
 * <p>只追加、不修改、不删除，故没有 update_time 与 deleted，也就不继承 BaseEntity。
 *
 * <p>写入方是 com.vcp.system.event.OperationLogListener（消费 OperationLogEvent），
 * 读取方是日志页。注意 role 不是本表的列：它由 user_id 关联角色实时推导，
 * 冗余存储会在角色调整后失真。
 */
@Data
@TableName("operation_log")
public class OperationLog implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 操作人ID，未登录可为空 */
    private Long userId;

    /** 操作人用户名，冗余留存以便用户改名后仍可追溯 */
    private String username;

    /** 操作描述，形如「志愿活动 - 发布活动」 */
    private String operation;

    /** 所属模块，取值必须是日志页筛选项里的中文名 */
    private String module;

    /** 动作描述，如「新增用户」 */
    private String action;

    /** 操作对象描述；切面暂未采集，留空 */
    private String target;

    /** 执行结果：SUCCESS / FAIL */
    private String result;

    /** 被调用的方法签名 */
    private String method;

    /** 请求参数 JSON */
    private String params;

    private String ip;

    /** 业务方法耗时（毫秒） */
    private Long cost;

    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
