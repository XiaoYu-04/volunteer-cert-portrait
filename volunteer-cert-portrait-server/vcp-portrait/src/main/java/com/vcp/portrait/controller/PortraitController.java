package com.vcp.portrait.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.vcp.common.result.PageResult;
import com.vcp.common.result.R;
import com.vcp.framework.log.OperationLog;
import com.vcp.portrait.dto.PortraitQuery;
import com.vcp.portrait.dto.PortraitRecomputeDTO;
import com.vcp.portrait.service.PortraitService;
import com.vcp.portrait.vo.PortraitRecomputeVO;
import com.vcp.portrait.vo.PortraitVO;
import com.vcp.portrait.vo.TagDistributionVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 公益画像接口。
 *
 * <p>权限沿用 {@code StpInterfaceImpl.ROLE_PERMS} 里已有的两条画像权限，不新增权限码
 * （新增会多出一条前端角色管理页看得见、却没有接口引用的权限）：
 * <ul>
 *   <li>{@code portrait:profile:mine} —— 学生，只能看本人的画像；</li>
 *   <li>{@code portrait:profile:list} —— 学校管理员，看全校画像、标签分布与重算。</li>
 * </ul>
 *
 * <p><b>越权防线（待办 B19）</b>：「我的画像」不接收任何学生 id 参数，服务端只认登录会话里的
 * studentId —— 前端 mock 里 {@code eq(undefined)} 会放行全量，后端绝不能照抄；
 * {@code /{studentId}} 这条按学生取画像的路径只有学校管理员有权限，学生拿不到别人的画像。
 *
 * <p>静态段 {@code /me}、{@code /distribution} 声明在 {@code /{studentId}} 之前，
 * 与前端 mock 路由的约定一致（Spring 本身会优先匹配字面量路径，这里保持同样的顺序便于对照）。
 */
@RestController
@RequestMapping("/api/v1/portraits")
@RequiredArgsConstructor
public class PortraitController {

    private final PortraitService portraitService;

    /**
     * 取当前登录学生本人的画像。
     *
     * @return 本人画像，含五个维度的得分
     */
    @GetMapping("/me")
    @SaCheckPermission("portrait:profile:mine")
    public R<PortraitVO> mine() {
        return R.ok(portraitService.getMyPortrait());
    }

    /**
     * 取画像标签分布（八类标签各有多少人）。
     *
     * @return 标签分布，按人数降序
     */
    @GetMapping("/distribution")
    @SaCheckPermission("portrait:profile:list")
    public R<List<TagDistributionVO>> distribution() {
        return R.ok(portraitService.getDistribution());
    }

    /**
     * 分页查询画像明细。
     *
     * @param query 查询条件：keyword（姓名/学号）、college、tag、page、pageSize
     * @return 画像分页结果
     */
    @GetMapping
    @SaCheckPermission("portrait:profile:list")
    public R<PageResult<PortraitVO>> list(PortraitQuery query) {
        return R.ok(portraitService.listPortraits(query));
    }

    /**
     * 取指定学生的画像。
     *
     * @param studentId 学生档案 id
     * @return 画像详情
     */
    @GetMapping("/{studentId}")
    @SaCheckPermission("portrait:profile:list")
    public R<PortraitVO> detail(@PathVariable Long studentId) {
        return R.ok(portraitService.getPortrait(studentId));
    }

    /**
     * 重算公益画像（等级 + 标签 + 画像快照）。
     *
     * <p>请求体可以省略：不带 studentId 时重算全部学生；带上时只重算该学生。
     * 模块名取 {@code LogView.vue} 筛选项里的「认证」—— 日志页是按模块名<b>全等匹配</b>的，
     * 这里写「公益画像」之类的新名字不会报错，但用户在日志页永远筛不出来。
     *
     * @param dto 重算范围，可为 null
     * @return 扫描人数与实际写入份数
     */
    @PostMapping("/recompute")
    @SaCheckPermission("portrait:profile:list")
    @OperationLog(module = "认证", action = "重算公益画像")
    public R<PortraitRecomputeVO> recompute(@RequestBody(required = false) PortraitRecomputeDTO dto) {
        return R.ok(portraitService.recompute(dto));
    }
}
