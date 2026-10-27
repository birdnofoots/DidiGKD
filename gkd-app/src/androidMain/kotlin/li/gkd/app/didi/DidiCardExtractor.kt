package li.gkd.app.didi

import android.view.accessibility.AccessibilityNodeInfo

/**
 * 从滴滴卡片的无障碍节点树，按 viewId 提取结构化字段 → [DidiCard]。
 *
 * 这是把 DidiWatch 的 findCard() 移植成 Kotlin。viewId 与 DidiWatch 完全一致，
 * 便于影子对账（P4）逐字段比对。
 *
 * 薄补丁原则：只读节点，不做决策，不做点击。决策在 [DidiDecision.kt]。
 */
object DidiCardExtractor {

    // 滴滴车主 包名（提取时只认这个包的节点）
    private const val PKG = "com.sdu.didi.gsui"

    // 关键 viewId（与 DidiWatch didi_tree.py 一致）
    private const val VID_FRAGMENT = "order_review_fragment"   // 卡片容器
    private const val VID_PRICE = "price_info_card_title"      // 价格
    private const val VID_LEFT_BTN = "order_review_left_btn"   // "不抢"按钮容器
    private const val VID_RIGHT_BTN = "order_review_right_btn" // 抢单按钮容器
    private const val VID_HEAD_TAG = "order_review_head_tag_view" // 抬头

    /**
     * 判断当前树上有没有滴滴卡片。
     * 口径与 DidiWatch 一致：必须有卡片容器，且有 价格 或 抬头 之一。
     */
    fun hasCard(root: AccessibilityNodeInfo?): Boolean {
        val frag = findByVid(root, VID_FRAGMENT) ?: return false
        return findByVid(frag, VID_PRICE) != null || findByVid(frag, VID_HEAD_TAG) != null
    }

    /** 提取卡片字段；没有卡片返回 null */
    fun extract(root: AccessibilityNodeInfo?): DidiCard? {
        val frag = findByVid(root, VID_FRAGMENT) ?: return null
        val priceNode = findByVid(frag, VID_PRICE)
        val headNode = findByVid(frag, VID_HEAD_TAG)
        if (priceNode == null && headNode == null) return null

        val priceText = priceNode?.text?.toString()
        val price = parseDouble(priceText)

        // 距离/时间：卡片里 journey_value 节点按 y 坐标排序，预约单 3 个(时间/距您/全程)，实时单 2 个
        val journeys = findAllByVid(frag, "journey_value")
            .sortedWith(compareBy({ topOf(it) }, { leftOf(it) }))
        val jTexts = journeys.mapNotNull { it.text?.toString() }
        val timeText = jTexts.getOrNull(0)?.takeIf { it.contains(":") || it.contains("天") || it.contains("月") }
        val distMe = jTexts.mapNotNull { parseKm(it) }.getOrNull(0)
        val distAll = jTexts.mapNotNull { parseKm(it) }.getOrNull(1)

        // 起终点：address_name 节点按 y 排序，第一个起点第二个终点
        val addrs = findAllByVid(frag, "address_name")
            .sortedBy { topOf(it) }
            .mapNotNull { it.text?.toString()?.trim()?.takeIf { s -> s.isNotEmpty() } }
        val addrFrom = addrs.getOrNull(0)
        val addrTo = addrs.getOrNull(1)

        val tagText = headNode?.text?.toString()
        val hasLeftBtn = findByVid(frag, VID_LEFT_BTN) != null
        // 卡种：有"不抢"按钮 → 预约单；抬头文字兜底
        val kind = when {
            hasLeftBtn -> "预约单"
            tagText?.contains("预约单") == true -> "预约单"
            tagText?.contains("实时单") == true -> "实时单"
            else -> "未知"
        }

        return DidiCard(
            price = price,
            priceText = priceText,
            distMe = distMe,
            distAll = distAll,
            timeText = timeText,
            addrFrom = addrFrom,
            addrTo = addrTo,
            tagText = tagText,
            kind = kind,
            hasLeftBtn = hasLeftBtn,
        )
    }

    /** 找"不抢"按钮容器（可点击的那个，title 文本节点本身不可点） */
    fun findNoGrabButton(root: AccessibilityNodeInfo?): AccessibilityNodeInfo? =
        findByVid(root, VID_LEFT_BTN)

    /** 找抢单按钮容器 */
    fun findGrabButton(root: AccessibilityNodeInfo?): AccessibilityNodeInfo? =
        findByVid(root, VID_RIGHT_BTN)

    // ---------- 坐标 ----------
    // 用标准 API getBoundsInScreen(Rect)，不用 GKD 的 .toHidden.boundsInScreen
    // (那是 hidden API 扩展，直接对 AccessibilityNodeInfo 调会 Unresolved reference)
    private fun topOf(n: AccessibilityNodeInfo): Int {
        val r = android.graphics.Rect(); n.getBoundsInScreen(r); return r.top
    }
    private fun leftOf(n: AccessibilityNodeInfo): Int {
        val r = android.graphics.Rect(); n.getBoundsInScreen(r); return r.left
    }

    // ---------- 节点查找 ----------

    private fun findByVid(root: AccessibilityNodeInfo?, vidSuffix: String): AccessibilityNodeInfo? {
        root ?: return null
        if (matchesVid(root, vidSuffix)) return root
        for (i in 0 until root.childCount) {
            val r = findByVid(root.getChild(i), vidSuffix)
            if (r != null) return r
        }
        return null
    }

    private fun findAllByVid(root: AccessibilityNodeInfo?, vidSuffix: String): List<AccessibilityNodeInfo> {
        val out = mutableListOf<AccessibilityNodeInfo>()
        fun walk(n: AccessibilityNodeInfo?) {
            n ?: return
            if (matchesVid(n, vidSuffix)) out.add(n)
            for (i in 0 until n.childCount) walk(n.getChild(i))
        }
        walk(root)
        return out
    }

    /** viewId 形如 "com.sdu.didi.gsui:id/order_review_fragment"，按后缀匹配 */
    private fun matchesVid(n: AccessibilityNodeInfo, vidSuffix: String): Boolean {
        val v = n.viewIdResourceName ?: return false
        return v == "$PKG:id/$vidSuffix" || v.endsWith(":id/$vidSuffix")
    }

    // ---------- 数值解析 ----------

    /** "107.88" / "¥107.88" / "107" → 107.88；解析失败 null */
    private fun parseDouble(s: String?): Double? {
        s ?: return null
        val m = Regex("""(\d+(?:\.\d+)?)""").find(s) ?: return null
        return m.groupValues[1].toDoubleOrNull()
    }

    /**
     * 距离解析（单位感知，移植自 DidiWatch 的 km/m 判断）：
     *  "12.5km" → 12.5   "907m" → 0.907   "12km" → 12.0
     *  无单位的纯数字（如 "12.5"）→ 12.5
     */
    private fun parseKm(s: String?): Double? {
        s ?: return null
        val num = parseDouble(s) ?: return null
        return when {
            s.endsWith("m") && !s.endsWith("km") -> num / 1000.0   // 米制折算
            else -> num                                              // km 或无单位
        }
    }
}
