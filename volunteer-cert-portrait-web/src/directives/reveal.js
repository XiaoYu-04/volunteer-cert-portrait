/**
 * v-reveal：元素滚动进入视口时淡入上浮，只播一次。
 *
 *   <section v-reveal>        淡入 + 上浮 10px
 *   <figure v-reveal.fade>    只淡入（含 ECharts 画布的区块用这个：
 *                             translate 会让 canvas 在动画期间发虚）
 *
 * 三条兜底规则（都很重要，别删）：
 *   1. 默认可见：隐藏初态由本指令在 mounted 里同步添加，指令没跑（旧浏览器、
 *      打印、出错）时元素就是普通可见状态，不会永久隐形；
 *   2. 首屏豁免：元素已经落在首屏内 → 不加隐藏类、不播动画。否则首屏内容会在
 *      挂载瞬间"先消失再浮现"，看起来像闪了一下；
 *   3. 降级：prefers-reduced-motion: reduce 或没有 IntersectionObserver 时直接不接管。
 *
 * 用法约定：只加在「首屏以下」的整块 section / panel 上，不要逐条加在 v-for 的
 * 子项上（那是 .anim-stagger 的活）；列表项多时逐条观察会浪费且节奏难统一。
 */
const PENDING = 'reveal-pending'
const PENDING_FADE = 'reveal-pending-fade'
const IN = 'reveal-in'

/** 元素顶边进到视口这个比例以内就算「首屏」，不播动画（1 = 视口高度） */
const FIRST_SCREEN_RATIO = 0.85

/** 根边距：底部收 8%，让元素露头多一点再播，避免刚擦边就动 */
const ROOT_MARGIN = '0px 0px -8% 0px'

function shouldSkip() {
  if (typeof IntersectionObserver === 'undefined') return true
  return window.matchMedia?.('(prefers-reduced-motion: reduce)')?.matches ?? false
}

export default {
  mounted(el, binding) {
    if (binding.value === false || shouldSkip()) return

    // 延后一帧再量：路由切换时 router 的 scrollBehavior 归零可能发生在 mounted 之后，
    // 此刻量到的位置会偏上，导致该播的元素被误判成"首屏"。
    // 元素若本来就在首屏内，本帧内跳过 —— 用户看不到任何隐藏，自然没有闪烁。
    requestAnimationFrame(() => {
      if (!el.isConnected) return
      if (el.getBoundingClientRect().top < window.innerHeight * FIRST_SCREEN_RATIO) return

      el.classList.add(binding.modifiers?.fade ? PENDING_FADE : PENDING)

      const observer = new IntersectionObserver(
        (entries) => {
          if (!entries.some((entry) => entry.isIntersecting)) return
          el.classList.add(IN)
          observer.disconnect()
          el._revealObserver = null
        },
        { rootMargin: ROOT_MARGIN, threshold: 0.05 },
      )

      observer.observe(el)
      el._revealObserver = observer
    })
  },

  unmounted(el) {
    el._revealObserver?.disconnect()
    el._revealObserver = null
  },
}
