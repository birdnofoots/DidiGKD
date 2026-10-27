package li.gkd.app.didi

import android.graphics.Bitmap
import android.os.Environment
import java.io.File
import java.io.FileWriter

/**
 * 滴滴决策存档（P2）：决策后保存 截图 + 结构化记录。
 *
 * 存储位置: 外部存储 /Android/data/<pkg>/files/DidiGKD/
 *   captures/didi_HHMMSS_<price>.jpg   截图(决策那一刻的屏幕)
 *   records.jsonl                       每行一条决策记录(含卡片字段+决策+截图名)
 *
 * 为什么存外部 filesDir: 无需额外权限(Android 11+ 作用域存储)、adb 可直接读取
 * 便于 P4 对账脚本读; P3 的 HTTP 端点也从这里出报告。
 *
 * 薄补丁纪律: 只做"存档"，不做决策、不做点击。
 */
object DidiArchiver {

    // 去重: 同一张卡(价格|距您|时间签名)15秒内不重复存档(actionCd:0 时同卡会反复触发, 曾致 10 倍重复)
    private val lastSig = java.util.concurrent.atomic.AtomicReference<Pair<String, Long>?>(null)
    private fun dup(sig: String): Boolean {
        val now = System.currentTimeMillis()
        val prev = lastSig.get()
        if (prev != null && prev.first == sig && now - prev.second < 15000) return true
        lastSig.set(Pair(sig, now))
        return false
    }

    private fun root(): File {
        // 优先 App 外部私有目录(无权限问题); 失败退回内部 filesDir
        val base = try {
            Environment.getExternalStorageDirectory()?.parentFile
        } catch (e: Exception) {
            null
        }
        // 直接用固定路径: /storage/emulated/0/Android/data/<pkg>/files/DidiGKD
        val f = File("/storage/emulated/0/Android/data/com.birdnofoots.didigkd/files/DidiGKD")
        if (!f.exists()) f.mkdirs()
        return f
    }

    /**
     * 存一条决策记录 + 截图。
     * @return 记录的截图文件名(空=无图)
     */
    fun archive(card: DidiCard, verdict: DidiVerdict, bmp: Bitmap?): String {
        val sig = "${card.price}|${card.distMe}|${card.timeText}"
        if (dup(sig)) return ""          // 同卡 15s 内重复触发 -> 跳过存档
        return try {
            val dir = root()
            val capDir = File(dir, "captures"); capDir.mkdirs()
            val ts = java.text.SimpleDateFormat("HHmmss", java.util.Locale.US)
                .format(java.util.Date())
            val priceTag = card.price?.let { String.format(java.util.Locale.US, "%.2f", it) } ?: "NA"
            val shotName = "didi_${ts}_${priceTag}.jpg"
            bmp?.let {
                File(capDir, shotName).outputStream().use { out ->
                    it.compress(Bitmap.CompressFormat.JPEG, 85, out)
                }
            }
            val rec = org.json.JSONObject().apply {
                put("ts", java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US).format(java.util.Date()))
                put("price", card.price ?: org.json.JSONObject.NULL)
                put("dist_me", card.distMe ?: org.json.JSONObject.NULL)
                put("dist_all", card.distAll ?: org.json.JSONObject.NULL)
                put("time_text", card.timeText ?: org.json.JSONObject.NULL)
                put("addr_from", card.addrFrom ?: org.json.JSONObject.NULL)
                put("addr_to", card.addrTo ?: org.json.JSONObject.NULL)
                put("kind", card.kind)
                put("verdict", verdict.name)
                put("shot", if (bmp != null) shotName else "")
            }
            FileWriter(File(dir, "records.jsonl"), true).use { it.write(rec.toString() + "\n") }
            shotName
        } catch (e: Exception) {
            android.util.Log.w("DidiGKD", "存档失败: $e")
            ""
        }
    }
}
