package com.vcp.system.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.vcp.system.entity.StudentInfo;
import com.vcp.system.mapper.StudentInfoMapper;

/**
 * 学生档案按账号取行的共用小工具。
 *
 * <p>建档（{@link StudentArchiveRegistrar}）与销档（{@link StudentArchivePurger}）
 * 原先各有一份逐字相同的「按 user_id 取一条档案」查询，合并到这里，避免两处口径漂移
 * （例如将来一处加了排序或过滤、另一处忘改）。刻意不注册成 Spring Bean：
 * 它只是一个无状态的包内静态方法，Mapper 由调用方传入。
 *
 * <p>查询自带 {@code @TableLogic} 的 {@code deleted = 0}：已逻辑删除的档案查不出来，
 * 销档流程因此天然幂等；建档流程查不到时会落到插入分支，由唯一约束兜底。
 *
 * <p>本类是包内工具，不可实例化。
 */
final class StudentArchiveQueries {

    private StudentArchiveQueries() {
    }

    /**
     * 按账号 id 取在用档案。
     *
     * @param studentInfoMapper 学生档案 Mapper
     * @param userId            账号 id（{@code sys_user.id}）
     * @return 档案；该账号没有在用档案时返回 null
     */
    static StudentInfo findByUserId(StudentInfoMapper studentInfoMapper, Long userId) {
        return studentInfoMapper.selectOne(Wrappers.<StudentInfo>lambdaQuery()
                .eq(StudentInfo::getUserId, userId)
                .last("LIMIT 1"));
    }
}
