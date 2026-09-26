package com.vcp.framework.util;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * 口令哈希工具（BCrypt）。
 *
 * <p>为什么落在 vcp-framework：{@code spring-security-crypto} 依赖只声明在本模块，
 * vcp-system 通过依赖 vcp-framework 间接拿到它，不需要各自再引一遍。
 * 全工程只此一处做哈希，业务代码不得自己 new 编码器、更不得自己拼哈希串。
 *
 * <p><b>密文形态</b>：{@code $2a$10$} + 22 位盐 + 31 位摘要，共 60 字符，
 * 与 {@code sys_user.password VARCHAR(100)} 相容。同一明文每次哈希结果都不同
 * （盐随机），因此<b>不能拿密文做相等比较</b>，只能走 {@link #matches}。
 *
 * <p><b>不做明文兜底</b>：{@link #matches} 遇到库里还是明文的记录一律返回 false，
 * 而不是退化成字符串相等 —— 留这样一条后路等于加密白做。
 * 历史明文数据由 {@code sql/08_password_bcrypt.sql} 一次性刷成密文。
 *
 * <p>本类是纯函数工具，不可实例化。
 */
public final class PasswordUtils {

    /** BCrypt 强度（轮数取 2 的幂指数），10 是 Spring Security 的默认值，单次哈希约 50-100ms */
    private static final int BCRYPT_STRENGTH = 10;

    /** 口令最短长度，与注册接口既有校验一致 */
    public static final int MIN_LENGTH = 6;

    /**
     * 口令最长长度。
     *
     * <p>BCrypt 算法只取前 72 字节，超出部分被静默丢弃；Spring Security 的
     * {@code BCryptPasswordEncoder.encode} 对超长明文直接抛 IllegalArgumentException。
     * 与其让用户撞到 500，不如在入口用业务错误拦掉，故取一个远小于 72 字节的上限。
     */
    public static final int MAX_LENGTH = 32;

    /** BCrypt 密文固定前缀，$2a/$2b/$2y 三种版本号都算 */
    private static final String BCRYPT_PREFIX = "$2";

    /** BCrypt 密文固定长度（$2a$10$ + 22 位盐 + 31 位摘要） */
    private static final int BCRYPT_LENGTH = 60;

    /**
     * 编码器本身线程安全（内部只用 SecureRandom 与局部变量），
     * 全工程共用一个实例即可，每次 new 会重复解析配置、纯浪费。
     */
    private static final BCryptPasswordEncoder ENCODER = new BCryptPasswordEncoder(BCRYPT_STRENGTH);

    private PasswordUtils() {
    }

    /**
     * 把明文口令哈希成密文。
     *
     * @param rawPassword 明文口令，不得为空
     * @return 60 字符的 BCrypt 密文
     * @throws IllegalArgumentException 明文为空时抛出
     */
    public static String encode(String rawPassword) {
        if (rawPassword == null || rawPassword.isEmpty()) {
            throw new IllegalArgumentException("明文口令不能为空");
        }
        return ENCODER.encode(rawPassword);
    }

    /**
     * 校验明文口令与库中密文是否匹配。
     *
     * @param rawPassword     用户提交的明文口令
     * @param encodedPassword 库中存的密文
     * @return 匹配返回 true；任一为空、或库中存的不是 BCrypt 密文时返回 false
     */
    public static boolean matches(String rawPassword, String encodedPassword) {
        if (rawPassword == null || rawPassword.isEmpty()) {
            return false;
        }
        if (!isEncoded(encodedPassword)) {
            // 走不到编码器就不会有 "Encoded password does not look like BCrypt" 的告警噪音
            return false;
        }
        return ENCODER.matches(rawPassword, encodedPassword);
    }

    /**
     * 判断一个值是否已经是 BCrypt 密文。
     *
     * <p>用途有二：识别需要刷密文的历史明文记录；避免把明文喂给编码器。
     *
     * @param value 待判断的值
     * @return 是 BCrypt 密文返回 true
     */
    private static boolean isEncoded(String value) {
        return value != null && value.length() == BCRYPT_LENGTH && value.startsWith(BCRYPT_PREFIX);
    }

    /**
     * 校验明文口令是否符合口令策略。
     *
     * <p>注册、本人改密、管理员新增用户与重置口令四处共用同一套规则，
     * 分散写会在某个入口漏掉一条。
     *
     * @param rawPassword 明文口令
     * @return 合法返回 null，否则返回可直接展示给用户的文案
     */
    public static String checkPolicy(String rawPassword) {
        if (rawPassword == null || rawPassword.isEmpty()) {
            return "密码不能为空";
        }
        if (rawPassword.length() < MIN_LENGTH) {
            return "密码至少 " + MIN_LENGTH + " 位";
        }
        if (rawPassword.length() > MAX_LENGTH) {
            return "密码最多 " + MAX_LENGTH + " 位";
        }
        return null;
    }
}
