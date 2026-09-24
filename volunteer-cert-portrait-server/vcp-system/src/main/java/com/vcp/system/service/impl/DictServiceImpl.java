package com.vcp.system.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.vcp.system.entity.SysDict;
import com.vcp.system.mapper.SysDictMapper;
import com.vcp.system.service.DictService;
import com.vcp.system.vo.DictItemVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 字典服务实现。
 *
 * <p>用 {@link LinkedHashMap} 而不是 HashMap：字典类型的返回顺序会决定前端
 * 下拉框里各分组的顺序，固定顺序让每次刷新看到的都一样，排查问题时也能对得上。
 *
 * <p>只取 {@code status = 1} 的条目。停用的字典项在前端会表现为"这个码查不到标签、
 * 直接显示英文码"，因此下架一个状态码用停用即可，不必删数据。
 */
@Service
@RequiredArgsConstructor
public class DictServiceImpl implements DictService {

    /** 字典项启用状态，与 sql/02_schema.sql 的 sys_dict.status 注释一致 */
    private static final int STATUS_ENABLED = 1;

    private final SysDictMapper dictMapper;

    /**
     * 取全部启用中的字典项，按类型分组。
     *
     * @return 字典类型 → 条目列表
     */
    @Override
    public Map<String, List<DictItemVO>> listDicts() {
        Map<String, List<DictItemVO>> result = new LinkedHashMap<>();
        for (SysDict item : selectEnabled(null)) {
            if (item.getDictType() == null) {
                continue;
            }
            result.computeIfAbsent(item.getDictType(), key -> new ArrayList<>()).add(toVO(item));
        }
        return result;
    }

    /**
     * 取指定类型的启用中字典项，按 sort 升序。
     *
     * @param dictType 字典类型，如 "college"
     * @return 条目列表；类型不存在时返回空列表（不抛异常）
     */
    @Override
    public List<DictItemVO> listByType(String dictType) {
        // 空类型不能下推给 SQL：下面那个 eq 条件是「不限类型」的意思，
        // 传空值会退化成把全部字典项当成这一个类型返回。
        if (dictType == null || dictType.isBlank()) {
            return List.of();
        }
        return toVOList(selectEnabled(dictType));
    }

    /**
     * 查启用中的字典项。
     *
     * <p>「只取 status = 1、按 sort 升序」这条口径只写在这里，两个查询入口都走它，
     * 免得两处各写一遍之后慢慢漂移。按类型排序是给 {@link #listDicts} 用的：
     * 它靠结果顺序决定分组顺序，不限类型时也得先按类型排好。
     *
     * @param dictType 字典类型；{@code null} 表示不限类型
     * @return 字典实体列表
     */
    private List<SysDict> selectEnabled(String dictType) {
        return dictMapper.selectList(Wrappers.<SysDict>lambdaQuery()
                .eq(SysDict::getStatus, STATUS_ENABLED)
                .eq(dictType != null, SysDict::getDictType, dictType)
                .orderByAsc(SysDict::getDictType)
                .orderByAsc(SysDict::getSort)
                .orderByAsc(SysDict::getId));
    }

    /**
     * 判断某个类型下是否存在指定的启用中字典项。
     *
     * <p>空值一律判否，不下推给 SQL：{@code eq(column, null)} 在 MyBatis-Plus 里是
     * 「不拼这个条件」，传空值会退化成「这个类型下有没有任意一条启用项」，恒为真。
     *
     * @param dictType 字典类型，如 "college"
     * @param key      字典键（入库值），如学院名
     * @return 命中启用项时返回 true；任一参数为空时返回 false
     */
    @Override
    public boolean containsEnabled(String dictType, String key) {
        if (dictType == null || dictType.isBlank() || key == null || key.isBlank()) {
            return false;
        }
        Long count = dictMapper.selectCount(Wrappers.<SysDict>lambdaQuery()
                .eq(SysDict::getDictType, dictType)
                .eq(SysDict::getDictKey, key)
                .eq(SysDict::getStatus, STATUS_ENABLED));
        return count != null && count > 0;
    }

    /**
     * 字典实体列表转条目列表。
     *
     * @param items 字典实体列表
     * @return 条目列表
     */
    private List<DictItemVO> toVOList(List<SysDict> items) {
        List<DictItemVO> result = new ArrayList<>(items.size());
        for (SysDict item : items) {
            result.add(toVO(item));
        }
        return result;
    }

    /**
     * 字典实体转条目对象。
     *
     * @param item 字典实体
     * @return 条目对象，字段名与前端 load() 读取的一致
     */
    private DictItemVO toVO(SysDict item) {
        DictItemVO vo = new DictItemVO();
        vo.setValue(item.getDictKey());
        vo.setLabel(item.getDictValue());
        // tone 兜底成 mute：库里若漏填，前端会整体替换掉本地静态定义，
        // 返回 null 会让这类标签全部变成灰色，不如显式给一个安全值。
        vo.setTone(item.getTone() == null || item.getTone().isBlank() ? "mute" : item.getTone());
        return vo;
    }
}
