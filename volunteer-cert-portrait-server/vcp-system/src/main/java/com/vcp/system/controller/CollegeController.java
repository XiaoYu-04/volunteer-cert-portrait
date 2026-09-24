package com.vcp.system.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.vcp.common.result.R;
import com.vcp.framework.log.OperationLog;
import com.vcp.system.dto.CollegeSaveDTO;
import com.vcp.system.dto.StatusUpdateDTO;
import com.vcp.system.service.CollegeService;
import com.vcp.system.vo.CollegeVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 学院管理接口（学校管理端）。
 *
 * <p><b>学院不是独立表</b>：它就是 {@code sys_dict} 里 {@code dict_type = 'college'}
 * 的字典行，因此这里的增删改都作用在字典上 —— 删掉一个学院，注册页的学院下拉
 * （免登录的 {@code /api/v1/auth/colleges}）立刻选不到它，两个入口读的是同一份数据。
 *
 * <p><b>与那份免登录接口的差别是刻意的</b>：{@code /api/v1/auth/colleges} 只返回启用项
 * （注册页不该选到停招的学院），本接口的列表<b>不过滤 status</b> —— 管理页要能看到停用项
 * 并把它启用回来，否则停用等于「从列表里消失」，没有任何入口能改回去。
 *
 * <p>每个方法都挂了 {@code @SaCheckPermission("system:college:manage")}，该权限标识只配在
 * {@code StpInterfaceImpl.ROLE_PERMS} 的学校管理员那一组，学生与组织管理员拿不到。
 *
 * <p>三个写操作都标了 {@code @OperationLog}，module 固定为「用户与权限」——
 * 这个中文串必须与日志页筛选项逐字一致，写错不报错但永远筛不出来。
 * 这里<b>刻意不写 {@code params = false}</b>：请求体里只有学院名 / 排序 / 状态，
 * 没有口令一类需要避开的字段，采集参数正是想要的行为（与
 * {@link UserController#updateStatus} 同形）。
 */
@RestController
@RequestMapping("/api/v1/system/colleges")
@RequiredArgsConstructor
public class CollegeController {

    private final CollegeService collegeService;

    /**
     * 取全部学院（含停用项），带学生数与组织数。
     *
     * @return 学院列表，按 sort 升序、再按 id 升序
     */
    @GetMapping
    @SaCheckPermission("system:college:manage")
    public R<List<CollegeVO>> list() {
        return R.ok(collegeService.listColleges());
    }

    /**
     * 新增学院。
     *
     * @param dto 学院名称与排序
     * @return 空响应
     */
    @PostMapping
    @SaCheckPermission("system:college:manage")
    @OperationLog(module = "用户与权限", action = "新增学院")
    public R<Void> create(@RequestBody CollegeSaveDTO dto) {
        collegeService.createCollege(dto);
        return R.ok();
    }

    /**
     * 启用 / 停用学院。
     *
     * @param id  学院对应的 sys_dict.id
     * @param dto 目标状态
     * @return 空响应
     */
    @PutMapping("/{id}/status")
    @SaCheckPermission("system:college:manage")
    @OperationLog(module = "用户与权限", action = "启停学院")
    public R<Void> updateStatus(@PathVariable Long id, @RequestBody StatusUpdateDTO dto) {
        collegeService.updateStatus(id, dto);
        return R.ok();
    }

    /**
     * 删除学院。
     *
     * @param id 学院对应的 sys_dict.id
     * @return 空响应
     */
    @DeleteMapping("/{id}")
    @SaCheckPermission("system:college:manage")
    @OperationLog(module = "用户与权限", action = "删除学院")
    public R<Void> delete(@PathVariable Long id) {
        collegeService.deleteCollege(id);
        return R.ok();
    }
}
