package io.github.yuxiximyxx.libraryplus.hook

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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
        HookConfig.addListener { refreshLibrary() }
    }

    fun onAppCreated(ctx: Context) {
        appContext = ctx
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
    private fun lastPageState(ws: Any?): Pair<Any?, String?> {
        if (ws == null) return null to "no workspace"
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

    /**
     * 尝试从最后一页打开资源库。dx/dy 为相对按下点的位移（View 坐标）。
     * @return true 表示已经交给资源库（调用方应拦截本次事件）
     */
    private fun tryOpen(source: String, ev: MotionEvent, dx: Float, dy: Float, ws: Any?, dragLayer: Any?, ctx: Context?): Boolean {
        if (attemptedDownTime == ev.downTime) return false
        val threshold = max(dp(ctx, 10f), XposedHelpers.getIntField(ws ?: return false, "mTouchSlop").toFloat())
        if (dx > -threshold || abs(dx) <= abs(dy) * 1.2f) return false
        attemptedDownTime = ev.downTime
        val (state, why) = lastPageState(ws)
        if (state == null) {
            Lg.d("rightEntry[$source]: skip ($why)")
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
                    // 可选：关闭原来的上滑进入（仅在开启右侧进入时生效，避免资源库无法进入）
                    if (!c.keepSwipeUp) p.result = false
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
                    val (state, _) = lastPageState(ws)
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
