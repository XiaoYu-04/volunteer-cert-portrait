package com.vcp.system.service;

import com.vcp.system.dto.CollegeSaveDTO;
import com.vcp.system.dto.StatusUpdateDTO;
import com.vcp.system.vo.CollegeVO;

import java.util.List;

/**
 * 学院管理服务。
 *
 * <p>学院是 {@code sys_dict} 里 {@code dict_type = 'college'} 的字典行，
 * 本服务是这一类字典行的写入口（读给注册页的那条链路仍在 {@link DictService}）。
 * 不并进 {@code DictService}：字典服务是「英文码 → 中文标签 + 色调」的通用翻译层，
 * 口径是「只返回启用项、按类型分组」；学院是「中文名即数据」的特殊一类，
 * 还带学生 / 组织的占用校验与跨模块计数，混进去会让两个不相干的口径互相牵制
 * （字典查询必须只返回启用项，而学院管理必须能看到停用项）。
 */
public interface CollegeService {

    /**
     * 取全部学院（含停用项），并补上学生数与组织数。
     *
     * @return 学院列表，按 sort 升序、再按 id 升序
     */
    List<CollegeVO> listColleges();

    /**
     * 新增学院。
     *
     * @param dto 学院名称与排序
     */
    void createCollege(CollegeSaveDTO dto);

    /**
     * 删除学院（物理删除）。
     *
     * <p>sys_dict 没有 deleted 列，字典行的下架手段本来是停用；这里之所以还提供删除，
     * 是为了清掉「建错了、还没被任何数据引用」的行。删除前必须挡住还有学生或组织的学院，
     * 否则会留下一批学院名查不到的档案（见实现里的占用校验）。
     *
     * @param id 学院对应的 sys_dict.id
     */
    void deleteCollege(Long id);

    /**
     * 启用 / 停用学院。
     *
     * <p><b>停用不校验占用</b>：学院合并或停招时，硬删会被占用校验挡住，
     * 只能靠停用让它从注册页下拉里消失（{@link DictService#listByType} 只返回
     * {@code status = 1} 的项），已有学生与组织的数据不受影响。
     *
     * @param id  学院对应的 sys_dict.id
     * @param dto 目标状态
     */
    void updateStatus(Long id, StatusUpdateDTO dto);
}
