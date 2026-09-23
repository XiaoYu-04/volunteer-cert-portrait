package com.vcp.portrait.enums;

import java.math.BigDecimal;

/**
 * 公益等级（六档）与分档阈值 —— 运行期的<b>单一事实来源</b>。
 *
 * <p>阈值取自 {@code docs/公益等级与标签规则方案.md} 的「方案 A 演示尺度」，已由团队拍板：
 * <pre>
 *   普通志愿者  x &lt; 3
 *   一星志愿者  3  ≤ x &lt; 6
 *   二星志愿者  6  ≤ x &lt; 10
 *   三星志愿者  10 ≤ x &lt; 20
 *   四星志愿者  20 ≤ x &lt; 40
 *   五星志愿者  x ≥ 40      （单位：小时）
 * </pre>
 * 方案 A 的取舍：3 小时即一星，比现实标准（中国青年志愿者星级标准一星 100 小时）低得多，
 * 但演示数据下能同时出现 4 个档位，答辩时等级分布图有层次。实际部署时只改本枚举的
 * {@code minHours}，不要散落改到别处。
 *
 * <p><b>为什么等级名存中文（待办 B18 的取舍）</b>：{@code student_info.public_welfare_level}
 * 存的是中文等级名（如「五星志愿者」），与「状态一律英文码 + {@code sys_dict} 翻译」的约定
 * 不一致。本轮<b>保留中文</b>，理由有三：
 * <ol>
 *   <li>前端契约把等级当<b>展示文案</b>用（{@code PortraitView} 直接渲染 {@code row.level}，
 *       {@code MyPortraitView} 把它当印章文字），改存码值会让这两个页面直接显示码值；</li>
 *   <li>改走字典需要往 {@code sys_dict} 插 {@code public_welfare_level} 类型的种子行，
 *       而种子数据在 {@code sql/03_init_data.sql}（本轮约定不动 {@code sql/} 目录），
 *       在代码里写启动期补数据既绕、又会与脚本抢同一份数据；</li>
 *   <li>{@code sql/04_demo_data.sql} 的等级回填写的也是中文名，改存码值会让「脚本跑一遍、
 *       接口跑一遍」得到两种值，反而制造第二处口径分歧。</li>
 * </ol>
 * 折中做法：本枚举把「码值」也定义出来（{@link #getCode()}），经 {@code PortraitVO.levelCode}
 * 一并返回，前端可用它做色调映射，将来字典化迁移时也不必再推一遍码表。
 *
 * <p><b>与 SQL 的一致性</b>：{@code sql/04_demo_data.sql} 的
 * {@code UPDATE student_info SET public_welfare_level = CASE ...} 硬编码了同一套阈值，
 * 两处必须同步修改 —— 这是 B18 点名的「单一事实来源」问题，见 {@link #of(BigDecimal)}。
 */
public enum PublicWelfareLevelEnum {

    /** 普通志愿者：不足 3 小时 */
    NORMAL("普通志愿者", "NORMAL", new BigDecimal("0")),

    /** 一星志愿者：3（含）~ 6 小时 */
    ONE_STAR("一星志愿者", "ONE_STAR", new BigDecimal("3")),

    /** 二星志愿者：6（含）~ 10 小时 */
    TWO_STAR("二星志愿者", "TWO_STAR", new BigDecimal("6")),

    /** 三星志愿者：10（含）~ 20 小时 */
    THREE_STAR("三星志愿者", "THREE_STAR", new BigDecimal("10")),

    /** 四星志愿者：20（含）~ 40 小时 */
    FOUR_STAR("四星志愿者", "FOUR_STAR", new BigDecimal("20")),

    /** 五星志愿者：40 小时及以上 */
    FIVE_STAR("五星志愿者", "FIVE_STAR", new BigDecimal("40"));

    /** 等级中文名，即 {@code student_info.public_welfare_level} 的入库值 */
    private final String levelName;

    /** 等级英文码，供前端做色调映射与后续字典化迁移（不入库） */
    private final String code;

    /** 该档位的时长下限（小时，含） */
    private final BigDecimal minHours;

    PublicWelfareLevelEnum(String levelName, String code, BigDecimal minHours) {
        this.levelName = levelName;
        this.code = code;
        this.minHours = minHours;
    }

    /**
     * 按累计有效时长定档。
     *
     * <p>取「满足条件的最高档位」，与 {@code sql/04_demo_data.sql} 里
     * {@code WHEN total_duration >= 40 THEN ... WHEN >= 20 THEN ...} 的写法等价；
     * 时长为空按 0 处理（SQL 的 {@code ELSE} 分支同样落到「普通志愿者」）。
     *
     * @param hours 累计有效时长（小时），权威值来自 {@code student_info.total_duration}
     * @return 对应等级，永不为 null
     */
    public static PublicWelfareLevelEnum of(BigDecimal hours) {
        BigDecimal value = hours == null ? BigDecimal.ZERO : hours;
        PublicWelfareLevelEnum matched = NORMAL;
        for (PublicWelfareLevelEnum level : values()) {
            if (value.compareTo(level.minHours) >= 0) {
                matched = level;
            }
        }
        return matched;
    }

    /**
     * 按库里的值反查枚举，中文名与英文码都能识别。
     *
     * <p>兼容英文码是给「将来改存码值」留的无痛迁移路径：迁移当天老数据是中文名、
     * 新数据是码值，本方法两者都能翻译，不会出现半截数据读不出来。
     *
     * @param value 等级名或等级码，允许为 null
     * @return 对应枚举；无法识别时返回 null（调用方决定回退还是留空）
     */
    public static PublicWelfareLevelEnum ofName(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        for (PublicWelfareLevelEnum level : values()) {
            if (level.levelName.equals(trimmed) || level.code.equalsIgnoreCase(trimmed)) {
                return level;
            }
        }
        return null;
    }

    /**
     * 等级中文名。
     *
     * @return 中文等级名，如「五星志愿者」
     */
    public String getLevelName() {
        return levelName;
    }

    /**
     * 等级英文码。
     *
     * @return 英文码，如 {@code FIVE_STAR}
     */
    public String getCode() {
        return code;
    }

    /**
     * 该档位的时长下限。
     *
     * @return 下限小时数（含），普通志愿者为 0
     */
    public BigDecimal getMinHours() {
        return minHours;
    }
}
