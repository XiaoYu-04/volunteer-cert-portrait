package com.vcp.system.service;

/**
 * 组织信息查询端口：登录时按用户 id 反查其所属志愿组织。
 *
 * <p><b>为什么需要这个接口（而不是直接查 org_info 表）</b>：
 * 模块依赖方向是 {@code vcp-system → vcp-framework → vcp-common}，而
 * {@code vcp-org} 依赖 {@code vcp-system}。也就是说 vcp-system 处在更底层，
 * <b>不能反向依赖 vcp-org</b>，碰不到 org_info 的实体与 Mapper
 * （项目约定见 CLAUDE.md：禁止反向依赖，跨模块只调对方 Service 接口）。
 *
 * <p>但登录必须往会话里写 orgId（前端组织端页面靠它取本组织数据），
 * 这个信息只有 vcp-org 拿得到。解法是依赖倒置：vcp-system 定义端口，
 * vcp-org 提供实现，运行时由 Spring 注入。
 *
 * <p><b>当前没有实现类</b>（vcp-org 尚未开工，见待办 B7），
 * 因此登录时 orgId 为 null，组织管理员登录后前端取不到 orgId。
 * 这不影响登录本身，等 B7 落地后加一个实现本接口的 Bean 即可自动接上 ——
 * 调用方用 ObjectProvider 取，没有实现时安静跳过，不需要改任何代码。
 */
public interface OrgLookupPort {

    /**
     * 按用户 id 查其负责的志愿组织 id。
     *
     * @param userId 用户 id（sys_user.id）
     * @return 组织 id；该用户不负责任何组织时返回 null
     */
    Long findOrgIdByUserId(Long userId);
}
