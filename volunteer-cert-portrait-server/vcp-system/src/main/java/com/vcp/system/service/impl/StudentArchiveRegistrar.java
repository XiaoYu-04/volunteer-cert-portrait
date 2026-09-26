package com.vcp.system.service.impl;

import com.vcp.common.exception.BusinessException;
import com.vcp.common.exception.ErrorCodeEnum;
import com.vcp.system.entity.StudentInfo;
import com.vcp.system.mapper.StudentInfoMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static com.vcp.common.util.StringUtils.hasText;

/**
 * 学生档案建档入口：账号创建时补一行 {@code student_info}。
 *
 * <p><b>为什么需要它</b>：{@code student_info} 此前只有读路径、没有任何写入，
 * 注册与管理员新增用户都只写 {@code sys_user}，于是新账号会话里的 studentId 恒为 null，
 * 学生端「我的报名 / 我的时长 / 我的画像」一律报 10003。建档收敛到本类一处，
 * 是为了让「学号怎么生成、等级要不要写」这些规则只有一个落点。
 *
 * <p><b>为什么是独立组件而不是 {@code StudentService} 的方法</b>：
 * {@code StudentService} 是给 Controller 用的查询契约（只出 VO），建档挂上去
 * 等于把它变成对外写接口；而本类的调用方只有注册与管理员新增用户两条写路径，
 * 都在本包内。与 {@link RoleResolver} 的差别在失败语义：RoleResolver 不抛业务异常、
 * 由调用方决定怎么处理，本类自己抛 —— 建档失败的处置方式只有一种，
 * 两个调用点各写一遍只会多两处漏写的可能。
 *
 * <p><b>刻意不做的两件事</b>：不写 {@code student_profile}（画像快照由 vcp-portrait
 * 的重算任务生成），不写 {@code public_welfare_level}（理由见 {@link #ensureArchive(Long, String, String)}）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StudentArchiveRegistrar {

    /** 占位学号前缀：与种子数据的 8 位纯数字学号（如 20230001）一眼可区分 */
    private static final String PLACEHOLDER_PREFIX = "S";

    /** 占位学号里 userId 的位数；不足补零，超过不截断（位数变长仍然唯一） */
    private static final int PLACEHOLDER_WIDTH = 6;

    private final StudentInfoMapper studentInfoMapper;

    /**
     * 为指定用户建一份学生档案，已有档案直接复用（幂等）。
     *
     * <p><b>必须在调用方的事务里执行</b>：两条调用路径都是「先插 sys_user、再建档」，
     * 建档失败要连账号一起回滚。若各自独立提交，库里会留下「有账号、无档案」的孤儿 ——
     * 那种账号能登录，但学生端每个页面都报 10003，只能人工补数据。
     *
     * <p><b>四个字段的口径</b>：
     * <ul>
     *   <li>{@code student_no} 优先写调用方传入的学号：两条调用路径（学生自助注册
     *       {@code AuthServiceImpl.register}、管理员新增用户
     *       {@code UserServiceImpl.createUser}）现在都收学号，且各自在调用前校验过格式
     *       （{@code ^[0-9]{4,20}$}）与唯一性，这里不再重复判。
     *       <b>传空时退回占位学号 {@code S + 六位零填充的 userId}</b>（userId=42 → S000042）：
     *       该列 NOT NULL UNIQUE 必须给值，而由主键派生可保证唯一、不必额外查重，
     *       也不会与种子数据的纯数字学号冲突。这条兜底路径服务于历史调用与将来可能出现的
     *       「系统内部建号」场景 —— 那种场合没有人能提供学号，硬要求必填只会把建档整条断掉。</li>
     *   <li>{@code total_duration} 写 0：它是累计时长的权威值，只由 vcp-certification
     *       在时长审核通过时累加，建号时不该有别的初值。</li>
     *   <li>{@code public_welfare_level} <b>留空</b>：等级阈值枚举 PublicWelfareLevelEnum
     *       在 vcp-portrait，而依赖方向是 vcp-portrait → vcp-system，本模块反向依赖
     *       会把依赖拧成环；在这里写死「普通志愿者」则等于再造一处硬编码阈值
     *       （B18 已把散落的硬编码阈值记为技术债）。等级由 vcp-portrait 的每日重算任务
     *       按 {@code PublicWelfareLevelEnum.of(0)} 的口径补写，重算前该列为 null，
     *       画像侧读取时会按累计时长兜底算一次等级，不会显示成空白。</li>
     *   <li>{@code college} 写注册表单提交的学院（{@code AuthServiceImpl} 已校验过它命中
     *       字典里的启用项）。这是该列目前唯一的写入入口：学院是「按学院统计」的分组键，
     *       缺了就落进空分组。两条调用路径都收学院（注册表单、管理员新增用户表单），
     *       各自在调用前用 {@code DictService.containsEnabled} 校验过，这里不再重复判空 ——
     *       真要收下 null，说明上游漏了校验，属于该修上游的 bug，不该靠建档层静默兜住。</li>
     * </ul>
     *
     * @param userId    用户 id（{@code sys_user.id}）
     * @param college   学院名，来自注册表单或管理员新增用户表单
     * @param studentNo 学号，来自注册表单或管理员新增用户表单；<b>允许为空</b>，
     *                  为空时按 {@code S + 六位零填充的 userId} 生成占位学号
     * @return 该用户的档案实体（含 id 与学号，调用方拿去写会话）；已有档案时返回库里那一行
     * @throws BusinessException 用户 id 为空（10001）、该账号已有档案或学号已被占用（10000）
     */
    @Transactional(rollbackFor = Exception.class)
    public StudentInfo ensureArchive(Long userId, String college, String studentNo) {
        if (userId == null) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "建档缺少用户 id");
        }

        // 先查后插：历史数据（老账号没档案、之后才补）与重复调用都在这里复用，不指望唯一约束兜。
        // 查询自带 @TableLogic 的 deleted = 0，逻辑删除过的档案查不出来、会落到下面的插入分支，
        // 那是预期行为：行还在，UNIQUE(user_id) 挡住重复插入并转成可读提示（见 catch）。
        // 已有档案时直接返回，**不把新学号覆盖上去**：本接口是建档不是补录，
        // 覆盖会绕过调用方的学号查重（不匹配时直接撞库里的唯一约束，用户拿到的是系统错误），
        // 也会让「改学号」这个动作从一条可审计的补录路径变成建档的副作用。补录入口另开。
        StudentInfo existing = StudentArchiveQueries.findByUserId(studentInfoMapper, userId);
        if (existing != null) {
            return existing;
        }

        StudentInfo archive = new StudentInfo();
        archive.setUserId(userId);
        // 学号只判空、不做格式校验：格式与唯一性由各自的上游负责（见本方法上 student_no 那段），
        // 建档层再判一遍只会在两处留下可能漂移的规则。
        archive.setStudentNo(hasText(studentNo) ? studentNo.trim() : placeholderStudentNo(userId));
        archive.setCollege(college);
        archive.setTotalDuration(BigDecimal.ZERO);
        try {
            studentInfoMapper.insert(archive);
        } catch (DuplicateKeyException e) {
            // 并发下第二次插入被唯一约束（user_id / student_no）挡下。
            // 这里不再回查一行来复用：PostgreSQL 里一条语句失败会把整个事务置为 aborted，
            // 回查只会再抛一次 25P02，盖掉真正的原因。
            // 原始异常进日志（约束名在里面，能区分撞的是 user_id 还是 student_no），
            // 对外只给可读文案，不把数据库异常抛到接口上。
            // 撞 student_no 时这句文案同样成立：唯一约束就是「一人一号」的最终守门人，
            // 能把并发下两个请求同时通过上游查重、抢同一个学号的情况接住。
            log.warn("[建档] 唯一约束冲突，userId={}, studentNo={}", userId, archive.getStudentNo(), e);
            throw new BusinessException(ErrorCodeEnum.SYSTEM_ERROR, "该账号已有学生档案或学号已被占用，请联系学校管理员核对数据");
        }
        return archive;
    }

    /**
     * 生成占位学号：{@code S + 六位零填充的 userId}。
     *
     * <p>不用 {@code String.format("%06d")}：它按默认 Locale 取数字字符，
     * 在阿拉伯语等区域会写出非 ASCII 数字。手写补零与 Locale 无关。
     *
     * @param userId 用户 id，非 null
     * @return 占位学号，如 S000042
     */
    private static String placeholderStudentNo(Long userId) {
        String digits = String.valueOf(userId);
        return PLACEHOLDER_PREFIX
                + "0".repeat(Math.max(0, PLACEHOLDER_WIDTH - digits.length()))
                + digits;
    }
}
