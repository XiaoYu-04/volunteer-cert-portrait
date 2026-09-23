package com.vcp.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.vcp.common.exception.BusinessException;
import com.vcp.common.exception.ErrorCodeEnum;
import com.vcp.common.result.PageResult;
import com.vcp.framework.util.PageUtils;
import com.vcp.system.dto.StudentQuery;
import com.vcp.system.entity.StudentInfo;
import com.vcp.system.entity.SysUser;
import com.vcp.system.mapper.StudentInfoMapper;
import com.vcp.system.mapper.SysUserMapper;
import com.vcp.system.service.StudentService;
import com.vcp.system.vo.StudentVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 学生档案服务实现。
 *
 * <p><b>姓名与手机号不在 student_info 表上</b>，它们在 {@code sys_user}。
 * 因此列表是"先分页查档案、再按 user_id 批量补姓名手机号"两步走：
 * 逐条查会 N+1，写 JOIN 又会让分页插件面对自定义 SQL 的列名与 COUNT 语句
 * （与用户列表按角色筛选是同一个取舍）。
 *
 * <p><b>按姓名筛选也只能两步</b>：姓名字段不在本表，没法在同一个 wrapper 里
 * 直接写 {@code like(realName)}。实现是先按姓名反查一批 user_id，
 * 再与学号条件一起 OR 进档案查询。
 */
@Service
@RequiredArgsConstructor
public class StudentServiceImpl implements StudentService {

    private final StudentInfoMapper studentInfoMapper;

    private final SysUserMapper userMapper;

    /**
     * 分页查询学生档案。
     *
     * @param query 查询条件
     * @return 分页结果
     */
    @Override
    public PageResult<StudentVO> listStudents(StudentQuery query) {
        StudentQuery condition = query == null ? new StudentQuery() : query;
        LambdaQueryWrapper<StudentInfo> wrapper = Wrappers.lambdaQuery();

        if (hasText(condition.getCollege())) {
            wrapper.eq(StudentInfo::getCollege, condition.getCollege().trim());
        }

        if (hasText(condition.getGrade())) {
            wrapper.eq(StudentInfo::getGrade, condition.getGrade().trim());
        }

        if (hasText(condition.getKeyword())) {
            String keyword = condition.getKeyword().trim();
            List<Long> matchedUserIds = findUserIdsByName(keyword);
            // 外层 and(...) 把「学号 like 或 用户 in」括成一组，避免与学院的 eq 混淆优先级。
            // like(studentNo) 一定会被加上，所以括号内不会为空 —— 空的 and(...) 会拼出
            // 一对空括号，SQL 直接语法错误。
            wrapper.and(w -> {
                w.like(StudentInfo::getStudentNo, keyword);
                if (!matchedUserIds.isEmpty()) {
                    w.or().in(StudentInfo::getUserId, matchedUserIds);
                }
            });
        }

        wrapper.orderByAsc(StudentInfo::getId);

        Page<StudentInfo> page = studentInfoMapper.selectPage(PageUtils.toPage(condition), wrapper);
        Map<Long, SysUser> userById = loadUsers(page.getRecords());
        return PageUtils.page(page, student -> toVO(student, userById.get(student.getUserId())));
    }

    /**
     * 取单个学生档案。
     *
     * @param id 学生档案 id
     * @return 学生档案
     */
    @Override
    public StudentVO getStudent(Long id) {
        StudentInfo student = id == null ? null : studentInfoMapper.selectById(id);
        if (student == null) {
            throw new BusinessException(ErrorCodeEnum.STUDENT_NOT_FOUND);
        }
        SysUser user = student.getUserId() == null ? null : userMapper.selectById(student.getUserId());
        return toVO(student, user);
    }

    /**
     * 按姓名反查用户 id。
     *
     * <p>返回空列表是正常情况（没人叫这个名字），调用方据此跳过该 OR 分支；
     * 这里不抛异常，否则"按学号搜一个名字对不上的人"会直接报错。
     *
     * @param keyword 姓名关键字
     * @return 匹配的用户 id 列表
     */
    private List<Long> findUserIdsByName(String keyword) {
        List<SysUser> users = userMapper.selectList(Wrappers.<SysUser>lambdaQuery()
                .select(SysUser::getId)
                .like(SysUser::getRealName, keyword));
        return users.stream().map(SysUser::getId).toList();
    }

    /**
     * 按 user_id 批量取用户，用于补齐姓名与手机号。
     *
     * <p>一次 {@code selectBatchIds} 而不是循环单查：一页几十条，逐条查就是
     * 几十次数据库往返。返回 Map 是为了让调用方按 id 直接取，不必再遍历匹配。
     *
     * @param students 当前页的档案列表
     * @return 用户 id → 用户；无数据时返回空 Map
     */
    private Map<Long, SysUser> loadUsers(List<StudentInfo> students) {
        Map<Long, SysUser> result = new HashMap<>();
        if (students == null || students.isEmpty()) {
            return result;
        }
        List<Long> userIds = students.stream()
                .map(StudentInfo::getUserId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (userIds.isEmpty()) {
            return result;
        }
        for (SysUser user : userMapper.selectBatchIds(userIds)) {
            result.put(user.getId(), user);
        }
        return result;
    }

    /**
     * 档案实体转响应对象。
     *
     * @param student 档案实体
     * @param user    关联用户，允许为 null（用户已被删除时）
     * @return 响应对象
     */
    private StudentVO toVO(StudentInfo student, SysUser user) {
        StudentVO vo = new StudentVO();
        vo.setId(student.getId());
        vo.setName(user == null ? null : user.getRealName());
        vo.setStudentNo(student.getStudentNo());
        vo.setCollege(student.getCollege());
        vo.setMajor(student.getMajor());
        vo.setClassName(student.getClassName());
        vo.setGrade(student.getGrade());
        vo.setPhone(user == null ? null : user.getPhone());
        vo.setTotalDuration(student.getTotalDuration());
        vo.setPublicWelfareLevel(student.getPublicWelfareLevel());
        return vo;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
