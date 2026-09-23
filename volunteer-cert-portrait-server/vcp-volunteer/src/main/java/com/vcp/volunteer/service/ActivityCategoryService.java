package com.vcp.volunteer.service;

import com.vcp.volunteer.dto.CategorySaveDTO;
import com.vcp.volunteer.vo.CategoryVO;

import java.util.List;

/**
 * 活动分类服务。
 *
 * <p>分类接口<b>不分页</b>：前端分类管理页与活动发布页都是全量取回（6 个分类，
 * 前端还要在本地算「覆盖活动」合计与画进度条），因此返回数组而不是分页壳。
 *
 * <p>读接口对所有角色开放（活动列表的类型筛选、发布页的分类下拉都要用），
 * 写接口由 {@code volunteer:category:manage} 把住，见 Controller。
 */
public interface ActivityCategoryService {

    /**
     * 取全部分类（含每个分类下的活动场次），按 sort 升序。
     *
     * @return 分类列表，无数据时为空列表
     */
    List<CategoryVO> listCategories();

    /**
     * 新增分类。
     *
     * @param dto 分类内容，name 必填；code 留空时由服务端生成 CUSTOM_n
     * @return 新分类 id
     * @throws com.vcp.common.exception.BusinessException 名称或编码已存在（30002 / 10001）
     */
    Long createCategory(CategorySaveDTO dto);

    /**
     * 修改分类。
     *
     * @param id  分类 id
     * @param dto 待更新字段，null 字段保持原值
     * @throws com.vcp.common.exception.BusinessException 分类不存在（30003）、名称或编码已存在
     */
    void updateCategory(Long id, CategorySaveDTO dto);

    /**
     * 删除分类（逻辑删除）。
     *
     * <p>分类下还有活动时拒绝删除：活动表的 category_id 是外键，且活动列表要显示分类名，
     * 删掉分类会让这些活动在页面上变成无类型的孤儿数据。
     *
     * @param id 分类 id
     * @throws com.vcp.common.exception.BusinessException 分类不存在（30003）、分类下有活动（30004）
     */
    void deleteCategory(Long id);
}
