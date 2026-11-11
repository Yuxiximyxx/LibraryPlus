package io.github.yuxiximyxx.libraryplus

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.util.Log

/** 模块 App 侧：保存配置、通知被 hook 的进程。 */
object ConfigStore {
    private val handler = Handler(Looper.getMainLooper())
    private var pending: Runnable? = null

    /** LSPosed new-xshared 是否可用（模块已激活时 MODE_WORLD_READABLE 不会抛异常） */
    @Volatile
    var xsharedAvailable: Boolean = false
        private set

    @SuppressLint("WorldReadableFiles")
    private fun sharedPrefs(ctx: Context): SharedPreferences? = try {
        @Suppress("DEPRECATION")
        ctx.getSharedPreferences(Const.PREFS_SHARED, Context.MODE_WORLD_READABLE).also { xsharedAvailable = true }
    } catch (_: SecurityException) {
        xsharedAvailable = false
        null
    }

    private fun privatePrefs(ctx: Context): SharedPreferences =
        ctx.getSharedPreferences(Const.PREFS_PRIVATE, Context.MODE_PRIVATE)

    fun load(ctx: Context): LpConfig {
        sharedPrefs(ctx) // 探测 xshared 状态
        return LpConfig.fromJson(privatePrefs(ctx).getString(Const.KEY_CONFIG, null)) ?: LpConfig()
    }

    fun loadJson(ctx: Context): String? = privatePrefs(ctx).getString(Const.KEY_CONFIG, null)

    fun save(ctx: Context, cfg: LpConfig): LpConfig {
        val app = ctx.applicationContext
        val stamped = cfg.copy(ts = System.currentTimeMillis())
        val json = stamped.toJson()
        privatePrefs(app).edit().putString(Const.KEY_CONFIG, json).apply()
        try {
            sharedPrefs(app)?.edit()?.putString(Const.KEY_CONFIG, json)?.commit()
        } catch (t: Throwable) {
            Log.w(Const.TAG, "write xshared prefs failed", t)
        }
        // 去抖：连续修改只在停止 600ms 后广播一次，避免桌面频繁刷新资源库
        pending?.let(handler::removeCallbacks)
        pending = Runnable { broadcast(app, json) }.also { handler.postDelayed(it, 600) }
        return stamped
    }

    fun flush(ctx: Context) {
        val r = pending ?: return
        handler.removeCallbacks(r)
        pending = null
        r.run()
    }

    private fun broadcast(ctx: Context, json: String) {
        for (pkg in Const.SCOPE_PKGS) {
            try {
                ctx.sendBroadcast(
                    Intent(Const.ACTION_CHANGED)
                        .setPackage(pkg)
                        .addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
                        .putExtra(Const.EXTRA_CONFIG, json),
                )
            } catch (t: Throwable) {
                Log.w(Const.TAG, "broadcast to $pkg failed", t)
            }
        }
        pending = null
    }

    /** 发送一个只有本模块能发出的广播（接收方注册时要求 PERMISSION）。 */
    fun sendSigned(ctx: Context, action: String, pkg: String) {
        try {
            ctx.sendBroadcast(Intent(action).setPackage(pkg).addFlags(Intent.FLAG_RECEIVER_FOREGROUND))
        } catch (t: Throwable) {
            Log.w(Const.TAG, "broadcast $action to $pkg failed", t)
        }
    }
}
