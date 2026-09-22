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
        List<SysDict> items = dictMapper.selectList(Wrappers.<SysDict>lambdaQuery()
                .eq(SysDict::getStatus, STATUS_ENABLED)
                .orderByAsc(SysDict::getDictType)
                .orderByAsc(SysDict::getSort)
                .orderByAsc(SysDict::getId));

        Map<String, List<DictItemVO>> result = new LinkedHashMap<>();
        for (SysDict item : items) {
            if (item.getDictType() == null) {
                continue;
            }
            result.computeIfAbsent(item.getDictType(), key -> new ArrayList<>()).add(toVO(item));
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
