package com.vcp.certification.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.vcp.certification.entity.DurationAudit;
import org.apache.ibatis.annotations.Mapper;

/**
 * 时长审核流水 Mapper。
 *
 * <p>只写不读：流水用于留痕与追溯，当前没有对外查询接口，因此不定义自定义 SQL。
 */
@Mapper
public interface DurationAuditMapper extends BaseMapper<DurationAudit> {
}
