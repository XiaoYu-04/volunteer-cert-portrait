package com.vcp.system.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serializable;

/**
 * 发布公告请求体（POST /api/v1/system/notifications）。
 *
 * <p>发布即群发：后端为每个启用的账号插入一行，并用同一个批次号串起来，
 * 这样"已读"才是每人独立的状态，删除时也能整批删干净。
 *
 * <p>字段名 from 是前端定的（见 api/system.js 的 createNotification）。
 * Java 里 from 不是关键字，可以直接用作字段名，Lombok 生成的 getFrom/setFrom
 * 也让 Jackson 能正确映射，因此这里保持与前端一致，不做改名。
 */
@Data
public class NotificationCreateDTO implements Serializable {

    @NotBlank(message = "请输入公告标题")
    private String title;

    /** 通知类型，缺省按 SYSTEM（系统公告）处理 */
    private String type;

    /** 发布方，缺省为「系统管理员」 */
    private String from;

    /** 是否置顶 */
    private Boolean top;
}
