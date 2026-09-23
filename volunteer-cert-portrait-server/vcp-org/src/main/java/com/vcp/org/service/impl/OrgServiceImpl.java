package com.vcp.org.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.vcp.common.enums.AuditActionEnum;
import com.vcp.common.enums.OrgStatusEnum;
import com.vcp.common.enums.RoleCodeEnum;
import com.vcp.common.exception.BusinessException;
import com.vcp.common.exception.ErrorCodeEnum;
import com.vcp.common.result.PageResult;
import com.vcp.common.util.DateTimeUtils;
import com.vcp.framework.security.AuthUtils;
import com.vcp.framework.util.PageUtils;
import com.vcp.org.dto.OrgAuditDTO;
import com.vcp.org.dto.OrgQuery;
import com.vcp.org.dto.OrgStatusDTO;
import com.vcp.org.dto.OrgUpdateDTO;
import com.vcp.org.entity.OrgInfo;
import com.vcp.org.mapper.OrgInfoMapper;
import com.vcp.org.service.OrgMetricsPort;
import com.vcp.org.service.OrgMetricsPort.OrgMetrics;
import com.vcp.org.service.OrgService;
import com.vcp.org.vo.OrgVO;
import com.vcp.system.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 志愿组织服务实现。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrgServiceImpl implements OrgService {

    private static final String AUDIT_SOURCE = "学校管理员";

    private final OrgInfoMapper orgMapper;

    private final NotificationService notificationService;

    private final ObjectProvider<OrgMetricsPort> metricsPortProvider;

    @Override
    public PageResult<OrgVO> listOrgs(OrgQuery query) {
        OrgQuery condition = query == null ? new OrgQuery() : query;
        LambdaQueryWrapper<OrgInfo> wrapper = Wrappers.lambdaQuery();

        if (hasText(condition.getKeyword())) {
            wrapper.like(OrgInfo::getOrgName, condition.getKeyword().trim());
        }
        if (hasText(condition.getStatus())) {
            wrapper.eq(OrgInfo::getStatus, condition.getStatus().trim());
        }
        if (hasText(condition.getCollege())) {
            wrapper.eq(OrgInfo::getCollege, condition.getCollege().trim());
        }

        wrapper.orderByDesc(OrgInfo::getId);
        Page<OrgInfo> page = orgMapper.selectPage(PageUtils.toPage(condition), wrapper);
        return PageUtils.page(page, this::toVO);
    }

    @Override
    public OrgVO getOrg(Long id) {
        OrgInfo org = requireOrg(id);
        requireReadableOrg(org);
        return toVO(org);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateOrg(Long id, OrgUpdateDTO dto) {
        OrgInfo org = requireOrg(id);
        requireReadableOrg(org);
        if (dto == null || !hasText(dto.getName())) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "组织名称不能为空");
        }

        OrgInfo patch = new OrgInfo();
        patch.setId(org.getId());
        patch.setOrgName(dto.getName().trim());
        patch.setCode(trimToNull(dto.getCode()));
        patch.setOrgType(trimToNull(dto.getOrgType()));
        patch.setContactName(trimToNull(dto.getContact()));
        patch.setPhone(trimToNull(dto.getPhone()));
        patch.setEmail(trimToNull(dto.getEmail()));
        patch.setCollege(trimToNull(dto.getCollege()));
        patch.setMemberCount(dto.getMemberCount());
        patch.setFoundedAt(dto.getFoundedAt());
        patch.setDescription(trimToNull(dto.getIntro()));
        orgMapper.updateById(patch);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void auditOrg(Long id, OrgAuditDTO dto) {
        OrgInfo org = requireOrg(id);
        if (!OrgStatusEnum.PENDING.getCode().equals(org.getStatus())) {
            throw new BusinessException(ErrorCodeEnum.ORG_ALREADY_AUDITED);
        }

        AuditActionEnum action = dto == null ? null : AuditActionEnum.of(dto.getAction());
        if (action == null) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "审核动作不合法");
        }

        String remark = dto.getRemark() == null ? "" : dto.getRemark().trim();
        if (action == AuditActionEnum.REJECT && remark.isBlank()) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "驳回时必须填写理由");
        }

        OrgInfo patch = new OrgInfo();
        patch.setId(org.getId());
        patch.setStatus(action == AuditActionEnum.APPROVE
                ? OrgStatusEnum.APPROVED.getCode()
                : OrgStatusEnum.REJECTED.getCode());
        patch.setAuditRemark(remark);
        orgMapper.updateById(patch);

        sendAuditNotification(org, action, remark);
        log.info("[组织] 资质审核完成。orgId={}, action={}", org.getId(), action.getCode());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(Long id, OrgStatusDTO dto) {
        OrgInfo org = requireOrg(id);
        OrgStatusEnum status = dto == null ? null : OrgStatusEnum.of(dto.getStatus());
        if (status != OrgStatusEnum.APPROVED && status != OrgStatusEnum.DISABLED) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "组织目标状态不合法");
        }
        if (!OrgStatusEnum.APPROVED.getCode().equals(org.getStatus())
                && !OrgStatusEnum.DISABLED.getCode().equals(org.getStatus())) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "只有已通过审核的组织可以启用或停用");
        }

        OrgInfo patch = new OrgInfo();
        patch.setId(org.getId());
        patch.setStatus(status.getCode());
        orgMapper.updateById(patch);
    }

    private OrgInfo requireOrg(Long id) {
        OrgInfo org = id == null ? null : orgMapper.selectById(id);
        if (org == null) {
            throw new BusinessException(ErrorCodeEnum.ORG_NOT_FOUND);
        }
        return org;
    }

    private void requireReadableOrg(OrgInfo org) {
        String roleCode = AuthUtils.getRoleCode();
        if (RoleCodeEnum.SCHOOL_ADMIN.getCode().equals(roleCode)) {
            return;
        }
        if (RoleCodeEnum.ORG_ADMIN.getCode().equals(roleCode)
                && org.getId().equals(AuthUtils.getOrgId())) {
            return;
        }
        throw new BusinessException(ErrorCodeEnum.NO_PERMISSION);
    }

    private void sendAuditNotification(OrgInfo org, AuditActionEnum action, String remark) {
        if (org.getContactUserId() == null) {
            return;
        }
        boolean approved = action == AuditActionEnum.APPROVE;
        String title = approved ? "组织资质已通过" : "组织资质未通过";
        String content = approved
                ? "您负责的组织「%s」已通过资质审核，可以发布志愿活动。".formatted(org.getOrgName())
                : "您负责的组织「%s」未通过资质审核。审核意见：%s".formatted(org.getOrgName(), remark);
        notificationService.createForUser(org.getContactUserId(), title, content,
                null, AUDIT_SOURCE);
    }

    private OrgVO toVO(OrgInfo org) {
        OrgVO vo = new OrgVO();
        vo.setId(org.getId());
        vo.setName(org.getOrgName());
        vo.setCode(org.getCode());
        vo.setContact(org.getContactName());
        vo.setPhone(org.getPhone());
        vo.setEmail(org.getEmail());
        vo.setCollege(org.getCollege());
        vo.setMemberCount(org.getMemberCount());
        vo.setFoundedAt(DateTimeUtils.formatDate(org.getFoundedAt()));
        vo.setStatus(org.getStatus());
        vo.setIntro(org.getDescription());
        vo.setAuditRemark(org.getAuditRemark());

        OrgMetrics metrics = metricsOf(org.getId());
        vo.setActivities(metrics.activities());
        vo.setSignRate(metrics.signRate());
        vo.setPassRate(metrics.passRate());
        return vo;
    }

    private OrgMetrics metricsOf(Long orgId) {
        OrgMetricsPort port = metricsPortProvider.getIfAvailable();
        if (port == null) {
            return OrgMetrics.empty();
        }
        OrgMetrics metrics = port.getMetrics(orgId);
        return metrics == null ? OrgMetrics.empty() : metrics;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static String trimToNull(String value) {
        return hasText(value) ? value.trim() : null;
    }
}
