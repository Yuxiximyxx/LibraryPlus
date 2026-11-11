package io.github.yuxiximyxx.libraryplus

import org.json.JSONArray
import org.json.JSONObject

object Const {
    const val TAG = "LibraryPlus"
    const val SELF_PKG = "io.github.yuxiximyxx.libraryplus"
    const val LAUNCHER_PKG = "com.hihonor.android.launcher"
    const val HIBOARD_PKG = "com.hihonor.hiboard"
    const val SEARCH_PKG = "com.hihonor.search"
    val SCOPE_PKGS = listOf(LAUNCHER_PKG, HIBOARD_PKG, SEARCH_PKG)

    /** LSPosed new-xshared 读取的文件名（MODE_WORLD_READABLE） */
    const val PREFS_SHARED = "libraryplus"
    /** 本应用私有副本，ContentProvider 从这里读 */
    const val PREFS_PRIVATE = "libraryplus_private"
    /** 被 hook 进程上报的数据（生效状态、真实分类） */
    const val PREFS_STATUS = "libraryplus_status"
    const val KEY_CONFIG = "config"
    const val KEY_CATEGORIES = "categories"
    const val KEY_CATEGORIES_TS = "categories_ts"
    const val KEY_STATUS_PREFIX = "status_"

    const val AUTHORITY = "io.github.yuxiximyxx.libraryplus.config"
    const val METHOD_GET = "get"
    const val METHOD_REPORT = "report"
    const val METHOD_PUT_CATEGORIES = "putCategories"
    const val ACTION_CHANGED = "io.github.yuxiximyxx.libraryplus.CONFIG_CHANGED"
    const val ACTION_RESTART = "io.github.yuxiximyxx.libraryplus.RESTART_SCOPE"
    const val ACTION_REQUEST_CATEGORIES = "io.github.yuxiximyxx.libraryplus.REQUEST_CATEGORIES"
    const val EXTRA_CONFIG = "config"
    const val EXTRA_JSON = "json"
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

    /** 新建分类：id 从 1001 起（避开 100 建议、201–204 热门/推荐、302 等系统内部 id） */
    const val CUSTOM_FOLDER_BASE = 1000
    const val CUSTOM_FOLDER_MAX_ID = 9999
    const val CUSTOM_FOLDER_LIMIT = 20
    const val FOLDER_NAME_MAX = 12
    fun isCustomFolderId(id: Int): Boolean = id in (CUSTOM_FOLDER_BASE + 1)..CUSTOM_FOLDER_MAX_ID
}

/** 用户新建的资源库分类 */
data class CustomFolder(val id: Int, val name: String)

/** 荣耀搜索相关设置 */
object SearchKeys {
    // 结果页卡片
    const val CARD_AD = "ad"
    const val CARD_REC_APP = "recApp"
    const val CARD_REC_NEWS = "recNews"
    const val CARD_HOT = "hot"
    const val CARD_BANNER = "banner"
    const val CARD_TOP = "topContent"
    const val CARD_THIRD_APP = "thirdApp"
    const val CARD_AI = "ai"
    const val CARD_SERVICE = "service"
    const val CARD_SURVEY = "survey"
    val CARDS: List<Pair<String, String>> = listOf(
        CARD_AD to "广告卡片",
        CARD_REC_APP to "推荐应用",
        CARD_REC_NEWS to "推荐资讯",
        CARD_HOT to "热搜",
        CARD_BANNER to "横幅",
        CARD_TOP to "置顶内容卡",
        CARD_THIRD_APP to "三方应用卡",
        CARD_AI to "AI / YOYO 卡片",
        CARD_SERVICE to "服务卡片",
        CARD_SURVEY to "满意度调查",
    )

    // 首页楼层
    const val HOME_HOT = "hot"
    const val HOME_BANNER = "banner"
    const val HOME_FEED = "feed"
    const val HOME_HISTORY = "history"
    val HOME: List<Pair<String, String>> = listOf(
        HOME_HOT to "热搜",
        HOME_BANNER to "横幅",
        HOME_FEED to "信息流",
        HOME_HISTORY to "搜索历史",
    )

    /** 搜索范围：key（荣耀搜索 setting_save_file 中的键）→ 名称 */
    const val SCOPE_APP_SERVICE = "appService"
    const val SCOPE_RED_NOTE = "redNote"
    const val SCOPE_DIAN_PING = "dianPing"
    val SCOPES: List<Pair<String, String>> = listOf(
        "searchNotePad" to "备忘录",
        "searchMms" to "信息",
        "searchContact" to "联系人",
        "searchSetting" to "设置",
        "searchCalendar" to "日历",
        "searchFile" to "文件",
        "searchEmail" to "邮件",
        "searchGallery" to "图库",
        "searchHnCollect" to "收藏",
        SCOPE_APP_SERVICE to "应用内服务",
        SCOPE_RED_NOTE to "小红书",
        SCOPE_DIAN_PING to "大众点评",
    )
    const val SCOPE_FOLLOW = 0
    const val SCOPE_ON = 1
    const val SCOPE_OFF = 2

    /** 结果页标签（实验性）：TabModel id → 名称 */
    val TABS: List<Pair<Long, String>> = listOf(
        2L to "AI 搜索 / YOYO",
        3L to "网页",
        4L to "应用",
        5L to "游戏",
        100L to "小红书",
    )

    const val DARK_DEFAULT = 0
    const val DARK_HIDE = 1
    const val DARK_FIXED = 2

    const val ENGINE_FOLLOW = 0
    const val ENGINE_BAIDU = 1
    const val ENGINE_BING = 2
    const val ENGINE_GOOGLE = 3
    const val ENGINE_CUSTOM = 4
    val ENGINES: List<String> = listOf("跟随系统", "百度", "Bing", "Google", "自定义")
    const val QUERY_PLACEHOLDER = "{\$query}"

    fun engineTemplate(engine: Int, custom: String): String? = when (engine) {
        ENGINE_BAIDU -> "https://m.baidu.com/s?word=$QUERY_PLACEHOLDER"
        ENGINE_BING -> "https://cn.bing.com/search?q=$QUERY_PLACEHOLDER"
        ENGINE_GOOGLE -> "https://www.google.com/search?q=$QUERY_PLACEHOLDER"
        ENGINE_CUSTOM -> custom.trim().takeIf { isValidTemplate(it) }
        else -> null
    }

    fun engineName(engine: Int): String = ENGINES.getOrElse(engine) { ENGINES[0] }

    fun isValidTemplate(t: String): Boolean =
        t.contains(QUERY_PLACEHOLDER) && (t.startsWith("https://") || t.startsWith("http://"))
}

data class SearchCfg(
    /** 请求时关闭广告 / 个性化 */
    val reqNoAd: Boolean = true,
    val hideCards: Set<String> = emptySet(),
    val hideHome: Set<String> = emptySet(),
    val darkWord: Int = SearchKeys.DARK_DEFAULT,
    val darkWordText: String = "",
    val hideAi: Boolean = false,
    /** key → SCOPE_FOLLOW / ON / OFF */
    val scope: Map<String, Int> = emptyMap(),
    val hideTabs: Set<Long> = emptySet(),
    /** 结果页只保留「本机」标签：去掉标签栏，默认显示本机结果 */
    val onlyLocal: Boolean = true,
    val engine: Int = SearchKeys.ENGINE_FOLLOW,
    val engineUrl: String = "",
    val externalBrowser: Boolean = false,
) {
    fun scopeOf(key: String): Int = scope[key] ?: SearchKeys.SCOPE_FOLLOW

    fun toJson(): JSONObject = JSONObject().apply {
        put("reqNoAd", reqNoAd)
        put("hideCards", JSONArray(hideCards.sorted()))
        put("hideHome", JSONArray(hideHome.sorted()))
        put("darkWord", darkWord)
        put("darkWordText", darkWordText)
        put("hideAi", hideAi)
        put("scope", JSONObject().apply { scope.forEach { (k, v) -> if (v != SearchKeys.SCOPE_FOLLOW) put(k, v) } })
        put("hideTabs", JSONArray(hideTabs.sorted()))
        put("onlyLocal", onlyLocal)
        put("engine", engine)
        put("engineUrl", engineUrl)
        put("externalBrowser", externalBrowser)
    }

    companion object {
        private fun strSet(a: JSONArray?): Set<String> {
            val s = HashSet<String>()
            if (a != null) for (i in 0 until a.length()) a.optString(i).takeIf { it.isNotEmpty() }?.let(s::add)
            return s
        }

        fun fromJson(o: JSONObject?): SearchCfg {
            if (o == null) return SearchCfg()
            val scope = HashMap<String, Int>()
            o.optJSONObject("scope")?.let { m -> m.keys().forEach { k -> val v = m.optInt(k, 0); if (v in 1..2) scope[k] = v } }
            val tabs = HashSet<Long>()
            o.optJSONArray("hideTabs")?.let { a -> for (i in 0 until a.length()) tabs += a.optLong(i, -1L) }
            tabs.remove(-1L)
            return SearchCfg(
                reqNoAd = o.optBoolean("reqNoAd", true),
                hideCards = strSet(o.optJSONArray("hideCards")),
                hideHome = strSet(o.optJSONArray("hideHome")),
                darkWord = o.optInt("darkWord", 0).coerceIn(0, 2),
                darkWordText = o.optString("darkWordText", ""),
                hideAi = o.optBoolean("hideAi", false),
                scope = scope,
                hideTabs = tabs,
                onlyLocal = o.optBoolean("onlyLocal", true),
                engine = o.optInt("engine", 0).coerceIn(0, 4),
                engineUrl = o.optString("engineUrl", ""),
                externalBrowser = o.optBoolean("externalBrowser", false),
            )
        }
    }
}

data class LpConfig(
    val hidden: Set<String> = emptySet(),
    val folderNames: Map<Int, String> = emptyMap(),
    val mapping: Map<String, Int> = emptyMap(),
    val rightEntry: Boolean = false,
    val slideFromRight: Boolean = false,
    /** 开启右侧入口后，上滑（原资源库位置）改为打开荣耀搜索 */
    val swipeUpSearch: Boolean = false,
    /** 重启作用域时优先使用 root（am force-stop） */
    val useRoot: Boolean = false,
    val search: SearchCfg = SearchCfg(),
    /** 新建分类（按创建顺序） */
    val customFolders: List<CustomFolder> = emptyList(),
    /** 已分配过的最大自定义分类 id，删除后不复用 */
    val folderSeq: Int = Const.CUSTOM_FOLDER_BASE,
    val ts: Long = 0L,
) {
    fun customName(folderId: Int): String? = folderNames[folderId]?.takeIf { it.isNotBlank() }

    fun customFolder(id: Int): CustomFolder? = customFolders.firstOrNull { it.id == id }

    /** hook 中使用：自定义改名或新建分类的名称 */
    fun folderTitle(id: Int): String? = customName(id) ?: customFolder(id)?.name

    /** 模块界面显示用名称（系统分类显示改名后名称） */
    fun displayName(id: Int): String =
        customName(id) ?: customFolder(id)?.name ?: Const.FOLDERS.firstOrNull { it.first == id }?.second ?: id.toString()

    fun isValidTarget(id: Int): Boolean = id in Const.FOLDER_IDS || customFolder(id) != null

    fun nextFolderId(): Int = maxOf(folderSeq, customFolders.maxOfOrNull { it.id } ?: Const.CUSTOM_FOLDER_BASE) + 1

    fun withNewFolder(name: String): Pair<LpConfig, Int> {
        val id = nextFolderId()
        return copy(customFolders = customFolders + CustomFolder(id, name), folderSeq = id) to id
    }

    fun renameFolder(id: Int, name: String): LpConfig =
        copy(customFolders = customFolders.map { if (it.id == id) it.copy(name = name) else it })

    /** 删除分类：其中的应用回到系统分类（去掉映射） */
    fun deleteFolder(id: Int): LpConfig =
        copy(customFolders = customFolders.filter { it.id != id }, mapping = mapping.filterValues { it != id })

    /** 上滑打开荣耀搜索只在开启右侧入口时生效（否则资源库将无法进入） */
    val swipeUpSearchActive: Boolean get() = rightEntry && swipeUpSearch

    fun toJson(): String = JSONObject().apply {
        put("v", 2)
        put("ts", ts)
        put("hidden", JSONArray(hidden.sorted()))
        put("folderNames", JSONObject().apply { folderNames.forEach { (k, v) -> if (v.isNotBlank()) put(k.toString(), v) } })
        put("mapping", JSONObject().apply { mapping.forEach { (k, v) -> put(k, v) } })
        put("rightEntry", rightEntry)
        put("slideFromRight", slideFromRight)
        put("swipeUpSearch", swipeUpSearch)
        put("useRoot", useRoot)
        put("search", search.toJson())
        put("customFolders", JSONArray().apply { customFolders.forEach { put(JSONObject().put("id", it.id).put("name", it.name)) } })
        put("folderSeq", folderSeq)
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
                val folders = ArrayList<CustomFolder>()
                o.optJSONArray("customFolders")?.let { a ->
                    for (i in 0 until a.length()) {
                        val f = a.optJSONObject(i) ?: continue
                        val id = f.optInt("id", -1)
                        val name = f.optString("name", "").replace("\n", "").trim().take(Const.FOLDER_NAME_MAX)
                        if (!Const.isCustomFolderId(id) || name.isEmpty() || folders.any { it.id == id }) continue
                        if (folders.size >= Const.CUSTOM_FOLDER_LIMIT) break
                        folders += CustomFolder(id, name)
                    }
                }
                val customIds = folders.map { it.id }.toSet()
                val mapping = HashMap<String, Int>()
                o.optJSONObject("mapping")?.let { m ->
                    m.keys().forEach { k -> val v = m.optInt(k, -1); if (v in Const.FOLDER_IDS || v in customIds) mapping[k] = v }
                }
                LpConfig(
                    hidden = hidden,
                    folderNames = names,
                    mapping = mapping,
                    rightEntry = o.optBoolean("rightEntry", false),
                    slideFromRight = o.optBoolean("slideFromRight", false),
                    swipeUpSearch = o.optBoolean("swipeUpSearch", false),
                    useRoot = o.optBoolean("useRoot", false),
                    search = SearchCfg.fromJson(o.optJSONObject("search")),
                    customFolders = folders,
                    folderSeq = o.optInt("folderSeq", Const.CUSTOM_FOLDER_BASE).coerceIn(Const.CUSTOM_FOLDER_BASE, Const.CUSTOM_FOLDER_MAX_ID),
                    ts = o.optLong("ts", 0L),
                )
            } catch (_: Throwable) {
                null
            }
        }
    }
}
