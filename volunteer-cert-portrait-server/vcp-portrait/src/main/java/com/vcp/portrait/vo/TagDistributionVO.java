package com.vcp.portrait.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 画像标签分布的一项，对应前端 {@code PortraitView} 的印章墙与玫瑰图
 * （{@code charts.profile(distribution)} 读 {@code tag} / {@code count}）。
 *
 * <p>口径说明：{@code count} 是<b>拥有该标签的学生人数</b>，一个学生可同时拥有多个标签
 * （如「热心志愿者 + 长期坚持型 + 校园服务型」），因此各项之和会大于学生总数 ——
 * 这是标签分布的常规口径，不是重复计数错误。
 */
@Data
public class TagDistributionVO implements Serializable {

    /** 标签名 */
    private String tag;

    /** 该标签下的学生人数 */
    private Integer count;

    /** 标签释义（判定条件），库中历史遗留的未知标签没有释义 */
    private String desc;
}
