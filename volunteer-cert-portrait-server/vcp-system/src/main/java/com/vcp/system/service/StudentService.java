package com.vcp.system.service;

import com.vcp.common.result.PageResult;
import com.vcp.system.dto.StudentQuery;
import com.vcp.system.vo.StudentVO;

/**
 * 学生档案服务。
 *
 * <p><b>本接口只有只读方法</b>：建档写入口不在服务接口上，而是
 * {@link com.vcp.system.service.impl.StudentArchiveRegistrar}，只由「学生注册」
 * 与「管理员新增用户」两条路径调用，避免档案被别的入口凭空创建。
 *
 * <p>管理员补录/修改学号、学院、专业、班级等<b>仍未做</b>（前端也还没有对应页面），
 * 因此这里没有补录、修改方法 —— 档案建出来时学号是系统占位值，真实学号等补录。
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
