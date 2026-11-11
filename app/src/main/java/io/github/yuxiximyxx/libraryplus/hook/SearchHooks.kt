package io.github.yuxiximyxx.libraryplus.hook

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import io.github.yuxiximyxx.libraryplus.Const
import io.github.yuxiximyxx.libraryplus.LpConfig
import io.github.yuxiximyxx.libraryplus.SearchCfg
import io.github.yuxiximyxx.libraryplus.SearchKeys
import org.json.JSONObject
import org.luckypray.dexkit.DexKitBridge
import java.io.File
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.WeakHashMap

/**
 * 荣耀搜索 com.hihonor.search（10.0.37.301 分析，R8 全混淆；模型 / Activity / Strategy / 自定义 View 类名保留）。
 * 分析见 FINDINGS.md 第三部分 D。
 */
internal object SearchHooks {
    private const val S = "com.hihonor.search"
    private const val GENERAL_TAB = "$S.feature.resultpage.presentation.adapter.strategy.generaltab"
    private const val VIEW_HOLDER = "androidx.recyclerview.widget.RecyclerView\$ViewHolder"

    // DexKit 失败时的兜底名字（10.0.37.301）
    private const val FALLBACK_SCOPE_CLASS = "stb"
    private const val FALLBACK_SCOPE_METHOD = "d"

    private lateinit var cl: ClassLoader
    private val cfg: LpConfig get() = HookConfig.cfg
    private val sc: SearchCfg get() = HookConfig.cfg.search

    fun install(lpparam: XC_LoadPackage.LoadPackageParam) {
        cl = lpparam.classLoader
        hookRequests()
        hookCardStrategies()
        hookViewEngine()
        hookInflate()
        hookDarkWords()
        hookScopePrefs()
        hookScopeMmkv()
        hookScopeList(resolveScopeMethod(lpparam))
        hookTabs()
        hookEngine()
        hookExternalBrowser()
        HookConfig.addListener { hiddenIds = null }
    }

    fun onAppCreated(ctx: Context) {
        // 搜索没有需要在 Application 之后才能做的事；保留入口便于以后扩展
    }

    // ================================================================== 请求层：关闭广告 / 个性化
    /**
     * GeneralDataReq(query, cityCode, tInfo, cloudComp, supportAd:Z, supportActivationAd:I, dftSearchEngineType,
     *                searchReqId, activityName, geoInfo, isPersonalizedClosed:Z, …)
     * HomeHotSearchReq(isPersonalizedClosed:Z, cityName, cityCode, isRecommend:Z, deviceMode:I, tInfo, oaidSwitchList,
     *                  supportedSelfHotList:Z, supportActivationAd:I, …)
     */
    private fun hookRequests() {
        val z = Boolean::class.javaPrimitiveType
        val i = Int::class.javaPrimitiveType
        safe("GeneralDataReq.<init>(noAd)") {
            val cls = XposedHelpers.findClass("$S.feature.resultpage.data.entity.GeneralDataReq", cl)
            val ctors = cls.declaredConstructors.filter { c ->
                val p = c.parameterTypes
                p.size >= 11 && p[0] == String::class.java && p[4] == z && p[5] == i && p[10] == z
            }
            require(ctors.isNotEmpty()) { "ctor not found" }
            ctors.forEach { c ->
                XposedBridge.hookMethod(c, before("GeneralDataReq") { p ->
                    if (!sc.reqNoAd) return@before
                    p.args[4] = false
                    p.args[5] = 0
                    p.args[10] = true
                })
            }
        }
        safe("HomeHotSearchReq.<init>(noAd)") {
            val cls = XposedHelpers.findClass("$S.feature.mainpage.data.remote.model.HomeHotSearchReq", cl)
            val ctors = cls.declaredConstructors.filter { c ->
                val p = c.parameterTypes
                p.size >= 9 && p[0] == z && p[1] == String::class.java && p[3] == z && p[4] == i && p[8] == i
            }
            require(ctors.isNotEmpty()) { "ctor not found" }
            ctors.forEach { c ->
                XposedBridge.hookMethod(c, before("HomeHotSearchReq") { p ->
                    if (!sc.reqNoAd) return@before
                    p.args[0] = true
                    p.args[8] = 0
                })
            }
        }
    }

    // ================================================================== 结果页卡片
    private val STRATEGIES: Map<String, String> = mapOf(
        "AdCardStrategy" to SearchKeys.CARD_AD,
        "RecommendAppStrategy" to SearchKeys.CARD_REC_APP,
        "RecommendNewsStrategy" to SearchKeys.CARD_REC_NEWS,
        "HotSearchInfoStrategy" to SearchKeys.CARD_HOT,
        "BannerStrategy" to SearchKeys.CARD_BANNER,
        "TopContentCardStrategy" to SearchKeys.CARD_TOP,
        "ThirdAppCardStrategy" to SearchKeys.CARD_THIRD_APP,
        "YOYOAgentCardStrategy" to SearchKeys.CARD_AI,
        "AiSoStrategy" to SearchKeys.CARD_AI,
        "LocalAiSoStrategy" to SearchKeys.CARD_AI,
        "LocalAiAuthStrategy" to SearchKeys.CARD_AI,
        "ServiceComponentStrategy" to SearchKeys.CARD_SERVICE,
        "SatisfactionSurveyStrategy" to SearchKeys.CARD_SURVEY,
    )

    /** 综合 tab 的多类型适配器：每种卡片一个 Strategy，创建 / 绑定 ViewHolder 时按开关折叠 itemView。 */
    private fun hookCardStrategies() {
        val vh = try { XposedHelpers.findClass(VIEW_HOLDER, cl) } catch (t: Throwable) {
            synchronized(HookReport) { HookReport.failed += "RecyclerView.ViewHolder" }
            Lg.e("ViewHolder class not found", t); return
        }
        for ((name, key) in STRATEGIES) {
            safe("Strategy.$name") {
                val cls = XposedHelpers.findClass("$GENERAL_TAB.$name", cl)
                var n = 0
                for (m in cls.declaredMethods) {
                    if (Modifier.isStatic(m.modifiers) || Modifier.isAbstract(m.modifiers)) continue
                    val pt = m.parameterTypes
                    val isCreate = pt.size == 2 && pt[0] == ViewGroup::class.java && pt[1] == Int::class.javaPrimitiveType && vh.isAssignableFrom(m.returnType)
                    val isBind = pt.isNotEmpty() && vh.isAssignableFrom(pt[0])
                    if (!isCreate && !isBind) continue
                    XposedBridge.hookMethod(m, after("card.$name") { p ->
                        val holder = if (isCreate) p.result else p.args[0]
                        val item = holder?.let { XposedHelpers.getObjectField(it, "itemView") } as? View ?: return@after
                        if (key in sc.hideCards) collapse(item) else expand(item)
                    })
                    n++
                }
                require(n > 0) { "no create/bind method" }
            }
        }
    }

    // ================================================================== 通用视图折叠 / 按 id 隐藏
    /** 被折叠的 View → [原 visibility, 原 height, 原 topMargin, 原 bottomMargin] */
    private val collapsed = WeakHashMap<View, IntArray>()
    @Volatile private var anyCollapsed = false
    @Volatile private var hiddenIds: Set<Int>? = null
    private var darkWordCls: Class<*>? = null

    private fun isCollapsed(v: View): Boolean = anyCollapsed && synchronized(collapsed) { collapsed.containsKey(v) }

    private fun collapse(v: View) {
        synchronized(collapsed) {
            if (collapsed.containsKey(v)) return
            val lp = v.layoutParams
            val mlp = lp as? ViewGroup.MarginLayoutParams
            collapsed[v] = intArrayOf(v.visibility, lp?.height ?: Int.MIN_VALUE, mlp?.topMargin ?: 0, mlp?.bottomMargin ?: 0)
            anyCollapsed = true
        }
        v.visibility = View.GONE
        v.layoutParams?.let { v.layoutParams = it } // setLayoutParams hook 会把高度和上下边距置 0
    }

    private fun expand(v: View) {
        if (!anyCollapsed) return
        val orig = synchronized(collapsed) { collapsed.remove(v) } ?: return
        v.layoutParams?.let { lp ->
            if (orig[1] != Int.MIN_VALUE) lp.height = orig[1]
            (lp as? ViewGroup.MarginLayoutParams)?.let { it.topMargin = orig[2]; it.bottomMargin = orig[3] }
            v.layoutParams = lp
        }
        v.visibility = orig[0]
    }

    /** “隐藏 AI 搜索入口”：搜索框里的 YOYO 图标、多模态“+”、首页 AI 背景 */
    private val AI_ENTRY_IDS = listOf("iv_yoyo_icon", "iv_yoyo_add", "yoyo_multi_modal_root", "iv_ai_bg")

    private fun idsToHide(ctx: Context): Set<Int> {
        hiddenIds?.let { return it }
        val s = HashSet<Int>()
        if (sc.hideAi) {
            for (n in AI_ENTRY_IDS) {
                val id = ctx.resources.getIdentifier(n, "id", S)
                if (id != 0) s += id
            }
        }
        hiddenIds = s
        return s
    }

    private fun hookViewEngine() {
        safe("View.setVisibility") {
            XposedHelpers.findAndHookMethod(View::class.java, "setVisibility", Int::class.javaPrimitiveType,
                before("setVisibility") { p ->
                    if (p.args[0] == View.GONE) return@before
                    val v = p.thisObject as View
                    if (isCollapsed(v)) { p.args[0] = View.GONE; return@before }
                    val id = v.id
                    if (id != View.NO_ID && sc.hideAi && id in idsToHide(v.context)) p.args[0] = View.GONE
                })
        }
        safe("View.setLayoutParams") {
            XposedHelpers.findAndHookMethod(View::class.java, "setLayoutParams", ViewGroup.LayoutParams::class.java,
                before("setLayoutParams") { p ->
                    if (!anyCollapsed) return@before
                    val v = p.thisObject as View
                    if (!isCollapsed(v)) return@before
                    val lp = p.args[0] as? ViewGroup.LayoutParams ?: return@before
                    lp.height = 0
                    (lp as? ViewGroup.MarginLayoutParams)?.let { it.topMargin = 0; it.bottomMargin = 0 }
                })
        }
        safe("View.onAttachedToWindow") {
            XposedHelpers.findAndHookMethod(View::class.java, "onAttachedToWindow",
                after("onAttachedToWindow") { p ->
                    val v = p.thisObject as View
                    val id = v.id
                    if (id != View.NO_ID && sc.hideAi && id in idsToHide(v.context)) v.visibility = View.GONE
                    val dw = darkWordCls
                    if (dw != null && dw.isInstance(v) && sc.darkWord == SearchKeys.DARK_FIXED) applyFixedDarkWord(v)
                })
        }
    }

    // ================================================================== 首页楼层（按布局名折叠）
    private val layoutKind = HashMap<Int, String>()

    /** 布局名 → 开关；返回 null 表示不处理 */
    private fun kindOf(name: String): String? = when {
        name == "general_tab_ad_card" || name == "item_home_feed_ad" || name == "item_home_feed_single_uptext_downimg_adv" ||
            name.startsWith("item_activate_ad") || name == "item_activate_icon_ad" ||
            name.startsWith("item_search_recommend_ad_") || name.startsWith("item_hot_search_app_ad_") ||
            name == "item_hot_search_game_app_ad_v2" || name == "item_hot_mini_game_app_ad" ||
            name.startsWith("banner_item_ad_") -> "card:" + SearchKeys.CARD_AD
        name == "item_hot_search_floor_b" || name == "item_hot_search_floor_multiple" ||
            name == "item_hot_search_floor_v2" || name == "layout_hotsearch_indicator_view" -> "home:" + SearchKeys.HOME_HOT
        name == "banner_list_view" || name == "banner_scroll_view" -> "home:" + SearchKeys.HOME_BANNER
        name.startsWith("item_home_feed_") -> "home:" + SearchKeys.HOME_FEED
        name.startsWith("item_search_history") || name.startsWith("item_search_card_history") -> "home:" + SearchKeys.HOME_HISTORY
        else -> null
    }

    private fun hookInflate() = safe("LayoutInflater.inflate(floors)") {
        XposedHelpers.findAndHookMethod(LayoutInflater::class.java, "inflate", Int::class.javaPrimitiveType, ViewGroup::class.java, Boolean::class.javaPrimitiveType,
            after("inflate") { p ->
                val s = sc
                if (s.hideHome.isEmpty() && SearchKeys.CARD_AD !in s.hideCards) return@after
                if (p.args[2] == true && p.args[1] != null) return@after // 返回的是父布局
                val v = p.result as? View ?: return@after
                val id = p.args[0] as Int
                val kind = synchronized(layoutKind) {
                    layoutKind.getOrPut(id) {
                        try {
                            if (v.resources.getResourcePackageName(id) != S) "" else kindOf(v.resources.getResourceEntryName(id)) ?: ""
                        } catch (_: Throwable) {
                            ""
                        }
                    }
                }
                if (kind.isEmpty()) return@after
                val hide = if (kind.startsWith("card:")) kind.removePrefix("card:") in s.hideCards else kind.removePrefix("home:") in s.hideHome
                if (hide) collapse(v)
            })
    }

    // ================================================================== 搜索框暗词
    private fun hookDarkWords() = safe("DarkWordViewSwitcher") {
        val cls = XposedHelpers.findClass("$S.feature.mainpage.presentation.view.DarkWordViewSwitcher", cl)
        darkWordCls = cls
        val wordCls = XposedHelpers.findClass("$S.feature.mainpage.data.remote.model.SearchDarkWord", cl)
        var setList: Method? = null
        var bind: Method? = null
        for (m in cls.declaredMethods) {
            val pt = m.parameterTypes
            if (pt.size == 3 && pt[0] == List::class.java && pt[1] == Boolean::class.javaPrimitiveType && pt[2] == Boolean::class.javaPrimitiveType) setList = m
            if (pt.size == 2 && pt[0] == View::class.java && pt[1] == wordCls) bind = m
        }
        requireNotNull(setList) { "setDarkWords(List,Z,Z) not found" }
        XposedBridge.hookMethod(setList, object : de.robv.android.xposed.XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                try {
                    if (sc.darkWord != SearchKeys.DARK_DEFAULT) param.args[0] = ArrayList<Any>()
                } catch (t: Throwable) { Lg.e("darkWord before error", t) }
            }

            override fun afterHookedMethod(param: MethodHookParam) {
                try {
                    if (sc.darkWord == SearchKeys.DARK_FIXED) applyFixedDarkWord(param.thisObject as View)
                } catch (t: Throwable) { Lg.e("darkWord after error", t) }
            }
        })
        bind?.let {
            XposedBridge.hookMethod(it, after("darkWordBind") { p ->
                if (sc.darkWord == SearchKeys.DARK_FIXED) applyFixedDarkWord(p.thisObject as View)
            })
        }
    }

    private fun applyFixedDarkWord(switcher: View) {
        val g = switcher as? ViewGroup ?: return
        val text = sc.darkWordText
        val id = g.resources.getIdentifier("et_search_input_text", "id", S)
        for (i in 0 until g.childCount) {
            val child = g.getChildAt(i) ?: continue
            val tv = (if (id != 0) child.findViewById<View>(id) else null) as? TextView ?: firstTextView(child) ?: continue
            if (tv.text?.toString() != text) tv.text = text
        }
    }

    private fun firstTextView(v: View): TextView? {
        if (v is TextView) return v
        if (v is ViewGroup) for (i in 0 until v.childCount) firstTextView(v.getChildAt(i) ?: continue)?.let { return it }
        return null
    }

    // ================================================================== 搜索范围
    /** 范围 key → setting_save_file 中的键（含旧版本迁移前的键） */
    private val SCOPE_PREF_KEYS: Map<String, List<String>> = mapOf(
        "searchNotePad" to listOf("searchNotePad", "searchMemo"),
        "searchMms" to listOf("searchMms"),
        "searchContact" to listOf("searchContact"),
        "searchSetting" to listOf("searchSetting"),
        "searchCalendar" to listOf("searchCalendar"),
        "searchFile" to listOf("searchFile", "searchNotepad"),
        "searchEmail" to listOf("searchEmail"),
        "searchGallery" to listOf("searchGallery"),
        "searchHnCollect" to listOf("searchHnCollect"),
        SearchKeys.SCOPE_APP_SERVICE to listOf("appService", "systemManager"),
    )
    private val SCOPE_MMKV_KEYS = mapOf(
        SearchKeys.SCOPE_RED_NOTE to "key_search_scope_red_note",
        SearchKeys.SCOPE_DIAN_PING to "key_search_scope_dian_ping",
    )
    /** 关闭时从本地搜索源列表里去掉的包名 */
    private val SCOPE_PACKAGES: Map<String, List<String>> = mapOf(
        "searchNotePad" to listOf("com.hihonor.notepad"),
        "searchMms" to listOf("com.hihonor.mms"),
        "searchContact" to listOf("com.hihonor.contacts", "com.android.providers.contacts"),
        "searchSetting" to listOf("com.android.settings"),
        "searchCalendar" to listOf("com.hihonor.calendar", "com.android.providers.calendar"),
        "searchFile" to listOf("com.hihonor.searchservice.file", "com.hihonor.filemanager"),
        "searchEmail" to listOf("com.hihonor.email"),
        "searchGallery" to listOf("com.hihonor.photos"),
        "searchHnCollect" to listOf("com.hihonor.collectcenter"),
        SearchKeys.SCOPE_APP_SERVICE to listOf("com.hihonor.searchservice.appservice", "com.hihonor.systemmanager"),
    )

    @Volatile private var forcedFor: SearchCfg? = null
    @Volatile private var forcedPrefs: Map<String, Boolean> = emptyMap()
    @Volatile private var forcedMmkv: Map<String, Boolean> = emptyMap()

    private fun refreshForced() {
        val s = sc
        if (forcedFor === s) return
        val prefs = HashMap<String, Boolean>()
        val mmkv = HashMap<String, Boolean>()
        for ((key, _) in SearchKeys.SCOPES) {
            val mode = s.scopeOf(key)
            if (mode == SearchKeys.SCOPE_FOLLOW) continue
            val on = mode == SearchKeys.SCOPE_ON
            SCOPE_PREF_KEYS[key]?.forEach { prefs[it] = on }
            SCOPE_MMKV_KEYS[key]?.let { mmkv[it] = on }
        }
        // 原生“搜索范围”页里的广告卡片 / 应用推荐开关
        if (SearchKeys.CARD_AD in s.hideCards || s.reqNoAd) prefs["searchAdCard"] = false
        if (SearchKeys.CARD_REC_APP in s.hideCards) prefs["searchAppRecommend"] = false
        forcedPrefs = prefs
        forcedMmkv = mmkv
        forcedFor = s
    }

    private fun hookScopePrefs() = safe("SharedPreferencesImpl.getBoolean(scope)") {
        val cls = XposedHelpers.findClass("android.app.SharedPreferencesImpl", null)
        XposedHelpers.findAndHookMethod(cls, "getBoolean", String::class.java, Boolean::class.javaPrimitiveType,
            before("getBoolean") { p ->
                val key = p.args[0] as? String ?: return@before
                refreshForced()
                val v = forcedPrefs[key] ?: return@before
                val file = try { XposedHelpers.getObjectField(p.thisObject, "mFile") as? File } catch (_: Throwable) { null }
                if (file != null && !file.name.startsWith("setting_save_file")) return@before
                p.result = v
            })
    }

    /** 小红书 / 大众点评 开关存在 MMKV（类名保留、方法名混淆）：hook 所有 (String, boolean) → boolean 的方法 */
    private fun hookScopeMmkv() = safe("MMKV.decodeBool(scope)") {
        val cls = XposedHelpers.findClass("com.tencent.mmkv.MMKV", cl)
        var n = 0
        for (m in cls.declaredMethods) {
            if (Modifier.isStatic(m.modifiers) || Modifier.isNative(m.modifiers)) continue
            val pt = m.parameterTypes
            if (m.returnType != Boolean::class.javaPrimitiveType || pt.size != 2 || pt[0] != String::class.java || pt[1] != Boolean::class.javaPrimitiveType) continue
            XposedBridge.hookMethod(m, before("mmkvBool") { p ->
                val key = p.args[0] as? String ?: return@before
                refreshForced()
                forcedMmkv[key]?.let { p.result = it }
            })
            n++
        }
        require(n > 0) { "no (String,boolean)boolean method" }
    }

    private data class ScopeTarget(val cls: String, val method: String)

    /** 本地搜索源列表 stb.d()：static、无参、返回 ArrayList、用到各系统应用包名 */
    private fun resolveScopeMethod(lpparam: XC_LoadPackage.LoadPackageParam): ScopeTarget {
        val apk = lpparam.appInfo?.sourceDir
        val apkFile = apk?.let(::File)
        val key = if (apkFile != null) "${apkFile.length()}-${apkFile.lastModified()}-s1" else "unknown"
        val cacheFile = lpparam.appInfo?.dataDir?.let { File(it, "files/libraryplus_dexkit_search.json") }
        try {
            if (cacheFile != null && cacheFile.canRead()) {
                val o = JSONObject(cacheFile.readText())
                if (o.optString("key") == key) {
                    val t = ScopeTarget(o.getString("cls"), o.getString("method"))
                    Lg.i("search dexkit cache hit: $t")
                    return t
                }
            }
        } catch (t: Throwable) {
            Lg.e("read search dexkit cache failed", t)
        }
        if (apk != null) {
            try {
                System.loadLibrary("dexkit")
                DexKitBridge.create(apk).use { bridge ->
                    val m = bridge.findMethod {
                        matcher {
                            usingStrings("com.hihonor.collectcenter", "com.hihonor.email", "com.hihonor.mms", "com.hihonor.android.launcher")
                            returnType = "java.util.ArrayList"
                            paramCount = 0
                        }
                    }.singleOrNull()
                    if (m != null) {
                        val t = ScopeTarget(m.className, m.methodName)
                        Lg.i("search dexkit result: $t")
                        try {
                            cacheFile?.parentFile?.mkdirs()
                            cacheFile?.writeText(JSONObject().apply { put("key", key); put("cls", t.cls); put("method", t.method) }.toString())
                        } catch (e: Throwable) {
                            Lg.e("write search dexkit cache failed", e)
                        }
                        return t
                    }
                    Lg.e("search dexkit: scope list method not found, use fallback")
                }
            } catch (t: Throwable) {
                Lg.e("search dexkit failed, use fallback", t)
            }
        }
        return ScopeTarget(FALLBACK_SCOPE_CLASS, FALLBACK_SCOPE_METHOD)
    }

    private fun hookScopeList(t: ScopeTarget) = safe("LocalSearchSources(${t.cls}.${t.method})") {
        val cls = XposedHelpers.findClass(t.cls, cl)
        val m = cls.declaredMethods.first {
            it.name == t.method && Modifier.isStatic(it.modifiers) && it.parameterTypes.isEmpty() && java.util.ArrayList::class.java.isAssignableFrom(it.returnType)
        }
        XposedBridge.hookMethod(m, after("scopeList") { p ->
            val s = sc
            if (s.scope.isEmpty()) return@after
            @Suppress("UNCHECKED_CAST")
            val list = p.result as? java.util.ArrayList<Any?> ?: return@after
            val remove = HashSet<String>()
            for ((key, pkgs) in SCOPE_PACKAGES) if (s.scopeOf(key) == SearchKeys.SCOPE_OFF) remove += pkgs
            if (remove.isEmpty()) return@after
            val before = list.size
            list.removeAll { it is String && (it in remove || (SearchKeys.SCOPE_OFF == s.scopeOf("searchFile") && it.endsWith(".file"))) }
            if (list.size != before) Lg.d("scope list: removed ${before - list.size} sources")
        })
    }

    // ================================================================== 结果页标签（实验性）
    private fun hookTabs() = safe("ResultModel\$Tab.<init>(hideTabs)") {
        val tabCls = XposedHelpers.findClass("$S.feature.resultpage.domain.model.ResultModel\$Tab", cl)
        val modelCls = XposedHelpers.findClass("$S.feature.resultpage.domain.model.TabModel", cl)
        val idField = modelCls.declaredFields.first { it.type == Long::class.javaPrimitiveType && !Modifier.isStatic(it.modifiers) }.apply { isAccessible = true }
        // 静态 copy(TabModel, boolean, int index, padding, int mask)
        val copy = modelCls.declaredMethods.firstOrNull { m ->
            val pt = m.parameterTypes
            Modifier.isStatic(m.modifiers) && m.returnType == modelCls && pt.size == 5 && pt[0] == modelCls &&
                pt[1] == Boolean::class.javaPrimitiveType && pt[2] == Int::class.javaPrimitiveType && pt[4] == Int::class.javaPrimitiveType
        }?.apply { isAccessible = true }
        val ctors = tabCls.declaredConstructors.filter { c ->
            val pt = c.parameterTypes
            pt.size == 5 && pt[1] == modelCls && List::class.java.isAssignableFrom(pt[2])
        }
        require(ctors.isNotEmpty()) { "ctor not found" }
        ctors.forEach { c ->
            XposedBridge.hookMethod(c, before("ResultTab") { p ->
                val hide = sc.hideTabs
                if (hide.isEmpty()) return@before
                val tabs = p.args[2] as? List<*> ?: return@before
                val selectedId = p.args[1]?.let { idField.getLong(it) }
                val kept = tabs.filter { t -> t == null || !modelCls.isInstance(t) || idField.getLong(t).let { id -> id == selectedId || !(id in hide || (id == 6L && 2L in hide)) } }
                if (kept.size == tabs.size) return@before
                // 重新编号 index（mask 里去掉 bit 8 = 不保留 index）
                val reindexed = if (copy != null) kept.mapIndexed { idx, t ->
                    if (t != null && modelCls.isInstance(t)) copy.invoke(null, t, false, idx, null, 0xFF xor 8) else t
                } else kept
                p.args[2] = ArrayList(reindexed)
                if (copy != null && selectedId != null) {
                    reindexed.firstOrNull { it != null && modelCls.isInstance(it) && idField.getLong(it) == selectedId }?.let { p.args[1] = it }
                }
            })
        }
    }

    // ================================================================== 搜索引擎
    private val inEngineJson = ThreadLocal<Int>()

    private fun hookEngine() = safe("SearchEngine.getUrl/getWebTabUrl/getCombineTabUrl") {
        val cls = XposedHelpers.findClass("$S.feature.mainpage.data.remote.model.SearchEngine", cl)
        // 保存用户选择时（Moshi toJson）不替换，避免把我们的模板写进荣耀搜索自己的设置
        try {
            val adapter = XposedHelpers.findClass("$S.feature.mainpage.data.remote.model.SearchEngineJsonAdapter", cl)
            adapter.declaredMethods.filter { it.name == "toJson" }.forEach { m ->
                XposedBridge.hookMethod(m, object : de.robv.android.xposed.XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) { inEngineJson.set((inEngineJson.get() ?: 0) + 1) }
                    override fun afterHookedMethod(param: MethodHookParam) { inEngineJson.set((inEngineJson.get() ?: 1) - 1) }
                })
            }
        } catch (t: Throwable) {
            Lg.e("SearchEngineJsonAdapter guard not installed", t)
        }
        for (name in listOf("getUrl", "getWebTabUrl", "getCombineTabUrl")) {
            XposedHelpers.findAndHookMethod(cls, name, after("engine.$name") { p ->
                val s = sc
                if (s.engine == SearchKeys.ENGINE_FOLLOW) return@after
                if ((inEngineJson.get() ?: 0) > 0) return@after
                val tpl = SearchKeys.engineTemplate(s.engine, s.engineUrl) ?: return@after
                p.result = tpl
            })
        }
    }

    // ================================================================== 网页结果用外部浏览器打开
    private fun hookExternalBrowser() = safe("WebDetailActivity.onCreate(externalBrowser)") {
        val cls = XposedHelpers.findClass("$S.feature.mainpage.presentation.page.web.WebDetailActivity", cl)
        XposedHelpers.findAndHookMethod(cls, "onCreate", Bundle::class.java, after("WebDetailActivity.onCreate") { p ->
            if (!sc.externalBrowser || p.args[0] != null) return@after
            val act = p.thisObject as Activity
            val url = act.intent?.getStringExtra("WEB_DETAIL_URL") ?: return@after
            if (!(url.startsWith("http://") || url.startsWith("https://"))) return@after
            val host = try { Uri.parse(url).host.orEmpty() } catch (_: Throwable) { "" }
            if (host.endsWith("honor.com") || host.endsWith("hihonorcloud.com")) return@after // 协议 / 隐私等内部页面
            try {
                act.startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse(url))
                        .addCategory(Intent.CATEGORY_BROWSABLE)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
                Lg.i("externalBrowser: opened $host")
                act.finish()
            } catch (t: Throwable) {
                Lg.e("externalBrowser: no browser, keep in-app", t)
            }
        })
    }
}
