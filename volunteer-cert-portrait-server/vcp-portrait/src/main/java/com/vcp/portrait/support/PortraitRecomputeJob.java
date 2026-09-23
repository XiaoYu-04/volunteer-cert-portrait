package com.vcp.portrait.support;

import com.vcp.portrait.service.PortraitService;
import com.vcp.portrait.vo.PortraitRecomputeVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 画像重算的定时兜底：每天 02:00 全量重算一次。
 *
 * <p>规则方案 §五 给了两条重算路径 —— 时长审核通过后发事件触发，或「手动重算接口 +
 * 定时任务兜底」。事件方案要求 vcp-certification 反向依赖 vcp-portrait（与现有依赖方向相反，
 * 本轮也不改其他模块），因此先落地兜底这条：即使没有任何人手动点重算，
 * 画像最多也只落后一天。
 *
 * <p>02:00 这个时间点与前端页面上的文案一致（「画像每日 02:00 更新」）。
 *
 * <p><b>为什么不标 {@code @OperationLog}</b>：操作日志切面靠 HttpServletRequest 取来源 IP、
 * 靠 Sa-Token 取登录用户，定时任务两者都没有；而且它记的是「谁在什么时候做了什么」，
 * 定时任务没有「谁」。它的执行痕迹由下面的 info 日志承担。
 *
 * <p>定时任务的异常不会被全局异常处理器接管（没有请求上下文），因此这里自己兜住并留日志，
 * 否则失败时只在调度线程里留下一行栈，看不出是哪个业务失败。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PortraitRecomputeJob {

    private final PortraitService portraitService;

    /**
     * 每日 02:00 全量重算画像。
     *
     * <p>重算本身是幂等的：内容没变就不写库，因此这一趟不会白白刷新
     * 「画像生成时间」，也不会把定时任务的执行痕迹写进业务数据。
     */
    @Scheduled(cron = "0 0 2 * * ?")
    public void recomputeDaily() {
        try {
            PortraitRecomputeVO result = portraitService.recompute(null);
            log.info("[画像] 每日定时重算完成。扫描 {} 人，写入 {} 份画像",
                    result.getScanned(), result.getUpdated());
        } catch (Exception e) {
            log.error("[画像] 每日定时重算失败，本次跳过，等待下一次触发", e);
        }
    }
}
