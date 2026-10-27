package li.gkd.app.data

import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import li.gkd.app.didi.DidiCardExtractor
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
 */
data object DidiActionPerformer : ActionPerformer("didi") {

    private const val TAG = "DidiGKD"

    /** 价格稳定状态机（跨事件保持；ActionPerformer 是 object 单例，天然适合持有状态） */
    private val tracker = PriceStableTracker()

    /** 开关：是否真实点击（影子模式时为 false，只记录不点） */
    @Volatile
    var tapEnabled: Boolean = true

    override suspend fun perform(
        node: AccessibilityNodeInfo,
        locationProps: RawSubscription.LocationProps,
    ): ActionResult {
        // 1. 提取卡片字段（node 就是规则匹配到的卡片容器）
        val card = DidiCardExtractor.extract(node)
            ?: return ActionResult(action = action, result = false)

        // 2. 价格稳定判定：签名 = 价格+距离+时间，区分"同卡连续读数" vs "换卡"
        val sig = "${card.price}|${card.distMe}|${card.timeText}"
        val stable = tracker.update(card.price, sig)

        // 3. 决策
        val verdict = decide(card, stable)

        Log.i(TAG, "card price=${card.price} dist=${card.distMe} kind=${card.kind} " +
                "stable=$stable verdict=${verdict.name} tap=$tapEnabled")

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
