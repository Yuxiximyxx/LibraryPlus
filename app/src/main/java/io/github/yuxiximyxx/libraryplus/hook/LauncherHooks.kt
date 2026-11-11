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
        hookSwipeUp()
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
    private fun hookRightEntry() {
        // 1) 最后一页左滑：在 Workspace.determineScrollingStart 中抢先触发资源库
        safe("Workspace.determineScrollingStart") {
            XposedHelpers.findAndHookMethod(WORKSPACE, cl, "determineScrollingStart", MotionEvent::class.java,
                before("determineScrollingStart") { p ->
                    if (!cfg.rightEntry || rightMode) return@before
                    val ev = p.args[0] as MotionEvent
                    if (ev.actionMasked != MotionEvent.ACTION_MOVE || ev.pointerCount > 1) return@before
                    val ws = p.thisObject
                    if (XposedHelpers.getBooleanField(ws, "mIsRtl")) return@before
                    if (XposedHelpers.callMethod(ws, "isLastPage") != true) return@before
                    val state = XposedHelpers.callMethod(ws, "getState")
                    if (state?.toString() != "NORMAL_MODE") return@before
                    val dx = ev.x - XposedHelpers.getFloatField(ws, "mDownMotionX")
                    val dy = ev.y - XposedHelpers.getFloatField(ws, "mDownMotionY")
                    val slop = XposedHelpers.getIntField(ws, "mTouchSlop").coerceAtLeast(8)
                    if (dx > -slop * 1.5f || abs(dx) <= abs(dy) * 1.2f) return@before
                    if (!libraryEnabled()) return@before
                    if (folderOpen()) return@before
                    val showLib = XposedHelpers.callStaticMethod(XposedHelpers.findClass(HIBOARD_UTIL, cl), "isShowLibrary", ev, false, state)
                    if (showLib != true) return@before

                    rightMode = true
                    downRawX = ev.rawX
                    downRawY = ev.rawY
                    val ok = XposedHelpers.callStaticMethod(XposedHelpers.findClass(HIBOARD_UTIL, cl), "scrollToHiLibrary", ev, true) == true
                    if (ok) {
                        try { XposedHelpers.callMethod(ws, "customCancelLongPress") } catch (_: Throwable) {}
                        p.result = null // 不让 Workspace 开始翻页
                        Lg.i("right entry: open resource library from last page")
                    } else {
                        rightMode = false
                    }
                })
        }

        // 2) 把向左的位移换算成向上的位移，交给原有的上滑进入逻辑
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

        // 3) 新手势开始时清理残留状态
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

    // ------------------------------------------------------------------ 关闭上滑进入
    private fun hookSwipeUp() = safe("DragLayer.handleResourceLibEnter") {
        XposedHelpers.findAndHookMethod(DRAG_LAYER, cl, "handleResourceLibEnter", MotionEvent::class.java,
            before("handleResourceLibEnter") { p ->
                // 只有开启了右侧进入时才允许关闭上滑，避免资源库完全无法进入
                if (cfg.rightEntry && !cfg.keepSwipeUp) p.result = false
            })
    }
}
