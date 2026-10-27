package li.gkd.app.didi

import kotlinx.atomicfu.atomic

/**
 * DidiGKD 运行时开关（commonMain，HTTP 端点和动作层都能访问）。
 *
 * tapEnabled: true=真实点击 / false=影子模式(只决策+存档不点)。
 * 默认 true —— 用户确认：DidiGKD 与 DidiWatch 规则相同、点同一按钮，真点击不冲突
 * (DidiGKD 更快会先点, DidiWatch 的点击落到已消失卡片上无害)。
 * 切影子模式(P4 对账)时 POST /api/didi_tap {"enabled":false}。
 */
object DidiConfig {
    val tapEnabled = atomic(true)
}
