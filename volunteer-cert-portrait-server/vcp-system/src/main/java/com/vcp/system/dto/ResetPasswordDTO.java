package com.vcp.system.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 管理员重置口令请求体（PUT /api/v1/system/users/{id}/password）。
 *
 * <p>只有口令一个字段，刻意与本人改密分开：本人改密必须带上旧口令（证明是账号本人在操作），
 * 管理员重置拿不到也不需要旧口令，两者合成一个 DTO 会让"旧口令必填"这条规则说不清。
 *
 * <p>password 留空表示重置为默认口令，由 Service 兜底，不在这里塞默认值 ——
 * 默认口令属于业务规则，散在 DTO 里以后改口径要翻好几处。
 *
 * <p>接口层的 {@code @OperationLog} 必须写 {@code params = false}：本类唯一的字段就是口令，
 * 采集参数会把明文原样写进 {@code operation_log.params}。
 */
@Data
public class ResetPasswordDTO implements Serializable {

    /** 新口令，留空则重置为默认口令 */
    private String password;
}
