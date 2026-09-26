package com.vcp.portrait.enums;

/**
 * 公益标签（8 类）与判定口径 —— 运行期的<b>单一事实来源</b>。
 *
 * <p>标签规则取自 {@code docs/公益等级与标签规则方案.md}（已拍板），共三类：
 * <ol>
 *   <li><b>热心志愿者</b>：累计有效时长 &gt; 0 小时；</li>
 *   <li><b>长期坚持型</b>：已完成（{@code COMPLETED}）活动次数 ≥ 3
 *       （阈值取 3 而非 5：演示数据里单人最多 4 次已完成活动，取 5 该标签永不出现）；</li>
 *   <li><b>类型标签</b>：取参与次数最多的活动分类映射而来，<b>互斥、最多一个</b>，
 *       并列时按分类 id 升序打破（不能用中文名排序：中文串排序依赖数据库 collation，
 *       不同机器结果会不一致）。不设参与次数下限。</li>
 * </ol>
 * 「助老服务型」「文化传播型」是方案文档未给出、由团队新增的两个标签 ——
 * 不补的话这两类参与者拿不到任何类型标签，而文化传播恰是人次最多的分类。
 *
 * <p><b>为什么以 {@code activity_category.code} 为键（待办 B18 的最脆弱处）</b>：
 * {@code sql/04_demo_data.sql} 的标签生成是以<b>中文分类名</b>为键的
 * （{@code CASE (分类名) WHEN '校园服务' THEN ...}），而分类名可以被
 * {@code PUT /v1/categories/{id}} 改名 —— 改名之后映射静默失配，学生的类型标签无声消失，
 * 数据库层没有任何保护。因此本枚举以 {@code code} 为键（{@code sql/06_backend_gap_fix2.sql}
 * 已回填 6 条分类的编码），中文名只作<b>兜底</b>：某条分类没填 code、或编码是新分类的
 * {@code CUSTOM_n} 时，先退回中文名匹配，仍然匹配不上则<b>不静默丢弃</b> ——
 * 由 {@code PortraitServiceImpl} 汇总成一条 WARN 日志（分类 id + 编码 + 名称）留给排查。
 *
 * <p>与 {@code sql/04_demo_data.sql} 的标签口径逐条一致（顺序也一致：
 * 热心志愿者 → 长期坚持型 → 类型标签），差异只有「映射键从中文名换成 code + 名称兜底」。
 */
public enum PortraitTagEnum {

    /** 热心志愿者：累计有效时长大于 0 小时 */
    ENTHUSIAST("热心志愿者", "累计有效志愿时长大于 0 小时", null, null),

    /** 长期坚持型：已完成活动 ≥ 3 场 */
    PERSISTENT("长期坚持型", "已完成志愿活动 ≥ 3 场", null, null),

    /** 校园服务型：参与最多的分类为「校园服务」 */
    CAMPUS("校园服务型", "参与最多的活动分类为校园服务", "CAMPUS", "校园服务"),

    /** 社区服务型：参与最多的分类为「社区服务」 */
    COMMUNITY("社区服务型", "参与最多的活动分类为社区服务", "COMMUNITY", "社区服务"),

    /** 环保行动型：参与最多的分类为「环保公益」 */
    ENVIRONMENT("环保行动型", "参与最多的活动分类为环保公益", "ENVIRONMENT", "环保公益"),

    /** 大型活动型：参与最多的分类为「大型赛事」 */
    EVENT("大型活动型", "参与最多的活动分类为大型赛事", "EVENT", "大型赛事"),

    /** 助老服务型：参与最多的分类为「助老服务」（方案文档未给出，团队新增） */
    ELDERLY("助老服务型", "参与最多的活动分类为助老服务", "ELDERLY", "助老服务"),

    /** 文化传播型：参与最多的分类为「文化传播」（方案文档未给出，团队新增） */
    CULTURE("文化传播型", "参与最多的活动分类为文化传播", "CULTURE", "文化传播");

    /** 标签名，即 {@code student_profile.tags} 里逗号分隔的取值 */
    private final String tagName;

    /** 标签释义，用于画像标签分布接口的说明文案 */
    private final String description;

    /** 对应活动分类的编码（{@code activity_category.code}），行为标签为 null */
    private final String categoryCode;

    /** 对应活动分类的名称，仅作 code 缺失时的兜底键，行为标签为 null */
    private final String categoryName;

    PortraitTagEnum(String tagName, String description, String categoryCode, String categoryName) {
        this.tagName = tagName;
        this.description = description;
        this.categoryCode = categoryCode;
        this.categoryName = categoryName;
    }

    /**
     * 是否为「类型标签」（由活动分类映射而来）。
     *
     * <p>类型标签互斥且最多一个，展示主标签时优先取它。
     *
     * @return 类型标签返回 true，「热心志愿者」「长期坚持型」返回 false
     */
    public boolean isTypeTag() {
        return categoryCode != null;
    }

    /**
     * 按分类编码反查类型标签。
     *
     * @param code 分类编码（{@code activity_category.code}），允许为 null
     * @return 对应标签；编码为空或不认识时返回 null
     */
    public static PortraitTagEnum byCategoryCode(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        String normalized = code.trim().toUpperCase();
        for (PortraitTagEnum tag : values()) {
            if (tag.categoryCode != null && tag.categoryCode.equals(normalized)) {
                return tag;
            }
        }
        return null;
    }

    /**
     * 按分类中文名反查类型标签（仅作兜底）。
     *
     * <p>只在分类没有 code、或 code 不认识时才走到这里。中文名可被改名，
     * 因此它不能当主键，但作为兜底比「直接丢标签」好得多。
     *
     * @param name 分类名称，允许为 null
     * @return 对应标签；名称为空或不认识时返回 null
     */
    public static PortraitTagEnum byCategoryName(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        String trimmed = name.trim();
        for (PortraitTagEnum tag : values()) {
            if (tag.categoryName != null && tag.categoryName.equals(trimmed)) {
                return tag;
            }
        }
        return null;
    }

    /**
     * 解析某活动分类对应的类型标签：先按 code，再按中文名兜底。
     *
     * @param code 分类编码
     * @param name 分类名称
     * @return 对应类型标签；两者都匹配不上时返回 null（调用方需记 WARN，不要静默丢弃）
     */
    public static PortraitTagEnum resolveTypeTag(String code, String name) {
        PortraitTagEnum byCode = byCategoryCode(code);
        return byCode != null ? byCode : byCategoryName(name);
    }

    /**
     * 判断本标签是否对应给定的活动分类（同样先比 code、再比名称）。
     *
     * <p>读取侧算「社区服务占比」「环保行动占比」时用它定位分类，
     * 保证与写入侧的映射口径完全一致。
     *
     * @param code 分类编码
     * @param name 分类名称
     * @return 是本标签对应的分类时返回 true
     */
    public boolean matchesCategory(String code, String name) {
        return isTypeTag() && this == resolveTypeTag(code, name);
    }

    /**
     * 按标签名反查枚举。
     *
     * @param tagName 标签名，允许为 null
     * @return 对应标签；名称为空或不认识时返回 null
     */
    public static PortraitTagEnum ofTagName(String tagName) {
        if (tagName == null || tagName.isBlank()) {
            return null;
        }
        String trimmed = tagName.trim();
        for (PortraitTagEnum tag : values()) {
            if (tag.tagName.equals(trimmed)) {
                return tag;
            }
        }
        return null;
    }

    /**
     * 标签名。
     *
     * @return 标签名，如「长期坚持型」
     */
    public String getTagName() {
        return tagName;
    }

    /**
     * 标签释义。
     *
     * @return 判定条件的可读说明
     */
    public String getDescription() {
        return description;
    }
}
