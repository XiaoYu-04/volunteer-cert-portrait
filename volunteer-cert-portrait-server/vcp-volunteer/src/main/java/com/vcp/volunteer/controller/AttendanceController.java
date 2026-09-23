package com.vcp.volunteer.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaCheckPermission;
import com.vcp.common.result.PageResult;
import com.vcp.common.result.R;
import com.vcp.framework.log.OperationLog;
import com.vcp.volunteer.dto.AttendanceIdDTO;
import com.vcp.volunteer.dto.AttendanceQuery;
import com.vcp.volunteer.dto.AttendanceUpdateDTO;
import com.vcp.volunteer.service.AttendanceService;
import com.vcp.volunteer.vo.AttendanceVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 签到签退接口。
 *
 * <p>列表与人工修正用 {@code volunteer:attendance:manage}（组织管理员与学校管理员持有）。
 *
 * <p><b>签到与签退只做 {@code @SaCheckLogin}，不挂权限标识</b>：这两个动作是学生本人做的，
 * 而 {@code StpInterfaceImpl} 里学生的 5 条权限（activity:list / signup:create /
 * signup:cancel / duration:mine / portrait:mine）没有一条对应签到。新增权限标识需要改
 * vcp-framework 的静态权限表（本轮不允许改其他模块），而真正的门禁是 Service 里的归属校验
 * （只能给自己的签到记录签到），因此这里用「已登录 + 归属校验」而不是形式上的权限码。
 */
@RestController
@RequestMapping("/api/v1/attendance")
@RequiredArgsConstructor
public class AttendanceController {

    private final AttendanceService attendanceService;

    /**
     * 分页查询签到记录。
     *
     * @param query 筛选条件，分页参数为 page / pageSize
     * @return 签到记录分页结果
     */
    @GetMapping
    @SaCheckPermission("volunteer:attendance:manage")
    public R<PageResult<AttendanceVO>> list(AttendanceQuery query) {
        return R.ok(attendanceService.listAttendance(query));
    }

    /**
     * 学生签到。
     *
     * <p><b>静态路径段必须声明在 {@code /{id}} 之前</b>（同 org-overview 的坑）。
     *
     * @param dto 签到记录 id
     * @return 空响应
     */
    @PostMapping("/sign-in")
    @SaCheckLogin
    @OperationLog(module = "签到签退", action = "签到")
    public R<Void> signIn(@Valid @RequestBody AttendanceIdDTO dto) {
        attendanceService.signIn(dto.getAttendanceId());
        return R.ok();
    }

    /**
     * 学生签退。
     *
     * @param dto 签到记录 id
     * @return 空响应
     */
    @PostMapping("/sign-out")
    @SaCheckLogin
    @OperationLog(module = "签到签退", action = "签退")
    public R<Void> signOut(@Valid @RequestBody AttendanceIdDTO dto) {
        attendanceService.signOut(dto.getAttendanceId());
        return R.ok();
    }

    /**
     * 人工修正签到记录（设备异常漏签时由组织管理员补）。
     *
     * @param id  签到记录 id
     * @param dto 目标状态与签到签退时间
     * @return 空响应
     */
    @PutMapping("/{id}")
    @SaCheckPermission("volunteer:attendance:manage")
    @OperationLog(module = "签到签退", action = "修正签到记录")
    public R<Void> update(@PathVariable Long id, @RequestBody AttendanceUpdateDTO dto) {
        attendanceService.updateAttendance(id, dto);
        return R.ok();
    }
}
