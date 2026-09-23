package com.vcp.portrait.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 画像重算请求（{@code POST /api/v1/portraits/recompute}）。
 *
 * <p>请求体可以为空（整批重算），因此 Controller 上的 {@code @RequestBody} 声明为可选。
 * 指定 {@code studentId} 时只重算该学生 —— 用于「某个学生刚通过时长审核，先单独刷新看看」，
 * 不必等整批。
 */
@Data
public class PortraitRecomputeDTO implements Serializable {

    /** 学生档案 id；为空表示重算全部学生 */
    private Long studentId;
}
