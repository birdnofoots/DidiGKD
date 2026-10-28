package li.gkd.app.didi

import kotlinx.atomicfu.atomic

/**
 * DidiGKD 运行时开关（commonMain，HTTP 端点和动作层都能访问）。
 *
 * tapEnabled: true=真实点击 / false=影子模式(只决策+存档不点)。
 * 默认 **false = 影子模式**（[stated] 发行版要求：新装用户先只记录数据、不代点，
 * 由用户自己在设置页或 /didi/control 开真实点击）。
 * 自用/对账需要真点击时 POST /api/didi_tap {"enabled":true}。
 */
object DidiConfig {
    val tapEnabled = atomic(false)
}
