package li.gkd.app.data

import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.launch
import li.gkd.app.appScope
import li.gkd.app.didi.DidiArchiver
import li.gkd.app.didi.DidiCardExtractor
import li.gkd.app.service.A11yService
import li.gkd.app.didi.DidiVerdict
import li.gkd.app.didi.PriceStableTracker
import li.gkd.app.didi.decide
import li.gkd.app.model.ActionResult
import li.gkd.app.subscription.RawSubscription

/**
 * 滴滴专属动作 `action: "did"` —— 薄补丁的唯一 GKD 核心接入点。
 *
 * 规则写法：matches 匹配到滴滴卡片容器(order_review_fragment)，action 用 "did"。
 * GKD 引擎匹配到卡片后调用本类，本类在卡片节点子树里完成：
 *   提取字段 → 价格稳定判定 → 决策 → 点击对应按钮。
 *
 * 与 GKD 原生动作的区别：原生动作是"匹配即点"，本类带**状态**(价格稳定需要连续两次同价)
 * 和**决策**(抢/不抢/等待)，这是纯选择器语言表达不了的。
 *
 * 薄补丁纪律：滴滴业务逻辑全在 li.gkd.app.didi 包；本文件只是"胶水"，不掺业务判断。
 *
 * ── 演进路径（P3 做 QuickJS 时照此改造，不必重新理解架构）─────────────────
 * 当前 action:"didi" 是**滴滴专属硬编码**，每适配一个新 App 都要重编译 —— 这是已知取舍。
 * P3 引入脚本引擎后，本文件的外部接口（GKD 匹配卡片 → 调 perform）**保持不变**，
 * 只需把 decide() 那一步换成"调用 QuickJS 执行 flow.js"，action 改名为 "script"：
 *
 *     现在:  perform() → extract → tracker → decide(硬编码) → 点击
 *     P3:    perform() → extract → tracker → quickjs.evaluate("flow.js") → 点击
 *
 * 提取字段 / 价格稳定 / 决策骨架 / 点击 这套流程是通用的，P3 直接复用；
 * 只有"决策"那一步从 Kotlin 常量换成脚本调用。届时各家 App 用各自 flow.js，
 * 不再重编译 —— 正是"模板生成动作"的最终形态。
 */
data object DidiActionPerformer : ActionPerformer("didi") {

    private const val TAG = "DidiGKD"

    /** 价格稳定状态机（跨事件保持；ActionPerformer 是 object 单例，天然适合持有状态） */
    private val tracker = PriceStableTracker()

    /** 开关见 DidiConfig.tapEnabled(commonMain 共享, HTTP 端点可切)。默认 true=真实点击。 */
    private val tapEnabled: Boolean get() = li.gkd.app.didi.DidiConfig.tapEnabled.value

    /** P2: 决策后截图+存档(后台异步) */
    private fun archiveAsync(card: li.gkd.app.didi.DidiCard, verdict: li.gkd.app.didi.DidiVerdict) {
        appScope.launch {
            try {
                val bmp = A11yService.instance?.screenshot()
                DidiArchiver.archive(card, verdict, bmp)
                bmp?.recycle()
            } catch (e: Exception) {
                Log.w(TAG, "存档异常: $e")
            }
        }
    }

    override suspend fun perform(
        node: AccessibilityNodeInfo,
        locationProps: RawSubscription.LocationProps,
    ): ActionResult {
        // 1. 提取卡片字段。规则匹配的是抬头(叶子), 字段要从整棵树找 → 用无障碍根节点
        val rootNode = A11yService.instance?.windowNodeInfo ?: node
        val card = DidiCardExtractor.extract(rootNode)
            ?: return ActionResult(action = action, result = false)

        // 2. 决策(价格稳定由订阅规则的 actionDelay 保证: 匹配后等 80ms 再执行, 读到的已是稳定价)
        //    不再用 price_stable 状态机 —— 它是 DidiWatch/adb 轮询时代的产物, GKD 单次触发下水土不服
        val verdict = decide(card, true)

        Log.i(TAG, "card price=${card.price} dist=${card.distMe} kind=${card.kind} " +
                "verdict=${verdict.name} tap=$tapEnabled")

        // P2: 决策后截图+存档(后台异步, 不拖慢点击)。所有决策都存(含 wait_price/pass), 供 P4 对账
        archiveAsync(card, verdict)

        // 4. 按决策点击对应按钮
        val target = when (verdict) {
            DidiVerdict.NoGrab -> DidiCardExtractor.findNoGrabButton(node)
            DidiVerdict.Grab -> DidiCardExtractor.findGrabButton(node)
            else -> null   // WaitPrice / Pass：不点
        }

        // 影子模式：只记录不点
        if (!tapEnabled) {
            return ActionResult(action = action, result = true)
        }

        if (target == null) {
            return ActionResult(action = action, result = false)
        }

        // 用节点点击而非坐标：卡片可能被别的窗口压住，坐标点击会打到别的 app
        val ok = target.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK)
        Log.i(TAG, "tap ${verdict.name} → ${target.viewIdResourceName} ok=$ok")
        return ActionResult(action = action, result = ok)
    }
}
