package com.vcp.system.controller;

import com.vcp.common.result.R;
import com.vcp.system.service.DictService;
import com.vcp.system.vo.DictItemVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 数据字典接口。
 *
 * <p><b>刻意不加权限校验</b>：三种角色的页面都要用它把英文状态码翻成中文标签
 * （学生端「通知公告」页也会调 dict.load()）。给它挂上 {@code system:*} 权限
 * 会让学生端的状态标签退化成英文码，而且因为前端 load() 是"失败就静默保留本地
 * 静态定义"，问题不会报错、只会在页面上表现为标签不一致，极难定位。
 *
 * <p>字典本身不含任何敏感信息，放开给全部登录用户是安全的 ——
 * 注意这里说的是"登录用户"，未登录仍然被 SaTokenConfig 的拦截器挡在外面。
 */
@RestController
@RequestMapping("/api/v1/system/dicts")
@RequiredArgsConstructor
public class DictController {

    private final DictService dictService;

    /**
     * 取全部启用中的字典项，按类型分组。
     *
     * @return 字典类型 → 条目列表
     */
    @GetMapping
    public R<Map<String, List<DictItemVO>>> list() {
        return R.ok(dictService.listDicts());
    }
}
