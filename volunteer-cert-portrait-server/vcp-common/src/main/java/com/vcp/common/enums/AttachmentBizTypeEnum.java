package com.vcp.common.enums;

import lombok.Getter;

/**
 * 附件业务类型。
 *
 * <p>对应 {@code attachment.biz_type} 字段，码值来源 {@code sys_dict} 中
 * {@code dict_type = 'attachment_biz_type'} 的字典项。库中存英文码，中文标签由前端查字典展示。
 *
 * <p>{@code attachment.biz_id} 是多态外键（不建物理外键约束），
 * 其指向哪张表由本枚举决定：ACTIVITY → volunteer_activity，ORG → org_info，AVATAR → sys_user。
 */
@Getter
public enum AttachmentBizTypeEnum {

    /** 活动图片：biz_id 为 volunteer_activity.id */
    ACTIVITY("ACTIVITY", "活动图片"),
    /** 组织资质：biz_id 为 org_info.id */
    ORG("ORG", "组织资质"),
    /** 用户头像：biz_id 为 sys_user.id */
    AVATAR("AVATAR", "用户头像");

    /** 英文码，入库值 */
    private final String code;

    /** 中文标签，与 sys_dict.dict_value 保持一致 */
    private final String label;

    AttachmentBizTypeEnum(String code, String label) {
        this.code = code;
        this.label = label;
    }

}
