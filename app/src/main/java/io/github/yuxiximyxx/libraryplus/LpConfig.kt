package io.github.yuxiximyxx.libraryplus

import org.json.JSONArray
import org.json.JSONObject

object Const {
    const val TAG = "LibraryPlus"
    const val SELF_PKG = "io.github.yuxiximyxx.libraryplus"
    const val LAUNCHER_PKG = "com.hihonor.android.launcher"
    const val HIBOARD_PKG = "com.hihonor.hiboard"

    /** LSPosed new-xshared 读取的文件名（MODE_WORLD_READABLE） */
    const val PREFS_SHARED = "libraryplus"
    /** 本应用私有副本，ContentProvider 从这里读 */
    const val PREFS_PRIVATE = "libraryplus_private"
    const val KEY_CONFIG = "config"

    const val AUTHORITY = "io.github.yuxiximyxx.libraryplus.config"
    const val METHOD_GET = "get"
    const val ACTION_CHANGED = "io.github.yuxiximyxx.libraryplus.CONFIG_CHANGED"
    const val EXTRA_CONFIG = "config"
    const val PERMISSION = "io.github.yuxiximyxx.libraryplus.permission.CONFIG"

    /** 资源库文件夹 id → 默认名称（来自 hiboard utils.r / FolderUtil） */
    val FOLDERS: List<Pair<Int, String>> = listOf(
        101 to "社交通讯",
        102 to "购物与美食",
        103 to "新闻阅读",
        104 to "影音娱乐",
        105 to "实用工具",
        106 to "办公与金融",
        107 to "旅游与出行",
        108 to "拍摄美化",
        109 to "生活与健康",
        110 to "游戏",
        111 to "教育早教",
        112 to "其他",
    )
    val FOLDER_IDS: Set<Int> = FOLDERS.map { it.first }.toSet()
}

data class LpConfig(
    val hidden: Set<String> = emptySet(),
    val folderNames: Map<Int, String> = emptyMap(),
    val mapping: Map<String, Int> = emptyMap(),
    val rightEntry: Boolean = false,
    val slideFromRight: Boolean = false,
    val keepSwipeUp: Boolean = true,
    val ts: Long = 0L,
) {
    fun customName(folderId: Int): String? = folderNames[folderId]?.takeIf { it.isNotBlank() }

    fun toJson(): String = JSONObject().apply {
        put("v", 1)
        put("ts", ts)
        put("hidden", JSONArray(hidden.sorted()))
        put("folderNames", JSONObject().apply { folderNames.forEach { (k, v) -> if (v.isNotBlank()) put(k.toString(), v) } })
        put("mapping", JSONObject().apply { mapping.forEach { (k, v) -> put(k, v) } })
        put("rightEntry", rightEntry)
        put("slideFromRight", slideFromRight)
        put("keepSwipeUp", keepSwipeUp)
    }.toString()

    companion object {
        fun fromJson(s: String?): LpConfig? {
            if (s.isNullOrBlank()) return null
            return try {
                val o = JSONObject(s)
                val hidden = HashSet<String>()
                o.optJSONArray("hidden")?.let { a -> for (i in 0 until a.length()) a.optString(i).takeIf { it.isNotEmpty() }?.let(hidden::add) }
                val names = HashMap<Int, String>()
                o.optJSONObject("folderNames")?.let { n -> n.keys().forEach { k -> k.toIntOrNull()?.let { id -> names[id] = n.optString(k) } } }
                val mapping = HashMap<String, Int>()
                o.optJSONObject("mapping")?.let { m -> m.keys().forEach { k -> val v = m.optInt(k, -1); if (v in Const.FOLDER_IDS) mapping[k] = v } }
                LpConfig(
                    hidden = hidden,
                    folderNames = names,
                    mapping = mapping,
                    rightEntry = o.optBoolean("rightEntry", false),
                    slideFromRight = o.optBoolean("slideFromRight", false),
                    keepSwipeUp = o.optBoolean("keepSwipeUp", true),
                    ts = o.optLong("ts", 0L),
                )
            } catch (_: Throwable) {
                null
            }
        }
    }
}
