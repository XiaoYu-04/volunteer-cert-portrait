package com.vcp.system.service;

import com.vcp.common.result.PageResult;
import com.vcp.system.dto.StudentQuery;
import com.vcp.system.vo.StudentVO;

/**
 * 学生档案服务。
 *
 * <p>目前只有只读接口 —— 档案由学校管理员在用户管理页建完账号后补录，
 * 补录入口尚未做（前端也还没有对应页面），因此这里不提供写方法。
 *
 * <p>前端 {@code api/system.js} 已定义 {@code listStudents} / {@code getStudent}
 * 但暂无页面调用，本接口是为后续学生管理页与画像模块预留的。
 */
public interface StudentService {

    /**
     * 分页查询学生档案。
     *
     * <p>关键字同时匹配姓名与学号。姓名不在 {@code student_info} 表上（它在
     * {@code sys_user.real_name}），因此实现里要先按姓名反查一批用户 id，
     * 再与学号的条件做 OR —— 详见实现类的说明。
     *
     * @param query 查询条件
     * @return 分页结果
     */
    PageResult<StudentVO> listStudents(StudentQuery query);

    /**
     * 取单个学生档案。
     *
     * @param id 学生档案 id（{@code student_info.id}），不是用户 id
     * @return 学生档案
     * @throws com.vcp.common.exception.BusinessException 档案不存在（10003）
     */
    StudentVO getStudent(Long id);
}
