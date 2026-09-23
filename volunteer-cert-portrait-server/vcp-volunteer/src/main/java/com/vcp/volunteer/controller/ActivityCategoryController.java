package com.vcp.volunteer.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.vcp.common.result.R;
import com.vcp.framework.log.OperationLog;
import com.vcp.volunteer.dto.CategorySaveDTO;
import com.vcp.volunteer.service.ActivityCategoryService;
import com.vcp.volunteer.vo.CategoryVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 活动分类接口。
 *
 * <p>读接口用 {@code volunteer:activity:list}：三个角色都要用它（学生端活动列表的类型筛选、
 * 组织端的发布表单与活动管理筛选、学校端的分类管理页），而分类管理页本身需要的
 * {@code volunteer:category:manage} 只有学校管理员持有。若读接口也用后者，
 * 学生与组织管理员的活动页会直接报 20003。
 *
 * <p>分类一次返回全部、不分页（前端全量渲染并自行算合计）。
 */
@RestController
@RequestMapping("/api/v1/categories")
@RequiredArgsConstructor
public class ActivityCategoryController {

    private final ActivityCategoryService categoryService;

    /**
     * 取全部分类。
     *
     * @return 分类列表（数组，无分页壳）
     */
    @GetMapping
    @SaCheckPermission("volunteer:activity:list")
    public R<List<CategoryVO>> list() {
        return R.ok(categoryService.listCategories());
    }

    /**
     * 新增分类。
     *
     * @param dto 分类内容
     * @return 新分类 id
     */
    @PostMapping
    @SaCheckPermission("volunteer:category:manage")
    @OperationLog(module = "志愿活动", action = "新增活动分类")
    public R<Long> create(@Valid @RequestBody CategorySaveDTO dto) {
        return R.ok(categoryService.createCategory(dto));
    }

    /**
     * 修改分类。
     *
     * @param id  分类 id
     * @param dto 待更新字段
     * @return 空响应
     */
    @PutMapping("/{id}")
    @SaCheckPermission("volunteer:category:manage")
    @OperationLog(module = "志愿活动", action = "修改活动分类")
    public R<Void> update(@PathVariable Long id, @Valid @RequestBody CategorySaveDTO dto) {
        categoryService.updateCategory(id, dto);
        return R.ok();
    }

    /**
     * 删除分类（逻辑删除）。
     *
     * @param id 分类 id
     * @return 空响应
     */
    @DeleteMapping("/{id}")
    @SaCheckPermission("volunteer:category:manage")
    @OperationLog(module = "志愿活动", action = "删除活动分类")
    public R<Void> delete(@PathVariable Long id) {
        categoryService.deleteCategory(id);
        return R.ok();
    }
}
