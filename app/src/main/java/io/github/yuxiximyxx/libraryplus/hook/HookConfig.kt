package io.github.yuxiximyxx.libraryplus.hook

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Process
import org.json.JSONArray
import org.json.JSONObject
import de.robv.android.xposed.XSharedPreferences
import io.github.yuxiximyxx.libraryplus.Const
import io.github.yuxiximyxx.libraryplus.LpConfig

/**
 * 被 hook 进程内的配置。来源（取 ts 最新者）：
 *  1. LSPosed new-xshared（XSharedPreferences，无需跨进程启动模块）
 *  2. 模块的 ContentProvider（call "get"）
 *  3. 模块发出的 CONFIG_CHANGED 广播（直接携带 JSON）
 */
internal object HookConfig {
    @Volatile
    var cfg: LpConfig = LpConfig()
        private set

    private var registered = false
    private val listeners = ArrayList<(LpConfig) -> Unit>()

    @Volatile
    var appContext: Context? = null
        private set

    /** 模块界面点了“刷新分类”：hiboard 重新上报，桌面让资源库重新拉取 */
    @Volatile
    var onRequestCategories: (() -> Unit)? = null

    /** 重启作用域时自杀前的延迟（桌面最后退出，避免资源库在桌面重连时被杀） */
    @Volatile
    var restartDelayMs: Long = 0L

    fun addListener(l: (LpConfig) -> Unit) {
        synchronized(listeners) { listeners += l }
    }

    @Synchronized
    private fun offer(candidate: LpConfig?, source: String, force: Boolean = false): Boolean {
        if (candidate == null) return false
        if (!force && candidate.ts < cfg.ts) return false
        cfg = candidate
        Lg.i("config loaded from $source: hidden=${candidate.hidden.size} names=${candidate.folderNames.size} mapping=${candidate.mapping.size} right=${candidate.rightEntry}")
        return true
    }

    fun loadEarly() {
        try {
            val xsp = XSharedPreferences(Const.SELF_PKG, Const.PREFS_SHARED)
            if (xsp.file.canRead()) {
                xsp.reload()
                offer(LpConfig.fromJson(xsp.getString(Const.KEY_CONFIG, null)), "xshared")
            } else {
                Lg.i("xshared prefs not readable: ${xsp.file}")
            }
        } catch (t: Throwable) {
            Lg.e("xshared load failed", t)
        }
    }

    private fun loadFromProvider(ctx: Context) {
        try {
            val b = ctx.contentResolver.call(Uri.parse("content://${Const.AUTHORITY}"), Const.METHOD_GET, null, null)
            if (offer(LpConfig.fromJson(b?.getString(Const.EXTRA_CONFIG)), "provider")) notifyListeners()
        } catch (t: Throwable) {
            Lg.e("provider load failed (module app not reachable?)", t)
        }
    }

    private fun notifyListeners() {
        val ls = synchronized(listeners) { listeners.toList() }
        ls.forEach {
            try {
                it(cfg)
            } catch (t: Throwable) {
                Lg.e("config listener error", t)
            }
        }
    }

    /** 在 Application.onCreate 之后调用：读 provider、注册变更广播。 */
    fun attach(ctx: Context) {
        synchronized(this) {
            if (registered) return
            registered = true
        }
        appContext = ctx
        try {
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(c: Context, intent: Intent) {
                    try {
                        if (offer(LpConfig.fromJson(intent.getStringExtra(Const.EXTRA_CONFIG)), "broadcast", force = true)) {
                            notifyListeners()
                        }
                    } catch (t: Throwable) {
                        Lg.e("onReceive error", t)
                    }
                }
            }
            ctx.registerReceiver(receiver, IntentFilter(Const.ACTION_CHANGED), Const.PERMISSION, null, Context.RECEIVER_EXPORTED)
        } catch (t: Throwable) {
            Lg.e("registerReceiver failed", t)
        }
        try {
            val ctl = object : BroadcastReceiver() {
                override fun onReceive(c: Context, intent: Intent) {
                    try {
                        when (intent.action) {
                            Const.ACTION_RESTART -> {
                                Lg.i("restart scope requested, killing ${c.packageName} in ${restartDelayMs}ms")
                                Handler(Looper.getMainLooper()).postDelayed({ Process.killProcess(Process.myPid()) }, restartDelayMs)
                            }
                            Const.ACTION_REQUEST_CATEGORIES -> onRequestCategories?.invoke()
                        }
                    } catch (t: Throwable) {
                        Lg.e("control receiver error", t)
                    }
                }
            }
            val filter = IntentFilter().apply {
                addAction(Const.ACTION_RESTART)
                addAction(Const.ACTION_REQUEST_CATEGORIES)
            }
            ctx.registerReceiver(ctl, filter, Const.PERMISSION, null, Context.RECEIVER_EXPORTED)
        } catch (t: Throwable) {
            Lg.e("register control receiver failed", t)
        }
        Thread({ loadFromProvider(ctx) }, "LibraryPlus-cfg").start()
        // 等延迟安装的 hook（DexKit 等）完成后再上报生效情况
        Handler(Looper.getMainLooper()).postDelayed({ Thread({ reportStatus(ctx) }, "LibraryPlus-report").start() }, 4000)
    }

    fun callProvider(ctx: Context, method: String, json: String) {
        try {
            ctx.contentResolver.call(Uri.parse("content://${Const.AUTHORITY}"), method, null, Bundle().apply { putString(Const.EXTRA_JSON, json) })
        } catch (t: Throwable) {
            Lg.e("provider $method failed (module app not reachable?)", t)
        }
    }

    private fun reportStatus(ctx: Context) {
        val (ok, failed) = synchronized(HookReport) { HookReport.ok.toList() to HookReport.failed.toList() }
        val json = JSONObject().apply {
            put("pkg", ctx.packageName)
            put("version", BuildInfo.VERSION)
            put("ok", JSONArray(ok))
            put("failed", JSONArray(failed))
            put("time", System.currentTimeMillis())
        }.toString()
        callProvider(ctx, Const.METHOD_REPORT, json)
        Lg.i(HookReport.summary(ctx.packageName) + " (module ${BuildInfo.VERSION}, reported)")
    }
}
