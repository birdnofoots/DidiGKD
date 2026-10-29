package li.gkd.app.didi.cloud

import android.os.Build
import android.util.Log
import li.gkd.app.app
import li.gkd.app.didi.DidiCloudConfig
import li.gkd.app.didi.DidiTreeDump
import li.gkd.app.service.A11yService
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import li.gkd.app.storage.appStorage

/**
 * 界面快照采集（S3：按版本触发，不按天）。
 *
 * 为什么要按版本而不是定时：**有价值的快照只在卡片弹出的那一瞬间存在**，
 * 定时拍到的基本是主屏/行程页，没用。而我们要回答的问题是
 * "别人机器上的这个滴滴版本，卡片长什么样、viewId 还在不在、层级差几层" ——
 * 每个 (appId, versionCode) 有一份样本就够了。
 *
 * 行为：
 *   卡片出现 → 查本地已采集集合 → 没见过这个版本才拍 → 上传 → 记下
 *   见过就什么都不做（流量几乎为零）
 *
 * ⚠️ 绝不阻塞卡片路径（卡片只活 138~294ms）→ 整个动作丢到后台线程。
 * ⚠️ 只在用户开了「静默上传」时才采集 —— 没开上传就不该产生流量。
 */
object DidiSnap {

    private const val TAG = "DidiGKD"
    private const val SEEN_FILE = "snaps.seen"

    @Volatile
    private var running = false

    /** 卡片处理完后调用。异步、不阻塞、失败不影响抢单。 */
    fun maybeCapture() {
        if (running) return
        if (!DidiCloudConfig.enabled.value) return
        Thread {
            running = true
            try {
                doCapture()
            } catch (e: Throwable) {
                Log.w(TAG, "快照采集异常: $e")
            } finally {
                running = false
            }
        }.start()
    }

    private fun seenFile(): File = File(appStorage().didi, SEEN_FILE)

    /** regionOnScreen 是 API 34+, minSdk 26 用标准 getBoundsInScreen */
    private fun screenOf(n: android.view.accessibility.AccessibilityNodeInfo): String = try {
        val r = android.graphics.Rect()
        n.getBoundsInScreen(r)
        "${r.width()}x${r.height()}"
    } catch (e: Exception) {
        ""
    }

    private fun loadSeen(): MutableSet<String> =
        seenFile().takeIf { it.isFile }?.readLines()?.filter { it.isNotBlank() }?.toMutableSet()
            ?: LinkedHashSet()

    private fun saveSeen(set: MutableSet<String>) {
        runCatching { seenFile().writeText(set.joinToString("\n")) }
    }

    private fun doCapture() {
        val w = A11yService.instance?.windowNodeInfo ?: return
        val pkg = w.packageName?.toString() ?: return
        if (pkg == app.packageName) return          // 别抓自己

        val info = try {
            app.packageManager.getPackageInfo(pkg, 0)
        } catch (e: Exception) {
            return
        }
        // longVersionCode 是 API 28+, minSdk 26 要兜底
        @Suppress("DEPRECATION")
        val verCode = if (Build.VERSION.SDK_INT >= 28) info.longVersionCode.toInt()
        else info.versionCode
        val verName = info.versionName ?: ""
        val key = "$pkg:$verCode"

        val seen = loadSeen()
        if (key in seen) return                      // ★核心: 每个版本只采一次

        val treeText = DidiTreeDump.dump(w)
        if (treeText.isEmpty()) return

        val tree = JSONArray(treeText)
        val payload = JSONObject()
            .put("appId", pkg)
            .put("versionCode", verCode)
            .put("versionName", verName)
            .put("model", Build.MODEL)
            .put("device", Build.MANUFACTURER + " " + Build.DEVICE)
            .put("sdk", Build.VERSION.SDK_INT)
            .put("screen", screenOf(w))
            .put("ts", java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US)
                .format(java.util.Date()))
            .put("tag", "card")
            .put("tree", tree)
            .toString().toByteArray(Charsets.UTF_8)

        Log.i(TAG, "采集快照 $pkg $verName($verCode) 节点=${tree.length()} -> 上传")
        if (CloudSync.uploadSnap(payload)) {
            seen.add(key)
            saveSeen(seen)                           // 上传成功才记下
        }
    }
}
