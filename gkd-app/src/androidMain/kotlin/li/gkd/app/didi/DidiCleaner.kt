package li.gkd.app.didi

import java.io.File

/**
 * 滴滴存档清理（P2）：截图+记录**联动删**，防 APK 数据膨胀。
 *
 * 规则:
 *  - 截图与记录一起删(不产生孤儿截图/孤儿记录)
 *  - 支持「N 天前」或「某日期前」两种口径
 *  - 返回删除条数, 供日志/界面显示
 *
 * 触发(P3 接 HTTP 端点): 自动(每日+容量阈值) + 手动(存储管理页 / POST /api/cleanup)
 * 薄补丁纪律: 只做清理, 不碰决策/点击。
 */
object DidiCleaner {

    private fun root(): File = File("/storage/emulated/0/Android/data/com.birdnofoots.didigkd/files/DidiGKD")

    /**
     * 清理旧记录+截图。
     * @param days 删除 N 天前(按记录 ts)
     * @param beforeDate 删除该日期前(yyyy-MM-dd)
     * @return "删除 N 条记录 + M 张截图"
     */
    fun cleanup(days: Int? = null, beforeDate: String? = null): String {
        val dir = root()
        val recFile = File(dir, "records.jsonl")
        val capDir = File(dir, "captures")
        if (!recFile.exists()) return "无记录文件"

        val cutoffEpoch = when {
            days != null -> System.currentTimeMillis() - days * 86400_000L
            beforeDate != null -> try {
                java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).parse(beforeDate)?.time
                    ?: 0L
            } catch (e: Exception) { 0L }
            else -> 0L
        }

        // 读记录, 按 ts 过滤保留/删除, 收集要删的截图名
        val keep = mutableListOf<String>()
        val dropShots = mutableListOf<String>()
        var dropped = 0
        recFile.readLines().forEach { line ->
            if (line.isBlank()) return@forEach
            try {
                val o = org.json.JSONObject(line)
                val ts = o.optString("ts")
                val tsEpoch = try {
                    java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US).parse(ts)?.time ?: 0L
                } catch (e: Exception) { 0L }
                if (tsEpoch in 1 until cutoffEpoch) {
                    dropped++
                    o.optString("shot").takeIf { it.isNotEmpty() }?.let { dropShots.add(it) }
                } else {
                    keep.add(line)
                }
            } catch (e: Exception) {
                keep.add(line)  // 解析不了的保留, 别误删
            }
        }

        // 联动删: 记录 + 对应截图
        var shotDel = 0
        dropShots.forEach { name ->
            val f = File(capDir, name)
            if (f.exists() && f.delete()) shotDel++
        }
        recFile.writeText(keep.joinToString("\n") + if (keep.isNotEmpty()) "\n" else "")
        return "删除 $dropped 条记录 + $shotDel 张截图"
    }
}
