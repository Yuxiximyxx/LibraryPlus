package io.github.yuxiximyxx.libraryplus.hook

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import io.github.yuxiximyxx.libraryplus.Const
import io.github.yuxiximyxx.libraryplus.LpConfig
import org.json.JSONObject
import org.luckypray.dexkit.DexKitBridge
import java.io.File
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import kotlin.math.abs
import kotlin.math.max

/**
 * 资源库界面 com.hihonor.hiboard（magiclibrary 模块，部分类被混淆）。
 * 混淆类用 DexKit 按日志字符串定位，结果按 APK 指纹缓存。
 */
internal object HiboardHooks {
    private const val ML = "com.hihonor.magiclibrary"
    private const val DEAL_FOLDER = "$ML.appmanager.domain.intercept.initialize.DealAppIFolderInfoStrategy"
    private const val MAIN_VIEW = "$ML.mainpage.presentation.ui.MainView"

    // 11.1.4.302 中的已知名字，DexKit 失败时兜底
    private const val FALLBACK_HIDDEN_CLASS = "$ML.appmanager.domain.usecase.q"
    private const val FALLBACK_HIDDEN_METHOD = "a"
    private const val FALLBACK_FOLDER_CLASS = "$ML.appmanager.domain.model.f"

    private lateinit var cl: ClassLoader
    private val cfg: LpConfig get() = HookConfig.cfg

    fun install(lpparam: XC_LoadPackage.LoadPackageParam) {
        cl = lpparam.classLoader
        val targets = resolveTargets(lpparam)
        hookCollectAppByFolderType()
        hookHiddenUseCase(targets.hiddenClass, targets.hiddenMethod)
        hookFolderTitle(targets.folderClass)
        hookSlideFromRight()
        hookSwipeToClose()
    }

    fun onAppCreated(ctx: Context) {
        HookConfig.onRequestCategories = { scheduleCategoryReport(0L) }
    }

    // ------------------------------------------------------------------ 真实分类上报
    /** 包名 → hiboard 原始分类（101–112），供模块“自定义分类”页显示 */
    private val categories = HashMap<String, Int>()
    private val main = Handler(Looper.getMainLooper())
    private val reportRunnable = Runnable { sendCategories() }

    private fun recordCategory(pkg: String, folderType: Int) {
        if (folderType !in Const.FOLDER_IDS) return
        val changed = synchronized(categories) { categories.put(pkg, folderType) != folderType }
        if (changed) scheduleCategoryReport(2000L)
    }

    private fun scheduleCategoryReport(delay: Long) {
        main.removeCallbacks(reportRunnable)
        main.postDelayed(reportRunnable, delay)
    }

    private fun sendCategories() {
        val ctx = HookConfig.appContext ?: return
        val json = synchronized(categories) { JSONObject(categories as Map<*, *>).toString() }
        if (json == "{}") {
            Lg.i("categories: nothing collected yet (open the resource library once)")
            return
        }
        Thread({
            HookConfig.callProvider(ctx, Const.METHOD_PUT_CATEGORIES, json)
            Lg.i("categories reported: ${synchronized(categories) { categories.size }} apps")
        }, "LibraryPlus-cat").start()
    }

    // ------------------------------------------------------------------ DexKit
    private data class Targets(val hiddenClass: String, val hiddenMethod: String, val folderClass: String)

    private fun resolveTargets(lpparam: XC_LoadPackage.LoadPackageParam): Targets {
        val apk = lpparam.appInfo?.sourceDir
        val apkFile = apk?.let(::File)
        val key = if (apkFile != null) "${apkFile.length()}-${apkFile.lastModified()}-v1" else "unknown"
        val cacheFile = lpparam.appInfo?.dataDir?.let { File(it, "files/libraryplus_dexkit.json") }

        // 1. 缓存
        try {
            if (cacheFile != null && cacheFile.canRead()) {
                val o = JSONObject(cacheFile.readText())
                if (o.optString("key") == key) {
                    val t = Targets(o.getString("hiddenClass"), o.getString("hiddenMethod"), o.getString("folderClass"))
                    Lg.i("dexkit cache hit: $t")
                    return t
                }
            }
        } catch (t: Throwable) {
            Lg.e("read dexkit cache failed", t)
        }

        // 2. DexKit
        var hiddenClass = FALLBACK_HIDDEN_CLASS
        var hiddenMethod = FALLBACK_HIDDEN_METHOD
        var folderClass = FALLBACK_FOLDER_CLASS
        var ok = false
        if (apk != null) {
            try {
                System.loadLibrary("dexkit")
                DexKitBridge.create(apk).use { bridge ->
                    val m = bridge.findMethod {
                        searchPackages(ML)
                        matcher {
                            usingStrings("checkAppIsHidden isParallelSpaceApp")
                            returnType = "boolean"
                            paramTypes("java.lang.String", "int")
                        }
                    }.singleOrNull()
                    val c = bridge.findClass {
                        searchPackages("$ML.appmanager.domain.model")
                        matcher { usingStrings("FolderAppsCategory") }
                    }.singleOrNull()
                    if (m != null) {
                        hiddenClass = m.className; hiddenMethod = m.methodName
                    } else Lg.e("dexkit: hidden usecase not found, use fallback")
                    if (c != null) folderClass = c.name else Lg.e("dexkit: folder model not found, use fallback")
                    ok = m != null && c != null
                }
            } catch (t: Throwable) {
                Lg.e("dexkit failed, use fallback names", t)
            }
        }
        val t = Targets(hiddenClass, hiddenMethod, folderClass)
        Lg.i("dexkit result ok=$ok: $t")
        if (ok && cacheFile != null) {
            try {
                cacheFile.parentFile?.mkdirs()
                cacheFile.writeText(JSONObject().apply {
                    put("key", key); put("hiddenClass", hiddenClass); put("hiddenMethod", hiddenMethod); put("folderClass", folderClass)
                }.toString())
            } catch (e: Throwable) {
                Lg.e("write dexkit cache failed", e)
            }
        }
        return t
    }

    // ------------------------------------------------------------------ 分类 / 隐藏
    private val pkgGetterCache = HashMap<Class<*>, Method?>()

    /** appResource(model.c) → 包名：找一个返回值带 getPackageName() 的无参方法。 */
    private fun packageOf(appResource: Any): String? {
        val cls = appResource.javaClass
        val getter = synchronized(pkgGetterCache) {
            pkgGetterCache.getOrPut(cls) {
                var found: Method? = null
                var c: Class<*>? = cls
                while (c != null && found == null) {
                    for (m in c.declaredMethods) {
                        if (m.parameterTypes.isNotEmpty() || Modifier.isStatic(m.modifiers)) continue
                        val rt = m.returnType
                        if (rt.isPrimitive || rt == String::class.java) continue
                        val gp = try { rt.getMethod("getPackageName") } catch (_: Throwable) { null }
                        if (gp != null && gp.returnType == String::class.java) {
                            m.isAccessible = true; found = m; break
                        }
                    }
                    c = c.superclass
                }
                Lg.i("package getter for ${cls.name}: ${found?.name}")
                found
            }
        } ?: return null
        val info = getter.invoke(appResource) ?: return null
        return XposedHelpers.callMethod(info, "getPackageName") as? String
    }

    /**
     * DealAppIFolderInfoStrategy.collectAppByFolderType(appResource, folderType, map)
     * - 隐藏：直接跳过（不放进任何文件夹）
     * - 自定义分类：替换 folderType（只替换 101–112 之间的普通分类）
     */
    private fun hookCollectAppByFolderType() = safe("DealAppIFolderInfoStrategy.collectAppByFolderType") {
        val cls = XposedHelpers.findClass(DEAL_FOLDER, cl)
        val method = cls.declaredMethods.first { it.name == "collectAppByFolderType" && it.parameterTypes.size == 3 }
        XposedBridge.hookMethod(method, before("collectAppByFolderType") { p ->
            val c = cfg
            val pkg = packageOf(p.args[0] ?: return@before) ?: return@before
            (p.args[1] as? Int)?.let { recordCategory(pkg, it) }
            if (c.hidden.isEmpty() && c.mapping.isEmpty()) return@before
            if (pkg in c.hidden) {
                p.result = null
                return@before
            }
            val target = c.mapping[pkg] ?: return@before
            val orig = p.args[1] as? Int ?: return@before
            if (orig in Const.FOLDER_IDS && target in Const.FOLDER_IDS && orig != target) {
                p.args[1] = target
            }
        })
    }

    /** usecase checkAppIsHidden(packageName, userId)：返回 true 即视为隐藏（搜索等其它入口也生效）。 */
    private fun hookHiddenUseCase(className: String, methodName: String) = safe("HiddenAppIconUseCase.$methodName") {
        val cls = XposedHelpers.findClass(className, cl)
        val m = cls.declaredMethods.first {
            it.name == methodName && it.returnType == Boolean::class.javaPrimitiveType &&
                it.parameterTypes.contentEquals(arrayOf(String::class.java, Int::class.javaPrimitiveType))
        }
        XposedBridge.hookMethod(m, before("checkAppIsHidden") { p ->
            val pkg = p.args[0] as? String ?: return@before
            if (pkg in cfg.hidden) p.result = true
        })
    }

    // ------------------------------------------------------------------ 文件夹改名
    @Volatile
    private var idField: Field? = null

    private fun hookFolderTitle(className: String) = safe("FolderModel title ($className)") {
        val cls = XposedHelpers.findClass(className, cl)
        // 构造函数 (int folderId, int titleResId, String name, CopyOnWriteArrayList apps, boolean)：标定 id 字段
        val ctors = cls.declaredConstructors.filter {
            val pt = it.parameterTypes
            pt.size >= 3 && pt[0] == Int::class.javaPrimitiveType && pt[1] == Int::class.javaPrimitiveType && pt[2] == String::class.java
        }
        require(ctors.isNotEmpty()) { "folder model ctor not found" }
        ctors.forEach { ctor ->
            XposedBridge.hookMethod(ctor, after("folderCtor") { p ->
                if (idField != null) return@after
                val id = p.args[0] as Int
                val res = p.args[1] as Int
                if (id == res) return@after
                val f = cls.declaredFields.firstOrNull { f ->
                    f.type == Int::class.javaPrimitiveType && !Modifier.isStatic(f.modifiers) &&
                        run { f.isAccessible = true; f.getInt(p.thisObject) == id }
                }
                if (f != null) {
                    idField = f
                    Lg.i("folder id field = ${f.name}")
                }
            })
        }
        // 标题 getter：无参、返回 String、非 toString
        val getters = cls.declaredMethods.filter {
            it.parameterTypes.isEmpty() && it.returnType == String::class.java && it.name != "toString" && !Modifier.isStatic(it.modifiers)
        }
        require(getters.isNotEmpty()) { "folder title getter not found" }
        getters.forEach { g ->
            XposedBridge.hookMethod(g, after("folderTitle") { p ->
                if (cfg.folderNames.isEmpty()) return@after
                val f = idField ?: return@after
                val id = f.getInt(p.thisObject)
                cfg.customName(id)?.let { p.result = it }
            })
        }
        Lg.i("folder title getters: ${getters.joinToString { it.name }}")
    }

    // ------------------------------------------------------------------ 从右侧滑入
    /**
     * MainViewManager.f(transY) 通过 mainView.setTranslationY(height + transY) 驱动上滑进入。
     * 开关打开时把纵向位移换成等比例的横向位移。
     */
    private fun hookSlideFromRight() = safe("MainView.setTranslationY") {
        val mainView = XposedHelpers.findClass(MAIN_VIEW, cl)
        XposedHelpers.findAndHookMethod(View::class.java, "setTranslationY", Float::class.javaPrimitiveType,
            before("setTranslationY") { p ->
                val v = p.thisObject as View
                if (!mainView.isInstance(v)) return@before
                if (!cfg.slideFromRight) {
                    if (v.translationX != 0f) v.translationX = 0f
                    return@before
                }
                val h = v.height
                val w = v.width
                if (h <= 0 || w <= 0) return@before
                val y = p.args[0] as Float
                val frac = (y / h).coerceIn(0f, 1f)
                v.translationX = frac * w
                p.args[0] = 0f
            })
    }

    // ------------------------------------------------------------------ 右滑关闭
    private const val LIB_WORKSPACE = "com.hihonor.intelligent.feature.workspace.presentation.library.HiLibraryWorkSpace"

    private var gDownX = 0f
    private var gDownY = 0f
    private var gDecided = false
    private var gClosing = false
    private var gDownTime = -1L

    /**
     * 资源库窗口根 HiLibraryWorkSpace：原生的关闭手势是“内容在顶部时下拉”，
     * 由 BaseWorkSpace.onInterceptTouchEvent → determineStartScroll 拦截，再在 onTouchEvent 里按 Y 位移跟手、松手时按速度/进度吸附。
     * 这里把“手指从左往右”的横向手势换算成等量的下拉（Y = 按下点 + 右移距离），
     * 并让 determineStartScroll 在这种手势下直接返回 true，从而复用原生的跟手 + 吸附 + 关闭流程。
     */
    private fun hookSwipeToClose() {
        val ws = try {
            XposedHelpers.findClass(LIB_WORKSPACE, cl)
        } catch (t: Throwable) {
            synchronized(HookReport) { HookReport.failed += "HiLibraryWorkSpace(class)" }
            Lg.e("HiLibraryWorkSpace not found", t)
            return
        }
        safe("HiLibraryWorkSpace.dispatchTouchEvent(swipeRightToClose)") {
            XposedHelpers.findAndHookMethod(ws, "dispatchTouchEvent", MotionEvent::class.java,
                before("libDispatchTouchEvent") { p ->
                    val ev = p.args[0] as? MotionEvent ?: return@before
                    val v = p.thisObject as ViewGroup
                    when (ev.actionMasked) {
                        MotionEvent.ACTION_DOWN -> {
                            gDownX = ev.x; gDownY = ev.y; gDownTime = ev.downTime
                            gDecided = false; gClosing = false
                            return@before
                        }
                    }
                    if (ev.downTime != gDownTime) return@before
                    if (!gClosing && !gDecided && ev.actionMasked == MotionEvent.ACTION_MOVE) {
                        if (!cfg.rightEntry) { gDecided = true; return@before }
                        val dx = ev.x - gDownX
                        val dy = ev.y - gDownY
                        val slop = ViewConfiguration.get(v.context).scaledTouchSlop.toFloat()
                        if (dx > slop * 1.5f && dx > abs(dy) * 1.5f) {
                            gDecided = true
                            val why = blockReason(v, ev)
                            if (why != null) {
                                Lg.i("swipeClose: skip ($why) dx=$dx dy=$dy")
                            } else {
                                gClosing = true
                                v.requestDisallowInterceptTouchEvent(false)
                                Lg.i("swipeClose: START dx=$dx dy=$dy")
                            }
                        } else if (abs(dy) > slop * 1.5f || dx < -slop * 1.5f) {
                            gDecided = true // 纵向滚动 / 反方向：不处理
                            Lg.d("swipeClose: not horizontal-right, ignore dx=$dx dy=$dy")
                        }
                    }
                    if (gClosing) {
                        val c = MotionEvent.obtain(ev)
                        c.setLocation(gDownX, gDownY + max(0f, ev.x - gDownX))
                        p.args[0] = c
                        if (ev.actionMasked == MotionEvent.ACTION_UP || ev.actionMasked == MotionEvent.ACTION_CANCEL) {
                            Lg.i("swipeClose: release dx=${ev.x - gDownX}")
                            gClosing = false
                        }
                    }
                })
        }
        safe("HiLibraryWorkSpace.determineStartScroll(swipeRightToClose)") {
            XposedHelpers.findAndHookMethod(ws, "determineStartScroll", MotionEvent::class.java,
                before("determineStartScroll") { p ->
                    if (gClosing) p.result = true
                })
        }
    }

    /** 返回不应接管的原因；null 表示可以关闭 */
    private fun blockReason(root: View, ev: MotionEvent): String? {
        if (ev.pointerCount > 1) return "multi touch"
        // 菜单展开 / 拖动文件夹（HiLibraryWorkSpace 的 isOpenMenu / isFolderDragIng，混淆名 k / l）
        for ((name, label) in listOf("isOpenMenu" to "menu open", "k" to "menu open", "isFolderDragIng" to "folder dragging", "l" to "folder dragging")) {
            try {
                val f = XposedHelpers.findField(root.javaClass, name)
                if (f.type == Boolean::class.javaPrimitiveType && f.getBoolean(root)) return label
            } catch (_: Throwable) {
            }
        }
        // 展开的文件夹内左右翻页优先
        if (findShown(root, "ExpandFolderView", 0)) return "expanded folder shown"
        return null
    }

    private fun findShown(v: View, simpleName: String, depth: Int): Boolean {
        if (depth > 40) return false
        if (v.javaClass.simpleName == simpleName && v.isShown) return true
        if (v is ViewGroup) {
            for (i in 0 until v.childCount) {
                if (findShown(v.getChildAt(i) ?: continue, simpleName, depth + 1)) return true
            }
        }
        return false
    }
}
