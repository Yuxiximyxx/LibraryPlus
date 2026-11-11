package io.github.yuxiximyxx.libraryplus.hook

import android.util.Log
import de.robv.android.xposed.XposedBridge
import io.github.yuxiximyxx.libraryplus.Const

internal object Lg {
    private fun xlog(msg: String) {
        try {
            XposedBridge.log("[${Const.TAG}] $msg")
        } catch (_: Throwable) {
        }
    }

    /** 重要信息：同时写 logcat 与 LSPosed 日志 */
    fun i(msg: String) {
        Log.i(Const.TAG, msg)
        xlog(msg)
    }

    /** 只写 logcat（高频手势细节） */
    fun d(msg: String) {
        Log.d(Const.TAG, msg)
    }

    fun e(msg: String, t: Throwable? = null) {
        Log.e(Const.TAG, msg, t)
        xlog(msg + (t?.let { ": ${Log.getStackTraceString(it)}" } ?: ""))
    }
}

/** 每个进程的 hook 安装结果 */
internal object HookReport {
    val ok = ArrayList<String>()
    val failed = ArrayList<String>()

    fun summary(pkg: String): String =
        "LibraryPlus loaded in $pkg, hooks ok: $ok, failed: $failed"
}

/** 执行一段安装 hook 的代码，任何异常都只记录日志。 */
internal inline fun safe(name: String, block: () -> Unit) {
    try {
        block()
        synchronized(HookReport) { HookReport.ok += name }
        Lg.d("hook ok: $name")
    } catch (t: Throwable) {
        synchronized(HookReport) { HookReport.failed += name }
        Lg.e("hook failed: $name", t)
    }
}
