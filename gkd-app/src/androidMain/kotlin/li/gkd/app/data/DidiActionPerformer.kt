package li.gkd.app.data

import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import li.gkd.app.didi.DidiCardExtractor
import li.gkd.app.didi.flow.DefaultFlow
import li.gkd.app.didi.flow.DidiFlowHost
import li.gkd.app.didi.flow.FlowEngine
import li.gkd.app.model.ActionResult
import li.gkd.app.service.A11yService
import li.gkd.app.subscription.RawSubscription

/**
 * 滴滴专属动作 `action: "didi"` —— 薄补丁的唯一 GKD 核心接入点。
 *
 * ★P3: 决策不再硬编码, 而是交给 **flow.js**（内嵌 Rhino JS 引擎）：
 *   GKD 匹配卡片 → DidiActionPerformer → FlowEngine.onCard(card) → flow.js 自由决定
 *     决策 → screenshot(verdict) 一卡一图+记录 → tap(按钮)
 *
 * 换场景 = 换 flow.js（热更新，不重编译）——"控制流可定制"的落地。
 * 详见 flow/ 下 FlowEngine/DidiFlowHost/DefaultFlow。
 */
data object DidiActionPerformer : ActionPerformer("didi") {

    private const val TAG = "DidiGKD"
    private val flowHost = DidiFlowHost()
    private val flowEngine = FlowEngine(flowHost)
    @Volatile private var flowLoaded = false

    override suspend fun perform(
        node: AccessibilityNodeInfo,
        locationProps: RawSubscription.LocationProps,
    ): ActionResult {
        // 提取卡片字段(用根节点, 规则匹配的可能是叶子)
        val rootNode = A11yService.instance?.windowNodeInfo ?: node
        val card = DidiCardExtractor.extract(rootNode)
            ?: return ActionResult(action = action, result = false)

        // 确保 flow.js 已加载(首次)
        if (!flowLoaded) {
            flowLoaded = flowEngine.load(DefaultFlow.CODE)   // TODO: 热更新从文件读
        }

        // 更新卡片上下文, 交给 flow.js 决策+截图+点击
        flowHost.root = rootNode
        flowHost.card = card
        flowHost.tapped = false
        Log.i(TAG, "flow.onCard price=${card.price} dist=${card.distMe} kind=${card.kind}")
        flowEngine.onCard(card)

        val tapped = flowHost.tapped
        Log.i(TAG, "flow.done tapped=$tapped err=${flowEngine.lastError}")
        return ActionResult(action = action, result = tapped)
    }
}
