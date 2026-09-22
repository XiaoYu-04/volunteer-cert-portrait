package com.vcp.system.controller;

import com.vcp.common.result.R;
import com.vcp.framework.log.OperationLog;
import com.vcp.system.dto.LoginDTO;
import com.vcp.system.dto.ProfileUpdateDTO;
import com.vcp.system.dto.RegisterDTO;
import com.vcp.system.service.AuthService;
import com.vcp.system.vo.LoginVO;
import com.vcp.system.vo.SessionVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证接口：登录、注册、退出、查改本人资料。
 *
 * <p>路径前缀是 {@code /api/v1/auth}。前端 axios 的 baseURL 配成 {@code /api}，
 * 各接口再写 {@code /v1/auth/xxx}，两者拼起来才是完整路径 ——
 * 后端这边没有配 {@code server.servlet.context-path}，前缀必须写在注解里，
 * 少写一段会表现为前端 404（且因为响应体不是统一外壳，页面提示会是"请求失败"而非具体文案）。
 *
 * <p>登录与注册两个接口在 {@code SaTokenConfig.EXCLUDE_PATHS} 里放行，否则
 * 永远拿不到第一个 token。路径若在这里改动，必须同步改那份放行清单。
 *
 * <p><b>登录与注册的 {@code @OperationLog} 都写了 {@code params = false}</b>：
 * 这两个请求体里带着明文密码，采集参数会把口令序列化进 {@code operation_log.params}
 * 永久留存。日志里保留"谁、什么时候、调了什么、成功还是失败"已经够用。
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /**
     * 账号密码登录。
     *
     * @param dto 登录参数
     * @return token 与用户信息
     */
    @PostMapping("/login")
    @OperationLog(module = "认证", action = "登录", params = false)
    public R<LoginVO> login(@Valid @RequestBody LoginDTO dto) {
        return R.ok(authService.login(dto));
    }

    /**
     * 学生自助注册，注册成功即视为登录。
     *
     * @param dto 注册参数
     * @return token 与用户信息
     */
    @PostMapping("/register")
    @OperationLog(module = "认证", action = "注册", params = false)
    public R<LoginVO> register(@Valid @RequestBody RegisterDTO dto) {
        return R.ok(authService.register(dto));
    }

    /**
     * 退出登录。
     *
     * @return 空响应
     */
    @PostMapping("/logout")
    public R<Void> logout() {
        authService.logout();
        return R.ok();
    }

    /**
     * 取当前登录用户信息。前端刷新页面后靠它用 token 换回用户信息。
     *
     * @return 当前用户信息
     */
    @GetMapping("/me")
    public R<SessionVO> me() {
        return R.ok(authService.currentUser());
    }

    /**
     * 修改本人资料（姓名、手机号、邮箱）。
     *
     * @param dto 待修改字段
     * @return 修改后的用户信息
     */
    @PutMapping("/me")
    @OperationLog(module = "认证", action = "修改个人资料")
    public R<SessionVO> updateMe(@RequestBody ProfileUpdateDTO dto) {
        return R.ok(authService.updateProfile(dto));
    }
}
