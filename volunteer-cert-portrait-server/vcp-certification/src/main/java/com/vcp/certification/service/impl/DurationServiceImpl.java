package com.vcp.certification.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.vcp.certification.dto.DurationItemDTO;
import com.vcp.certification.dto.DurationQuery;
import com.vcp.certification.dto.DurationSubmitDTO;
import com.vcp.certification.entity.DurationAudit;
import com.vcp.certification.entity.ServiceDuration;
import com.vcp.certification.mapper.ActivityBrief;
import com.vcp.certification.mapper.DurationAuditMapper;
import com.vcp.certification.mapper.DurationRefMapper;
import com.vcp.certification.mapper.ServiceDurationMapper;
import com.vcp.certification.service.DurationScope;
import com.vcp.certification.service.DurationService;
import com.vcp.certification.vo.DurationVO;
import com.vcp.common.enums.AuditActionEnum;
import com.vcp.common.enums.DurationStatusEnum;
import com.vcp.common.exception.BusinessException;
import com.vcp.common.exception.ErrorCodeEnum;
import com.vcp.common.result.PageResult;
import com.vcp.framework.security.AuthUtils;
import com.vcp.framework.util.PageUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 服务时长服务实现。
 *
 * <p><b>状态机</b>：PENDING_SUBMIT →（组织提交）→ PENDING_AUDIT →（学校审核）→
 * APPROVED / REJECTED；REJECTED 允许重新提交，回到 PENDING_AUDIT。
 * 由于 service_duration.signup_id 是 NOT NULL UNIQUE，「重新提交」只能更新原行，
 * 不能插新行 —— 这一点决定了下面 submitOne 的分支结构。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DurationServiceImpl implements DurationService {

    /**
     * 单条服务时长上限（小时）。
     * 不是业务规则，而是拦截明显录错的输入：一天不可能服务 24 小时以上，
     * 没有上限时一个手误的 1000 会直接进学生累计时长并污染画像。
     */
    private static final BigDecimal MAX_HOURS = BigDecimal.valueOf(24);

    /** 证明材料列宽 VARCHAR(255) */
    private static final int MAX_PROOF_LENGTH = 255;

    /** 活动类型列宽 VARCHAR(50)，与 activity_category.category_name 同宽 */
    private static final int MAX_ACTIVITY_TYPE_LENGTH = 50;

    private final ServiceDurationMapper serviceDurationMapper;

    private final DurationAuditMapper durationAuditMapper;

    private final DurationRefMapper refMapper;

    private final DurationScopeResolver scopeResolver;

    @Override
    public PageResult<DurationVO> listDurations(DurationQuery query) {
        DurationQuery condition = query == null ? new DurationQuery() : query;
        DurationScope scope = scopeResolver.resolve();

        // 只有学校管理员认请求里的 orgId / studentId；其余角色一律用会话解析出的范围
        IPage<DurationVO> page = refMapper.selectDurationPage(
                PageUtils.toPage(condition),
                trimToNull(condition.getStatus()),
                trimToNull(condition.getCollege()),
                scope.schoolWide() ? condition.getOrgId() : scope.orgId(),
                scope.schoolWide() ? condition.getStudentId() : scope.studentId(),
                likeKeyword(condition.getKeyword()));
        return PageUtils.page(page);
    }

    @Override
    public DurationVO getDuration(Long id) {
        if (id == null) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "缺少时长记录ID");
        }
        DurationVO row = refMapper.selectDurationDetail(id);
        if (row == null) {
            throw new BusinessException(ErrorCodeEnum.DURATION_NOT_FOUND);
        }
        requireVisible(row);
        return row;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<Long> submitDurations(DurationSubmitDTO dto) {
        List<DurationItemDTO> items = dto == null ? List.of() : dto.resolveItems();
        if (items.isEmpty()) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "请至少选择一条要提交的服务时长");
        }
        // 组织管理员只能提交本组织活动下的时长。能走到这里的只有组织管理员
        // （接口权限 certification:duration:submit），但取值仍走统一的范围解析，避免两套口径。
        Long submitterOrgId = scopeResolver.resolve().orgId();

        List<Long> ids = new ArrayList<>(items.size());
        for (int i = 0; i < items.size(); i++) {
            ids.add(submitOne(items.get(i), i + 1, submitterOrgId));
        }
        log.info("[时长认证] 服务时长已提交。共 {} 条，ids={}", ids.size(), ids);
        return ids;
    }

    /**
     * 提交（或重新提交）一条服务时长。
     *
     * @param item           提交明细
     * @param index          第几条（从 1 起，用于错误文案定位）
     * @param submitterOrgId 提交人所属组织；非组织管理员为 null（不做归属校验）
     * @return 时长记录 id
     */
    private Long submitOne(DurationItemDTO item, int index, Long submitterOrgId) {
        if (item == null) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "第 " + index + " 条记录为空");
        }
        Long studentId = item.getStudentId();
        Long activityId = item.getActivityId();
        if (studentId == null) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "第 " + index + " 条记录缺少学生ID");
        }
        if (activityId == null) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "第 " + index + " 条记录缺少活动ID");
        }
        BigDecimal hours = normalizeHours(item.getHours(), index);

        // 学生档案存在性：不存在就没有可认证的对象，宁可报错也不落一条查不到人的记录
        if (refMapper.selectStudentUserId(studentId) == null) {
            throw new BusinessException(ErrorCodeEnum.STUDENT_NOT_FOUND, "学生档案不存在（学生ID " + studentId + "）");
        }

        ActivityBrief activity = refMapper.selectActivityBrief(activityId);
        if (activity == null) {
            throw new BusinessException(ErrorCodeEnum.ACTIVITY_NOT_FOUND, "活动不存在（活动ID " + activityId + "）");
        }
        if (submitterOrgId != null && activity.getOrgId() != null && !submitterOrgId.equals(activity.getOrgId())) {
            // 越权防护：不校验的话，A 组织可以给 B 组织的活动提交时长。
            // 这里刻意用 10001 而不是 20003：前端 request.js 的 UNAUTHORIZED_CODES 里含 20003，
            // 命中会直接清 token 跳登录 —— 一次传错活动 id 不该把用户踢下线。
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR,
                    "活动「" + activity.getTitle() + "」不属于本组织，无法提交服务时长");
        }

        // signup_id 是 NOT NULL UNIQUE，前端只给 studentId + activityId，必须自己反查
        Long signupId = refMapper.selectSignupId(activityId, studentId);
        if (signupId == null) {
            throw new BusinessException(ErrorCodeEnum.SIGNUP_NOT_FOUND,
                    "未找到该学生在此活动下的有效报名记录（学生ID " + studentId + "，活动ID " + activityId + "），无法提交服务时长");
        }

        ServiceDuration existing = serviceDurationMapper.selectOne(Wrappers.<ServiceDuration>lambdaQuery()
                .eq(ServiceDuration::getSignupId, signupId));
        if (existing != null) {
            String status = existing.getStatus();
            if (DurationStatusEnum.APPROVED.getCode().equals(status)) {
                throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "该学生此活动的服务时长已通过审核，无法重复提交");
            }
            if (DurationStatusEnum.PENDING_AUDIT.getCode().equals(status)) {
                throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "该学生此活动的服务时长已提交，等待学校审核，请勿重复提交");
            }
            // 走到这里是 REJECTED（驳回后重新提交）或 PENDING_SUBMIT（历史上从未提交过）
        }

        ServiceDuration target = existing == null ? new ServiceDuration() : existing;
        target.setSignupId(signupId);
        target.setActivityId(activityId);
        target.setStudentId(studentId);
        target.setDuration(hours);
        target.setStatus(DurationStatusEnum.PENDING_AUDIT.getCode());
        target.setSubmitTime(LocalDateTime.now());
        target.setOrgId(activity.getOrgId());
        target.setActivityType(resolveActivityType(activity, item, index));
        target.setProof(limitLength(item.getProof(), MAX_PROOF_LENGTH, "证明材料地址", index));
        // 上一轮的审核痕迹一律清空：结论已失效，历史仍在 duration_audit 流水里
        // （三个字段在实体上声明了 FieldStrategy.ALWAYS，null 才会真正写库）
        target.setAuditUserId(null);
        target.setAuditTime(null);
        target.setAuditRemark(null);

        if (existing == null) {
            serviceDurationMapper.insert(target);
        } else {
            serviceDurationMapper.updateById(target);
        }

        // 每次提交（含重新提交）都留一条流水：「驳回 → 重新提交 → 再审核」的轨迹靠它才追得回来
        recordSubmitFlow(target.getId(), activity.getTitle(), hours);
        return target.getId();
    }

    /**
     * 取活动类型：以活动分类名为准，活动没挂分类时退回请求里带的值。
     *
     * @param activity 活动精简信息
     * @param item     提交明细
     * @param index    第几条
     * @return 活动类型；两者都为空时返回 null
     */
    private String resolveActivityType(ActivityBrief activity, DurationItemDTO item, int index) {
        if (hasText(activity.getCategoryName())) {
            return activity.getCategoryName();
        }
        return limitLength(item.getActivityType(), MAX_ACTIVITY_TYPE_LENGTH, "活动类型", index);
    }

    /**
     * 写一条 SUBMIT 流水。
     *
     * @param durationId    时长记录 id
     * @param activityTitle 活动名称，仅用于日志
     * @param hours         本次提交的时长
     */
    private void recordSubmitFlow(Long durationId, String activityTitle, BigDecimal hours) {
        DurationAudit flow = new DurationAudit();
        flow.setDurationId(durationId);
        flow.setAuditorId(AuthUtils.getUserId());
        flow.setAction(AuditActionEnum.SUBMIT.getCode());
        // remark 列宽 255，这里刻意不拼活动名称：名称最长 200 字符，拼进去有超长风险
        flow.setRemark("组织提交服务时长 " + plainHours(hours) + " 小时");
        durationAuditMapper.insert(flow);
        log.info("[时长认证] 提交时长流水已写入。durationId={}, activity={}", durationId, activityTitle);
    }

    /**
     * 校验并归一化服务时长。
     *
     * @param hours 原始时长
     * @param index 第几条
     * @return 一位小数的时长（与 NUMERIC(10,1) 对齐）
     */
    private BigDecimal normalizeHours(BigDecimal hours, int index) {
        if (hours == null) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "第 " + index + " 条记录缺少服务时长");
        }
        if (hours.signum() <= 0) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "第 " + index + " 条记录的服务时长必须大于 0 小时");
        }
        if (hours.compareTo(MAX_HOURS) > 0) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "第 " + index + " 条记录的服务时长不能超过 24 小时");
        }
        return hours.setScale(1, RoundingMode.HALF_UP);
    }

    /**
     * 校验当前登录人能看到这条记录。
     *
     * <p>超出范围时回 40001「记录不存在」而不是 20003「无权限」，沿用 vcp-system 对越权读取的
     * 处理口径：报「无权限」等于确认这条记录确实存在，属信息泄露。另一个同样重要的原因是
     * 前端 request.js 的 UNAUTHORIZED_CODES 里含 20003，命中会清 token 跳登录 ——
     * 看错一条记录不该把用户踢下线。
     *
     * @param row 记录
     */
    private void requireVisible(DurationVO row) {
        DurationScope scope = scopeResolver.resolve();
        if (scope.studentId() != null && !scope.studentId().equals(row.getStudentId())) {
            throw new BusinessException(ErrorCodeEnum.DURATION_NOT_FOUND, "服务时长记录不存在，或不在当前账号的数据范围内");
        }
        if (scope.orgId() != null && !scope.orgId().equals(row.getOrgId())) {
            throw new BusinessException(ErrorCodeEnum.DURATION_NOT_FOUND, "服务时长记录不存在，或不在当前账号的数据范围内");
        }
    }

    private static String likeKeyword(String keyword) {
        return hasText(keyword) ? "%" + keyword.trim() + "%" : null;
    }

    private static String limitLength(String value, int max, String field, int index) {
        String text = trimToNull(value);
        if (text != null && text.length() > max) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR,
                    "第 " + index + " 条记录的" + field + "过长（最多 " + max + " 个字符）");
        }
        return text;
    }

    private static String plainHours(BigDecimal hours) {
        return hours == null ? "0" : hours.stripTrailingZeros().toPlainString();
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static String trimToNull(String value) {
        return hasText(value) ? value.trim() : null;
    }
}
