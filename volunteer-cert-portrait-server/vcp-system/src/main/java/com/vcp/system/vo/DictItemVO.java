package com.vcp.system.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 字典条目：一条「英文码 -> 中文标签 + 色调」的翻译。
 *
 * <p>字段名是前端定的（见 stores/dict.js 的 load）：value / label / tone。
 * 其中 tone 缺省时前端会回退成 mute，但本项目会正常返回 ——
 * 前端是用接口数据<b>整体替换</b>本地静态字典的，返回里没有 tone
 * 会把所有状态标签的颜色一起刷成灰色，所以 tone 必须给全。
 */
@Data
public class DictItemVO implements Serializable {

    /** 英文码，入库值，对应 sys_dict.dict_key */
    private String value;

    /** 中文标签，对应 sys_dict.dict_value */
    private String label;

    /** 色调：mute / ok / warn / bad / info */
    private String tone;
}
