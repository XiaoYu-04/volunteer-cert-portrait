package com.vcp.volunteer.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.vcp.common.exception.BusinessException;
import com.vcp.common.exception.ErrorCodeEnum;
import com.vcp.volunteer.constant.VolunteerConstants;
import com.vcp.volunteer.dto.CategorySaveDTO;
import com.vcp.volunteer.entity.ActivityCategory;
import com.vcp.volunteer.entity.VolunteerActivity;
import com.vcp.volunteer.mapper.ActivityCategoryMapper;
import com.vcp.volunteer.mapper.VolunteerActivityMapper;
import com.vcp.volunteer.mapper.row.CategoryRow;
import com.vcp.volunteer.service.ActivityCategoryService;
import com.vcp.volunteer.vo.CategoryVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 活动分类服务实现。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ActivityCategoryServiceImpl implements ActivityCategoryService {

    /** 自动生成编码的前缀，与前端 mock 的 CUSTOM_n 同形 */
    private static final String CUSTOM_CODE_PREFIX = "CUSTOM_";

    /** 自动生成编码的最大重试次数：并发新增时序号可能被别的请求抢先占用 */
    private static final int CODE_GENERATE_RETRY = 20;

    private final ActivityCategoryMapper categoryMapper;

    private final VolunteerActivityMapper activityMapper;

    @Override
    public List<CategoryVO> listCategories() {
        return categoryMapper.selectCategoryList().stream().map(this::toVO).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createCategory(CategorySaveDTO dto) {
        String name = requireName(dto);
        requireNameAvailable(name, null);

        ActivityCategory category = new ActivityCategory();
        category.setCategoryName(name);
        category.setCode(resolveCodeForCreate(dto.getCode()));
        category.setSort(dto.getSort() == null ? nextSort() : dto.getSort());
        category.setStatus(VolunteerConstants.CATEGORY_STATUS_ACTIVE);
        categoryMapper.insert(category);

        log.info("[活动分类] 新增分类。id={}, name={}, code={}",
                category.getId(), name, category.getCode());
        return category.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateCategory(Long id, CategorySaveDTO dto) {
        ActivityCategory category = requireCategory(id);
        String name = requireName(dto);
        requireNameAvailable(name, id);

        ActivityCategory patch = new ActivityCategory();
        patch.setId(category.getId());
        patch.setCategoryName(name);
        if (dto.getCode() != null) {
            // 编码允许留空（前端表单可清空）：留空时保留原值，原值也没有才生成一个
            String code = trimToNull(dto.getCode());
            if (code == null) {
                code = category.getCode() == null ? generateCustomCode() : category.getCode();
            } else {
                requireCodeAvailable(code, category);
            }
            patch.setCode(code);
        }
        if (dto.getSort() != null) {
            patch.setSort(dto.getSort());
        }
        categoryMapper.updateById(patch);

        log.info("[活动分类] 修改分类。id={}, name={}", category.getId(), name);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteCategory(Long id) {
        ActivityCategory category = requireCategory(id);
        Long activityCount = activityMapper.selectCount(Wrappers.<VolunteerActivity>lambdaQuery()
                .eq(VolunteerActivity::getCategoryId, id));
        long used = activityCount == null ? 0L : activityCount;
        if (used > 0) {
            // 文案带上具体场次：只说「无法删除」用户会反复试
            throw new BusinessException(ErrorCodeEnum.CATEGORY_IN_USE,
                    "该分类下还有 " + used + " 场活动，无法删除");
        }
        categoryMapper.deleteById(id);
        log.info("[活动分类] 删除分类。id={}, name={}", category.getId(), category.getCategoryName());
    }

    /**
     * 取分类，不存在或已删除时抛 30003。
     *
     * @param id 分类 id
     * @return 分类实体
     */
    private ActivityCategory requireCategory(Long id) {
        ActivityCategory category = id == null ? null : categoryMapper.selectById(id);
        if (category == null) {
            throw new BusinessException(ErrorCodeEnum.CATEGORY_NOT_FOUND);
        }
        return category;
    }

    /**
     * 取并校验分类名称。
     *
     * @param dto 请求体
     * @return 去掉首尾空白的名称
     */
    private static String requireName(CategorySaveDTO dto) {
        String name = dto == null ? null : trimToNull(dto.getName());
        if (name == null) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "请填写分类名称");
        }
        return name;
    }

    /**
     * 名称唯一性校验。
     *
     * <p>库里 {@code activity_category.category_name} 没有唯一约束（见待办 B17），
     * 重名只能靠应用层挡；前端分类管理页也按名称查重并提示 30002，两边口径一致。
     *
     * @param name      待校验名称
     * @param excludeId 排除的分类 id（修改时排除自身），新增传 null
     */
    private void requireNameAvailable(String name, Long excludeId) {
        Long count = categoryMapper.selectCount(Wrappers.<ActivityCategory>lambdaQuery()
                .eq(ActivityCategory::getCategoryName, name)
                .ne(excludeId != null, ActivityCategory::getId, excludeId));
        if (count != null && count > 0) {
            throw new BusinessException(ErrorCodeEnum.CATEGORY_NAME_EXISTS);
        }
    }

    /**
     * 新增时的编码：留空则自动生成，填了则先查重。
     *
     * @param raw 前端提交的编码，可为空
     * @return 最终落库的编码
     */
    private String resolveCodeForCreate(String raw) {
        String code = trimToNull(raw);
        if (code == null) {
            return generateCustomCode();
        }
        requireCodeAvailable(code, null);
        return code;
    }

    /**
     * 编码唯一性校验。
     *
     * <p>库里 {@code uk_category_code} 是物理唯一约束，而逻辑删除的行仍占着编码，
     * 所以这里用 {@code countByCodeIncludingDeleted} 查全量，不能用会自动追加
     * {@code deleted = 0} 的 selectCount。
     *
     * @param code      待校验编码
     * @param current   修改场景下当前分类，编码没变时直接放行；新增传 null
     */
    private void requireCodeAvailable(String code, ActivityCategory current) {
        if (current != null && code.equals(current.getCode())) {
            return;
        }
        if (categoryMapper.countByCodeIncludingDeleted(code) > 0) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "分类编码「" + code + "」已存在");
        }
    }

    /**
     * 生成不与现有（含已删除）编码冲突的 CUSTOM_n。
     *
     * @return 可用的分类编码
     */
    private String generateCustomCode() {
        Integer maxSuffix = categoryMapper.selectMaxCustomCodeSuffix();
        int next = (maxSuffix == null ? 0 : maxSuffix) + 1;
        for (int attempt = 0; attempt < CODE_GENERATE_RETRY; attempt++) {
            String code = CUSTOM_CODE_PREFIX + next;
            if (categoryMapper.countByCodeIncludingDeleted(code) == 0) {
                return code;
            }
            next++;
        }
        throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "分类编码自动生成失败，请手动填写编码");
    }

    /**
     * 排序缺省值：排在现有分类之后。
     *
     * @return 建议的排序值
     */
    private Integer nextSort() {
        Long count = categoryMapper.selectCount(Wrappers.<ActivityCategory>lambdaQuery());
        return (int) ((count == null ? 0L : count) + 1);
    }

    /**
     * 行对象到 VO：库里的 status 是 SMALLINT，这里翻译成前端要的英文码。
     *
     * @param row 查询行
     * @return 分类 VO
     */
    private CategoryVO toVO(CategoryRow row) {
        CategoryVO vo = new CategoryVO();
        vo.setId(row.getId());
        vo.setName(row.getCategoryName());
        vo.setCode(row.getCode());
        vo.setActivityCount(row.getActivityCount() == null ? 0 : row.getActivityCount());
        vo.setSort(row.getSort());
        vo.setStatus(row.getStatus() != null && row.getStatus() == VolunteerConstants.CATEGORY_STATUS_ACTIVE
                ? VolunteerConstants.CATEGORY_STATUS_ACTIVE_CODE
                : VolunteerConstants.CATEGORY_STATUS_DISABLED_CODE);
        // activity_category 没有 remark 列（待办 B16），前端对空值渲染为「—」
        vo.setRemark(null);
        return vo;
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
