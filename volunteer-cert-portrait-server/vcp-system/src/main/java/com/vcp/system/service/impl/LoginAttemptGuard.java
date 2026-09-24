package com.vcp.system.service.impl;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 登录失败计数与账号锁定的看门人。
 *
 * <p>规则：同一用户名连续 5 次失败即锁定 15 分钟，锁定期间即使口令正确也拒绝登录，
 * 登录成功清零。用户名按 {@code trim()} 后的值作为 key，与登录查库时的取值口径一致。
 *
 * <h3>三个取舍</h3>
 * <ol>
 *   <li><b>单实例内存实现，重启即清空</b>。多实例部署时各节点各算各的，
 *       锁定会被"换个节点重试"绕过。当前是单实例部署，先用内存扛住脚本化的口令爆破；
 *       真要横向扩容时把本类换成 Redis 实现即可，调用方不用动。</li>
 *   <li><b>既不落表列也不上 Redis</b>。给 {@code sys_user} 加"失败次数 / 锁定至"两列要动
 *       {@code sql/02_schema.sql} 与 ER 图，还得让每次登录失败都写一次库，
 *       代价大于收益；工程目前也没有引入 Redis，为这一个功能引一套中间件不划算。</li>
 *   <li><b>对不存在的用户名同样计数</b>。否则"连续 5 次失败会被锁"只对真实账号成立，
 *       攻击者拿"第 5 次之后有没有被锁"一测就知道账号存不存在，
 *       登录接口那套防枚举的文案也就白设计了。</li>
 * </ol>
 *
 * <p>计数不是账号的永久属性：超过观察窗口（与锁定时长同为 15 分钟）没有新的失败，
 * 旧计数作废。否则用户一个月前失败 4 次、今天再错 1 次就被锁，属于误伤。
 */
@Component
public class LoginAttemptGuard {

    /** 连续失败多少次触发锁定 */
    private static final int MAX_FAILURES = 5;

    /** 锁定时长（分钟） */
    private static final long LOCK_MINUTES = 15;

    /** 观察窗口（分钟）：超过这么久没有新的失败，旧计数作废，重新从 1 开始数 */
    private static final long OBSERVE_WINDOW_MINUTES = 15;

    /**
     * 条目数上限。key 来自用户提交的用户名，攻击者可以用随机用户名把 Map 撑大，
     * 所以必须有清理动作（见 {@link #evictIfOversized()}）。
     */
    private static final int MAX_ENTRIES = 10000;

    /** 用户名 → 失败情况。key 为 trim 过的用户名，绝不为 null */
    private final Map<String, Attempt> attempts = new ConcurrentHashMap<>();

    /**
     * 账号当前是否处于锁定期。
     *
     * @param username 用户名
     * @return 锁定中返回 true
     */
    public boolean isLocked(String username) {
        Attempt attempt = attempts.get(normalize(username));
        return attempt != null && attempt.lockedUntil != null && attempt.lockedUntil.isAfter(Instant.now());
    }

    /**
     * 取剩余锁定分钟数。
     *
     * <p>向上取整且至少为 1：剩 20 秒时显示「1 分钟」而不是「0 分钟」，
     * 用户按提示等一分钟即可，不必再试一次再被拒。
     *
     * @param username 用户名
     * @return 锁定中的剩余分钟数（至少 1）；未锁定时返回 0
     */
    public long remainingMinutes(String username) {
        Attempt attempt = attempts.get(normalize(username));
        if (attempt == null || attempt.lockedUntil == null) {
            return 0;
        }
        long seconds = Duration.between(Instant.now(), attempt.lockedUntil).getSeconds();
        if (seconds <= 0) {
            return 0;
        }
        return Math.max(1, (seconds + 59) / 60);
    }

    /**
     * 记一次失败，达到阈值即进入锁定期。
     *
     * <p>用 {@code ConcurrentHashMap#compute} 而不是"先查再改"：compute 对同一个 key 是原子的，
     * 并发提交同一用户名的多次失败不会互相覆盖计数 —— 否则攻击者并发打请求就能把计数冲散，
     * 永远够不到阈值，锁定形同虚设。
     *
     * @param username 用户名；不存在的账号也要记，理由见类注释第三条取舍
     */
    public void recordFailure(String username) {
        Instant now = Instant.now();
        attempts.compute(normalize(username), (key, current) -> {
            Attempt attempt = current == null ? new Attempt() : current;
            if (attempt.lockedUntil != null && attempt.lockedUntil.isAfter(now)) {
                // 锁定期内继续重试不再累加：否则解锁瞬间计数仍是满的，会立刻再锁一轮
                attempt.lastFailureAt = now;
                return attempt;
            }
            if (attempt.lastFailureAt == null
                    || attempt.lastFailureAt.isBefore(now.minus(Duration.ofMinutes(OBSERVE_WINDOW_MINUTES)))) {
                attempt.failures = 0;
            }
            attempt.failures++;
            attempt.lastFailureAt = now;
            if (attempt.failures >= MAX_FAILURES) {
                attempt.lockedUntil = now.plus(Duration.ofMinutes(LOCK_MINUTES));
            }
            return attempt;
        });
        evictIfOversized();
    }

    /**
     * 清除某个用户名的失败计数与锁定状态，登录成功时调用。
     *
     * @param username 用户名
     */
    public void clear(String username) {
        attempts.remove(normalize(username));
    }

    /**
     * 条目数超限时清理「未锁定且已过观察窗口」的条目。
     *
     * <p>只删同时满足这两条的：锁定中的删掉等于提前解锁，观察窗口内的删掉等于
     * 把连续失败重新算成第一次。清理完仍可能超限（攻击者同时在用上万个用户名失败），
     * 那也只是每次失败多一遍 O(条目数) 的扫描，相对一次 BCrypt 校验（约 50-100ms）可忽略。
     *
     * <p>并发下迭代器是弱一致的，可能漏掉清理期间新插入的条目，下次失败会再触发一遍，
     * 不影响正确性。
     */
    private void evictIfOversized() {
        if (attempts.size() <= MAX_ENTRIES) {
            return;
        }
        Instant now = Instant.now();
        Instant expireBefore = now.minus(Duration.ofMinutes(OBSERVE_WINDOW_MINUTES));
        Iterator<Map.Entry<String, Attempt>> iterator = attempts.entrySet().iterator();
        while (iterator.hasNext()) {
            Attempt attempt = iterator.next().getValue();
            boolean locked = attempt.lockedUntil != null && attempt.lockedUntil.isAfter(now);
            boolean expired = attempt.lastFailureAt == null || attempt.lastFailureAt.isBefore(expireBefore);
            if (!locked && expired) {
                iterator.remove();
            }
        }
    }

    /**
     * 统一 key 口径。
     *
     * <p>登录查库用的是 trim 后的用户名，计数必须用同一个，否则「 alice」与「alice」
     * 会被当成两个账号各算一套计数，一个账号就有两倍的尝试机会。
     *
     * @param username 用户名
     * @return trim 后的用户名；为 null 时返回空串（ConcurrentHashMap 不接受 null 键）
     */
    private static String normalize(String username) {
        return username == null ? "" : username.trim();
    }

    /** 单个用户名的失败情况，字段只在 compute 的原子块里改，故不必加锁 */
    private static final class Attempt {

        /** 观察窗口内的连续失败次数 */
        private int failures;

        /** 最近一次失败时间，用于判断观察窗口是否已过 */
        private Instant lastFailureAt;

        /** 锁定截止时间，未锁定为 null */
        private Instant lockedUntil;
    }
}
