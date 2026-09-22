package com.vcp.system.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import com.vcp.common.result.PageResult;
import com.vcp.common.result.R;
import com.vcp.system.dto.StudentQuery;
import com.vcp.system.service.StudentService;
import com.vcp.system.vo.StudentVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 学生档案接口。
 *
 * <p>权限用 {@code @SaCheckRole("SCHOOL_ADMIN")} 而不是 {@code @SaCheckPermission}：
 * {@code StpInterfaceImpl.ROLE_PERMS} 里没有为"查看学生档案"定义权限标识，
 * 而学生档案是全校学籍数据，不该开给学生与组织管理员。用角色校验是这里最贴切的表达 ——
 * 不要为了凑格式去 ROLE_PERMS 里补一个没人用的权限码，那会让前端角色管理页
 * 多出一条实际没有被任何接口引用的权限。
 *
 * <p>前端 {@code api/system.js} 已定义 listStudents / getStudent 但暂无页面调用，
 * 本接口是为后续学生管理页与画像模块预留的。
 */
@RestController
@RequestMapping("/api/v1/system/students")
@RequiredArgsConstructor
public class StudentController {

    private final StudentService studentService;

    /**
     * 分页查询学生档案。
     *
     * @param query 查询条件
     * @return 分页结果
     */
    @GetMapping
    @SaCheckRole("SCHOOL_ADMIN")
    public R<PageResult<StudentVO>> list(StudentQuery query) {
        return R.ok(studentService.listStudents(query));
    }

    /**
     * 取单个学生档案。
     *
     * @param id 学生档案 id
     * @return 学生档案
     */
    @GetMapping("/{id}")
    @SaCheckRole("SCHOOL_ADMIN")
    public R<StudentVO> detail(@PathVariable Long id) {
        return R.ok(studentService.getStudent(id));
    }
}
