package io.github.yuxiximyxx.libraryplus.hook

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.net.Uri
import android.provider.Settings
import android.view.MotionEvent
import de.robv.android.xposed.XposedHelpers
import io.github.yuxiximyxx.libraryplus.LpConfig
import kotlin.math.abs
import kotlin.math.max

/**
 * 荣耀桌面 com.hihonor.android.launcher（类名未混淆）。
 */
internal object LauncherHooks {
    private const val P = "com.hihonor.android.launcher"
    private const val RES_LIB_UTIL = "$P.resourcelib.utils.ResourceLibUtil"
    private const val RES_LIB_SETTING_UTIL = "$P.resourcelib.utils.ResourceLibSettingUtil"
    private const val HIBOARD_UTIL = "$P.utils.HiBoardUtil"
    private const val CLIENT_MANAGER = "$P.hiboard.HiBoardClientManager"
    private const val WORKSPACE = "$P.Workspace"
    private const val DRAG_LAYER = "$P.DragLayer"
    private const val FOLDER_MANAGER = "$P.folder.FolderManager"
    private const val LAUNCHER = "$P.Launcher"
    private const val LAUNCHER_STATE = "com.android.launcher3.LauncherState"
    private const val RECENTS_ANIM_UTILS = "$P.quickstep.util.RecentsAnimUtils"
    private const val LAUNCHER_ANIM_UTILS = "$P.animutils.LauncherAnimUtils"
    private const val DESKTOP_UTILS = "$P.extension.hndesktop.HnDesktopUtils"
    private const val MANUAL_STACK = "$P.manualstack.ManualStackManager"

    private lateinit var cl: ClassLoader
    private var appContext: Context? = null
    private val main = Handler(Looper.getMainLooper())

    // ---- 右侧进入的手势状态（只在主线程访问） ----
    private var rightMode = false
    private var downRawX = 0f
    private var downRawY = 0f

    private val cfg: LpConfig get() = HookConfig.cfg

    fun install(classLoader: ClassLoader) {
        cl = classLoader
        hookAllAppList()
        hookRightEntry()
        hookSwipeUpSearch()
        HookConfig.addListener { refreshLibrary() }
    }

    fun onAppCreated(ctx: Context) {
        appContext = ctx
        // 重启作用域：桌面最后退出，让资源库 / 搜索先结束
        HookConfig.restartDelayMs = 600L
        HookConfig.onRequestCategories = { refreshLibrary() }
    }

    // ------------------------------------------------------------------ 隐藏应用
    /** ResourceLibUtil.getAllAppList()：资源库从桌面拉取的应用列表，按包名过滤。 */
    private fun hookAllAppList() = safe("ResourceLibUtil.getAllAppList") {
        XposedHelpers.findAndHookMethod(RES_LIB_UTIL, cl, "getAllAppList", after("getAllAppList") { p ->
            val hidden = cfg.hidden
            if (hidden.isEmpty()) return@after
            val bundle = p.result as? Bundle ?: return@after
            @Suppress("DEPRECATION")
            val list = bundle.getParcelableArrayList<Bundle>("value") ?: return@after
            val before = list.size
            val kept = ArrayList(list.filterNot { b ->
                @Suppress("DEPRECATION")
                val intent = try { b.getParcelable<Intent>("intent") } catch (_: Throwable) { null }
                val pkg = intent?.component?.packageName ?: intent?.`package`
                pkg != null && pkg in hidden
            })
            if (kept.size != before) {
                bundle.putParcelableArrayList("value", kept)
                Lg.i("getAllAppList: filtered ${before - kept.size} hidden apps")
            }
        })
    }

    /** 配置变化后让资源库重新拉取数据（写 Settings.Global launcher_data_lastupdate）。 */
    private fun refreshLibrary() {
        main.post {
            try {
                XposedHelpers.callStaticMethod(XposedHelpers.findClass(RES_LIB_UTIL, cl), "notifyChangeToResourceLibraryNodelay")
                Lg.i("notifyChangeToResourceLibraryNodelay called")
            } catch (t: Throwable) {
                Lg.e("notifyChangeToResourceLibraryNodelay failed, fallback to Settings.Global", t)
                try {
                    val ctx = appContext ?: return@post
                    Settings.Global.putLong(ctx.contentResolver, "launcher_data_lastupdate", System.currentTimeMillis())
                } catch (t2: Throwable) {
                    Lg.e("write launcher_data_lastupdate failed", t2)
                }
            }
        }
    }

    // ------------------------------------------------------------------ 右侧进入
    /** 本次手势已尝试过打开（按 downTime 记），避免每个 MOVE 重复调用 */
    private var attemptedDownTime = -1L
    private var loggedSuppressDownTime = -1L

    private fun dp(ctx: Context?, v: Float): Float = v * (ctx?.resources?.displayMetrics?.density ?: 3f)

    private fun workspaceOf(dragLayer: Any?): Any? = try {
        val launcher = dragLayer?.let { XposedHelpers.getObjectField(it, "mLauncher") }
            ?: XposedHelpers.callStaticMethod(XposedHelpers.findClass("$P.Launcher", cl), "getLauncher")
        launcher?.let { XposedHelpers.callMethod(it, "getWorkspace") }
    } catch (_: Throwable) {
        null
    }

    private fun workspaceDragLayer(ws: Any?): Any? = try {
        val launcher = ws?.let { XposedHelpers.getObjectField(it, "mLauncher") }
        launcher?.let { XposedHelpers.callMethod(it, "getDragLayer") }
    } catch (_: Throwable) {
        null
    }

    private fun isDragging(dragLayer: Any?): Boolean = try {
        val dc = dragLayer?.let { XposedHelpers.getObjectField(it, "mDragController") }
            ?: XposedHelpers.callStaticMethod(XposedHelpers.findClass("$P.Launcher", cl), "getLauncher")
                ?.let { XposedHelpers.callMethod(it, "getDragController") }
        dc != null && XposedHelpers.callMethod(dc, "isDragging") == true
    } catch (_: Throwable) {
        false
    }

    /**
     * 是否处于“最后一页、普通模式、可以进入资源库”的状态。返回 Workspace 的 state，否则返回 null 并给出原因。
     */
    private fun lastPageState(ws: Any?, dragLayer: Any? = null): Pair<Any?, String?> {
        if (ws == null) return null to "no workspace"
        homeBlockReason(dragLayer)?.let { return null to it }
        if (XposedHelpers.getBooleanField(ws, "mIsRtl")) return null to "rtl"
        if (XposedHelpers.callMethod(ws, "isLastPage") != true) return null to "not last page"
        val state = XposedHelpers.callMethod(ws, "getState")
        if (state?.toString() != "NORMAL_MODE") return null to "state=$state"
        try {
            if (XposedHelpers.callMethod(ws, "getOpenFolder") != null) return null to "folder open"
        } catch (_: Throwable) {
        }
        if (folderOpen()) return null to "folder animating/open"
        return state to null
    }

    private fun staticBool(cls: String, method: String): Boolean =
        XposedHelpers.callStaticMethod(XposedHelpers.findClass(cls, cl), method) == true

    /**
     * 桌面是否处于“普通桌面、静止、无覆盖层”的状态；返回阻止原因，null 表示可以。
     * 任何一项无法判断（异常）都视为不可以（fail-safe）。
     * 多任务（OVERVIEW / BACKGROUND_APP / QUICK_SWITCH_FROM_HOME / OVERVIEW_MODAL_TASK）、所有应用（ALL_APPS）、
     * 双指捏合编辑（HintState）以及它们之间的过渡动画都属于非 NORMAL 或 isInTransition。
     */
    private fun homeBlockReason(dragLayer: Any?): String? {
        return try {
            val launcher = (dragLayer?.let { try { XposedHelpers.getObjectField(it, "mLauncher") } catch (_: Throwable) { null } })
                ?: XposedHelpers.callStaticMethod(XposedHelpers.findClass(LAUNCHER, cl), "getLauncher")
                ?: return "no launcher"
            val normal = XposedHelpers.getStaticObjectField(XposedHelpers.findClass(LAUNCHER_STATE, cl), "NORMAL")
            val sm = XposedHelpers.callMethod(launcher, "getStateManager") ?: return "no state manager"
            val st = XposedHelpers.callMethod(sm, "getState")
            if (st !== normal) return "launcherState=$st"
            if (XposedHelpers.callMethod(sm, "isInStableState", normal) != true) return "state not stable"
            if (XposedHelpers.callMethod(sm, "isInTransition") == true) return "state in transition"
            if (staticBool(RECENTS_ANIM_UTILS, "isOverviewState")) return "overview"
            if (staticBool(RECENTS_ANIM_UTILS, "isBackGroundAppState")) return "background app"
            if (staticBool(RECENTS_ANIM_UTILS, "isQuickSwitchState")) return "quick switch"
            if (staticBool(RECENTS_ANIM_UTILS, "isHomeOverviewState")) return "home overview (hint)"
            val recents = XposedHelpers.callMethod(launcher, "getRecentOverviewPanel") as? android.view.View
            if (recents != null && recents.visibility == android.view.View.VISIBLE) return "recents visible"
            for (m in listOf("isRecentAnimationDoing", "isEnterRecentsAnimationDoing", "isRecentTransitionAnimRunning",
                "isRecentRemovingAnimationDoing", "isWaitingRecentFinishCallback")) {
                if (staticBool(LAUNCHER_ANIM_UTILS, m)) return "anim: $m"
            }
            if (XposedHelpers.callStaticMethod(XposedHelpers.findClass(LAUNCHER_ANIM_UTILS, cl), "getBackHomeAnimatioStatus") == true) return "back-home anim"
            if (staticBool(DESKTOP_UTILS, "isInDesktopMode")) return "desktop mode"
            val ms = XposedHelpers.callStaticMethod(XposedHelpers.findClass(MANUAL_STACK, cl), "getInstance")
            val group = XposedHelpers.callMethod(ms, "findManualStackExpandGroup") as? java.util.Optional<*>
            if (group?.isPresent == true) return "manual stack expanded"
            if (dragLayer != null && XposedHelpers.getBooleanField(dragLayer, "isAnimRunning")) return "app launch anim"
            if (XposedHelpers.callMethod(launcher, "isMultiChoiceMode") == true) return "multi choice"
            // 负一屏、锁屏、通知栏、其它窗口覆盖时桌面没有焦点
            if ((launcher as? android.app.Activity)?.hasWindowFocus() != true) return "no window focus"
            null
        } catch (t: Throwable) {
            Lg.e("homeBlockReason: cannot determine launcher state, skip", t)
            "state unknown"
        }
    }

    /**
     * 尝试从最后一页打开资源库。dx/dy 为相对按下点的位移（View 坐标）。
     * @return true 表示已经交给资源库（调用方应拦截本次事件）
     */
    private fun tryOpen(source: String, ev: MotionEvent, dx: Float, dy: Float, ws: Any?, dragLayer: Any?, ctx: Context?): Boolean {
        if (attemptedDownTime == ev.downTime) return false
        val threshold = max(dp(ctx, 10f), XposedHelpers.getIntField(ws ?: return false, "mTouchSlop").toFloat())
        if (dx > -threshold || abs(dx) <= abs(dy) * 1.2f) return false
        attemptedDownTime = ev.downTime
        val (state, why) = lastPageState(ws, dragLayer)
        if (state == null) {
            if (why == "not last page") Lg.d("rightEntry[$source]: skip ($why)") else Lg.i("rightEntry[$source]: skip ($why)")
            return false
        }
        if (ev.pointerCount > 1) { Lg.i("rightEntry[$source]: skip (multi touch)"); return false }
        if (isDragging(dragLayer)) { Lg.i("rightEntry[$source]: skip (dragging icon)"); return false }
        if (!libraryEnabled()) { Lg.i("rightEntry[$source]: skip (resource library disabled in launcher settings)"); return false }
        val showLib = try {
            XposedHelpers.callStaticMethod(XposedHelpers.findClass(HIBOARD_UTIL, cl), "isShowLibrary", ev, false, state) == true
        } catch (t: Throwable) {
            Lg.e("isShowLibrary failed", t); true
        }
        if (!showLib) { Lg.i("rightEntry[$source]: skip (touch on widget/card)"); return false }

        rightMode = true
        downRawX = ev.rawX
        downRawY = ev.rawY
        val ok = try {
            XposedHelpers.callStaticMethod(XposedHelpers.findClass(HIBOARD_UTIL, cl), "scrollToHiLibrary", ev, true) == true
        } catch (t: Throwable) {
            Lg.e("scrollToHiLibrary failed", t); false
        }
        if (!ok) {
            rightMode = false
            Lg.i("rightEntry[$source]: scrollToHiLibrary returned false (resource library not connected?) dx=$dx dy=$dy")
            return false
        }
        try { XposedHelpers.callMethod(ws, "customCancelLongPress") } catch (_: Throwable) {}
        Lg.i("rightEntry[$source]: OPEN resource library, dx=$dx dy=$dy")
        return true
    }

    private fun hookRightEntry() {
        // 1) 主路径：DragLayer.onInterceptTouchEvent 的 MOVE 分支会调用 handleResourceLibEnter（上滑进入就在这里），
        //    它先于 Workspace 看到每个 MOVE，所以从任意空白位置左滑都能在这里接管。
        safe("DragLayer.handleResourceLibEnter(rightEntry+swipeUp)") {
            XposedHelpers.findAndHookMethod(DRAG_LAYER, cl, "handleResourceLibEnter", MotionEvent::class.java,
                before("handleResourceLibEnter") { p ->
                    val c = cfg
                    if (!c.rightEntry) return@before
                    val ev = p.args[0] as MotionEvent
                    val dl = p.thisObject
                    if (!rightMode && ev.actionMasked == MotionEvent.ACTION_MOVE) {
                        val dx = ev.x - XposedHelpers.getFloatField(dl, "mDownMotionX")
                        val dy = ev.y - XposedHelpers.getFloatField(dl, "mDownMotionY")
                        if (dx < 0 && abs(dx) > abs(dy)) {
                            if (XposedHelpers.getBooleanField(dl, "mIsMultiTouch")) return@before
                            if (tryOpen("DragLayer", ev, dx, dy, workspaceOf(dl), dl, (dl as? android.view.View)?.context)) {
                                p.result = true // DragLayer 拦截，后续事件交给 HiBoardClientManager
                                return@before
                            }
                        }
                    }
                })
        }

        // 2) Workspace：最后一页向左的横向手势不再开始翻页（否则 PagedView 在空白处 0.2×slop 就开始翻页），
        //    同时作为兜底入口。
        safe("Workspace.determineScrollingStart") {
            XposedHelpers.findAndHookMethod(WORKSPACE, cl, "determineScrollingStart", MotionEvent::class.java,
                before("determineScrollingStart") { p ->
                    if (!cfg.rightEntry) return@before
                    val ev = p.args[0] as MotionEvent
                    if (ev.actionMasked != MotionEvent.ACTION_MOVE || ev.pointerCount > 1) return@before
                    val ws = p.thisObject
                    if (rightMode) { p.result = null; return@before }
                    val dx = ev.x - XposedHelpers.getFloatField(ws, "mDownMotionX")
                    val dy = ev.y - XposedHelpers.getFloatField(ws, "mDownMotionY")
                    if (dx >= 0 || abs(dx) < abs(dy)) return@before
                    val (state, _) = lastPageState(ws, workspaceDragLayer(ws))
                    if (state == null || !libraryEnabled()) return@before
                    if (tryOpen("Workspace", ev, dx, dy, ws, null, (ws as? android.view.View)?.context)) {
                        p.result = null
                        return@before
                    }
                    // 最后一页向左：吞掉翻页起始，等待 DragLayer 达到阈值后打开资源库
                    if (attemptedDownTime != ev.downTime) {
                        if (loggedSuppressDownTime != ev.downTime) {
                            loggedSuppressDownTime = ev.downTime
                            Lg.d("rightEntry: suppress paging on last page, dx=$dx dy=$dy")
                        }
                        p.result = null
                    }
                })
        }

        // 3) 把向左的位移换算成向上的位移，交给原有的上滑进入逻辑
        safe("HiBoardClientManager.onTouchEvent") {
            XposedHelpers.findAndHookMethod(CLIENT_MANAGER, cl, "onTouchEvent", MotionEvent::class.java, Int::class.javaPrimitiveType,
                object : de.robv.android.xposed.XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        try {
                            if (!rightMode) return
                            val ev = param.args[0] as? MotionEvent ?: return
                            param.args[0] = convert(ev)
                        } catch (t: Throwable) {
                            Lg.e("onTouchEvent convert error", t)
                        }
                    }

                    override fun afterHookedMethod(param: MethodHookParam) {
                        try {
                            if (!rightMode) return
                            val action = (param.args[1] as Int) and 0xff
                            val ev = param.args[0] as? MotionEvent
                            val evAction = ev?.actionMasked ?: -1
                            if (action == 1 || action == 3 || evAction == MotionEvent.ACTION_UP || evAction == MotionEvent.ACTION_CANCEL) {
                                rightMode = false
                                Lg.d("rightEntry: gesture end (action=$action)")
                            }
                        } catch (t: Throwable) {
                            Lg.e("onTouchEvent after error", t)
                        }
                    }
                })
        }

        safe("HiBoardClientManager.resetTouchEvent") {
            XposedHelpers.findAndHookMethod(CLIENT_MANAGER, cl, "resetTouchEvent", MotionEvent::class.java,
                before("resetTouchEvent") { p ->
                    if (!rightMode) return@before
                    val ev = p.args[0] as? MotionEvent ?: return@before
                    p.args[0] = convert(ev)
                    if (ev.actionMasked == MotionEvent.ACTION_UP || ev.actionMasked == MotionEvent.ACTION_CANCEL) rightMode = false
                })
        }

        // 4) 新手势开始时清理残留状态
        safe("HiBoardClientManager.onInterceptTouchEvent") {
            XposedHelpers.findAndHookMethod(CLIENT_MANAGER, cl, "onInterceptTouchEvent", MotionEvent::class.java, Int::class.javaPrimitiveType,
                before("onInterceptTouchEvent") { p ->
                    if (rightMode && p.args[1] == 0) rightMode = false
                })
        }
    }

    // ------------------------------------------------------------------ 上滑打开荣耀搜索
    private var searchDownTime = -1L

    /**
     * DragLayer.handleResourceLibEnter → isScrollToHiLibrary → HiBoardUtil.scrollToHiLibrary(ev, up)。
     * 原生的所有前置判断（资源库开关、多指、文件夹、拖动、小部件区域、10dp 阈值）都已在它之前完成，
     * 所以在这里把“上滑进入资源库”替换为“打开荣耀搜索”，返回 true 让 DragLayer 拦截本次手势。
     * 我们自己的右侧入口在调用前会置 rightMode=true，不受影响。
     */
    private fun hookSwipeUpSearch() = safe("HiBoardUtil.scrollToHiLibrary(swipeUpSearch)") {
        XposedHelpers.findAndHookMethod(HIBOARD_UTIL, cl, "scrollToHiLibrary", MotionEvent::class.java, Boolean::class.javaPrimitiveType,
            before("scrollToHiLibrary") { p ->
                if (rightMode || !cfg.swipeUpSearchActive) return@before
                if (p.args[1] != true) return@before
                val ev = p.args[0] as? MotionEvent ?: return@before
                if (searchDownTime != ev.downTime) {
                    searchDownTime = ev.downTime
                    main.post { openSearch() }
                }
                p.result = true
            })
    }

    private fun openSearch() {
        try {
            val launcherCls = XposedHelpers.findClass("$P.Launcher", cl)
            val launcher = XposedHelpers.callStaticMethod(launcherCls, "getLauncher")
            val enabled = try {
                XposedHelpers.callStaticMethod(XposedHelpers.findClass("$P.unlock.LauncherPackageManager", cl), "isPackageUpdateEnable", "com.hihonor.search") == true
            } catch (t: Throwable) {
                Lg.e("isPackageUpdateEnable failed", t); true
            }
            if (launcher != null && enabled) {
                XposedHelpers.callMethod(launcher, "handleClickViewEnterSearchEvent")
                Lg.i("swipeUpSearch: handleClickViewEnterSearchEvent")
                return
            }
            Lg.i("swipeUpSearch: launcher=${launcher != null} searchEnabled=$enabled, use intent fallback")
        } catch (t: Throwable) {
            Lg.e("swipeUpSearch: handleClickViewEnterSearchEvent failed, use intent fallback", t)
        }
        try {
            val ctx = appContext ?: return
            ctx.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse("honorsearch://com.hihonor.search/home"))
                    .setPackage("com.hihonor.search")
                    .putExtra("from", "launcher_search_icon")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            )
            Lg.i("swipeUpSearch: started honorsearch://com.hihonor.search/home")
        } catch (t: Throwable) {
            Lg.e("swipeUpSearch: start activity failed", t)
        }
    }

    /** 以手势起点为原点，左移距离 → 上移距离。新事件的 raw 坐标即 x/y。 */
    private fun convert(ev: MotionEvent): MotionEvent {
        val left = max(0f, downRawX - ev.rawX)
        return MotionEvent.obtain(ev.downTime, ev.eventTime, ev.action, downRawX, downRawY - left, ev.metaState)
    }

    private fun libraryEnabled(): Boolean = try {
        XposedHelpers.callStaticMethod(XposedHelpers.findClass(RES_LIB_SETTING_UTIL, cl), "checkLibraryEnable") == true
    } catch (t: Throwable) {
        Lg.e("checkLibraryEnable failed", t)
        false
    }

    private fun folderOpen(): Boolean = try {
        val fm = XposedHelpers.callStaticMethod(XposedHelpers.findClass(FOLDER_MANAGER, cl), "getInstance")
        XposedHelpers.callMethod(fm, "hasOpenedFolder") == true || XposedHelpers.callMethod(fm, "isFolderAnimateOpening") == true
    } catch (_: Throwable) {
        false
    }
}
