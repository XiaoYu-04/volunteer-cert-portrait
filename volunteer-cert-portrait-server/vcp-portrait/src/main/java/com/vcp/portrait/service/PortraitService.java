package com.vcp.portrait.service;

import com.vcp.common.result.PageResult;
import com.vcp.portrait.dto.PortraitQuery;
import com.vcp.portrait.dto.PortraitRecomputeDTO;
import com.vcp.portrait.vo.PortraitRecomputeVO;
import com.vcp.portrait.vo.PortraitVO;
import com.vcp.portrait.vo.TagDistributionVO;

import java.util.List;

/**
 * 公益画像服务：画像读取、标签分布、画像重算（公益等级 + 标签）。
 *
 * <p>规则口径见 {@code docs/公益等级与标签规则方案.md}（已拍板），代码里的单一事实来源是
 * {@link com.vcp.portrait.enums.PublicWelfareLevelEnum}（六档阈值）与
 * {@link com.vcp.portrait.enums.PortraitTagEnum}（八个标签与分类映射）。
 *
 * <p>读接口的数据范围完全由登录态决定：「我的画像」只认会话里的 studentId，
 * 不接受前端传入的学生 id（待办 B19 点名的越权风险 —— 前端 mock 里
 * {@code eq(undefined)} 会直接放行全量，后端不能照抄这种行为）。
 */
public interface PortraitService {

    /**
     * 取当前登录学生本人的画像。
     *
     * <p>学生 id 只从 Sa-Token 会话读取，<b>忽略任何前端传入的 studentId</b>。
     *
     * @return 本人画像，含维度得分
     * @throws com.vcp.common.exception.BusinessException 未登录（20001）或画像尚未生成（50001）
     */
    PortraitVO getMyPortrait();

    /**
     * 分页查询画像明细（学校管理端）。
     *
     * @param query 查询条件：关键字（姓名/学号）、学院、标签、分页参数
     * @return 画像分页结果
     */
    PageResult<PortraitVO> listPortraits(PortraitQuery query);

    /**
     * 取指定学生的画像（学校管理端）。
     *
     * @param studentId 学生档案 id
     * @return 画像详情
     * @throws com.vcp.common.exception.BusinessException studentId 为空（10003）或画像尚未生成（50001）。
     *         学生档案不存在时同样落到 50001：与前端 mock 的 {@code fail(50001, '画像尚未生成')} 一致，
     *         也不额外透露「这个学生 id 到底存不存在」
     */
    PortraitVO getPortrait(Long studentId);

    /**
     * 取画像标签分布（八类标签各有多少人）。
     *
     * <p>数据来自 {@code student_profile.tags} 这份画像快照，与明细列表同源；
     * 重算之后两者一起刷新，不会出现「列表一个标签、分布图另一个标签」。
     * 统计在 SQL 里按标签串分组完成（投影带恒非空列，避免可空列整行为 NULL 时
     * MyBatis 把该行变成 null 元素），Service 只负责把「标签组合」拆成单个标签累加人数。
     *
     * @return 标签分布，按人数降序、并列时按标签体系的固定顺序
     */
    List<TagDistributionVO> getDistribution();

    /**
     * 重算画像：按 {@code student_info.total_duration} 定公益等级，按已完成活动聚合公益标签。
     *
     * <p>这是画像的写入口，三条调用路径：手动触发（学校管理员）、每日定时兜底，
     * 以及将来时长审核通过后的自动触发（见待办 B10 的说明）。
     *
     * @param dto 重算范围；为 null 或 studentId 为空时重算全部学生
     * @return 扫描人数与实际写入份数
     * @throws com.vcp.common.exception.BusinessException 指定的学生档案不存在（10003）
     */
    PortraitRecomputeVO recompute(PortraitRecomputeDTO dto);
}
