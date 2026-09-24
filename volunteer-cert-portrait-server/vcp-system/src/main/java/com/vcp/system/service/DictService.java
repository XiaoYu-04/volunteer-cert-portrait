package com.vcp.system.service;

import com.vcp.system.vo.DictItemVO;

import java.util.List;
import java.util.Map;

/**
 * 数据字典服务：把库里的「英文码 → 中文标签 + 色调」交给前端。
 *
 * <p>前端 {@code stores/dict.js} 的 {@code load()} 是<b>整体替换</b>语义：
 * 接口返回哪个类型，就整个覆盖该类型的本地静态定义。因此这里必须一次返回全部类型，
 * 只返回一部分会让没返回的类型继续用静态定义、返回了的用库里的，两边不一致。
 */
public interface DictService {

    /**
     * 取全部启用中的字典项，按类型分组。
     *
     * <p>返回 Map 而不是 List：前端要的就是 {@code {dictType: [条目...]}} 这个形状，
     * 在服务端分好组，前端拿到直接替换，不用再做一次 groupBy。
     *
     * @return 字典类型 → 条目列表，按 {@code sort} 升序；无数据时返回空 Map
     */
    Map<String, List<DictItemVO>> listDicts();

    /**
     * 取指定类型的启用中字典项，按 sort 升序。
     *
     * <p>给只关心一个类型的调用方用：注册页的学院下拉、注册时对学院的合法性校验。
     * 注册页还没有登录态、也拿不到 {@code /api/v1/system/dicts}，只能走这个入口。
     *
     * @param dictType 字典类型，如 "college"
     * @return 条目列表；类型不存在时返回空列表（不抛异常）
     */
    List<DictItemVO> listByType(String dictType);
}
