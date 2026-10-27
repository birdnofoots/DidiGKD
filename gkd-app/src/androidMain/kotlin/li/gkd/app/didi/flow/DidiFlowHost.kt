package li.gkd.app.didi.flow

import android.graphics.Bitmap
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.runBlocking
import li.gkd.app.didi.DidiArchiver
import li.gkd.app.didi.DidiCard
import li.gkd.app.didi.DidiCardExtractor
import li.gkd.app.didi.DidiVerdict
import li.gkd.app.service.A11yService

/**
 * FlowHost 的 Android 实现 —— 把 flow.js 的原语桥接到真实动作。
 *   screenshot(verdict) → A11yService 截图 + DidiArchiver 存档(一卡一图, 记录带决策)
 *   tap(target)         → 在卡片节点树找按钮并点击(no_grab/grab)
 *   log(msg)            → 落日志
 *
 * 持有"当前卡片上下文"(root/card)，每次 onCard 前由 DidiActionPerformer 更新。
 */
class DidiFlowHost : FlowHost {

    var root: AccessibilityNodeInfo? = null
    var card: DidiCard? = null
    var tapped: Boolean = false

    private fun toVerdict(s: String): DidiVerdict = when (s) {
        "no_grab" -> DidiVerdict.NoGrab
        "grab" -> DidiVerdict.Grab
        "wait_price" -> DidiVerdict.WaitPrice
        else -> DidiVerdict.Pass
    }

    override fun flowScreenshot(verdict: String): String {
        val c = card ?: return ""
        return try {
            val bmp: Bitmap? = runBlocking { A11yService.instance?.screenshot() }
            val name = DidiArchiver.archive(c, toVerdict(verdict), bmp)
            bmp?.recycle()
            name ?: ""
        } catch (e: Exception) {
            android.util.Log.w("DidiGKD", "flow.screenshot 异常: $e")
            ""
        }
    }

    override fun flowTap(target: String): Boolean {
        // 影子模式: 只记录不点(开关 DidiConfig.tapEnabled, HTTP 可切)
        if (!li.gkd.app.didi.DidiConfig.tapEnabled.value) {
            android.util.Log.i("DidiGKD", "flow.tap $target (影子模式, 不真点)")
            return true
        }
        val r = root ?: return false
        val btn = when (target) {
            "no_grab" -> DidiCardExtractor.findNoGrabButton(r)
            "grab" -> DidiCardExtractor.findGrabButton(r)
            else -> null
        } ?: return false
        val ok = btn.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        tapped = tapped || ok
        android.util.Log.i("DidiGKD", "flow.tap $target → ${btn.viewIdResourceName} ok=$ok")
        return ok
    }

    override fun flowLog(msg: String) {
        android.util.Log.i("DidiGKD", "flow.log $msg")
    }
}
