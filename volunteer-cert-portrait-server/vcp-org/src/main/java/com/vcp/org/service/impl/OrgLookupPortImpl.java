package com.vcp.org.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.vcp.org.entity.OrgInfo;
import com.vcp.org.mapper.OrgInfoMapper;
import com.vcp.system.service.OrgLookupPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 登录时按用户 id 反查所属组织。
 *
 * <p>一个组织管理员只负责一个组织；唯一索引会防止同一用户挂多个在用组织。
 */
@Service
@RequiredArgsConstructor
public class OrgLookupPortImpl implements OrgLookupPort {

    private final OrgInfoMapper orgMapper;

    @Override
    public Long findOrgIdByUserId(Long userId) {
        if (userId == null) {
            return null;
        }
        OrgInfo org = orgMapper.selectOne(Wrappers.<OrgInfo>lambdaQuery()
                .select(OrgInfo::getId)
                .eq(OrgInfo::getContactUserId, userId)
                .last("LIMIT 1"));
        return org == null ? null : org.getId();
    }
}
