package com.vcp.volunteer.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.vcp.volunteer.entity.ActivityCategory;
import com.vcp.volunteer.mapper.row.CategoryRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 活动分类 Mapper。
 *
 * <p>增删改与按 id 查询由 MyBatis-Plus 的 BaseMapper 提供（含逻辑删除过滤）；
 * 列表要带上「该分类下的活动场次」，用 XML 里的聚合查询实现。
 */
@Mapper
public interface ActivityCategoryMapper extends BaseMapper<ActivityCategory> {

    /**
     * 取全部分类（含活动场次），按 sort 升序。
     *
     * @return 分类列表，无数据时为空列表
     */
    List<CategoryRow> selectCategoryList();

    /**
     * 取现有分类编码里 CUSTOM_n 形态的最大序号，用于生成不冲突的新编码。
     *
     * <p><b>为什么不按「分类总数 + 1」生成</b>：{@code uk_category_code} 是物理唯一约束，
     * 而逻辑删除的行仍然占着那个编码。新建 CUSTOM_7 后再删除、再新建，
     * 按总数生成又会得到 CUSTOM_7，直接撞唯一键（与报名表的 A7 是同一类坑）。
     * 这里用原生 SQL 扫全表（<b>含已删除行</b>）取最大序号，避开该坑。
     *
     * <p>序号不匹配 CUSTOM_数字 形态的编码（例如人工填的 COMMUNITY）返回 NULL，
     * 被 MAX 忽略，不影响结果。
     *
     * @return 最大序号；表里没有 CUSTOM_n 形态的编码时返回 0
     */
    @Select("SELECT COALESCE(MAX(CAST(SUBSTRING(code FROM 'CUSTOM_([0-9]+)') AS INT)), 0) "
            + "FROM activity_category WHERE code LIKE 'CUSTOM\\_%'")
    Integer selectMaxCustomCodeSuffix();

    /**
     * 统计某个分类编码的占用行数（<b>含已删除行</b>）。
     *
     * <p>用于在写入前挡住重复编码：唯一约束在数据库层，一旦撞上只能抛出
     * 无法读懂的完整性约束异常，用户看到的是「系统繁忙」。这里先查一次给出明确文案。
     * 逻辑删除的行同样占着编码，因此<b>不能</b>用 MyBatis-Plus 的 selectCount
     * （它会自动追加 deleted = 0）。
     *
     * @param code 分类编码
     * @return 占用该编码的行数
     */
    @Select("SELECT COUNT(*) FROM activity_category WHERE code = #{code}")
    int countByCodeIncludingDeleted(@Param("code") String code);
}
