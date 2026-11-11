package io.github.yuxiximyxx.libraryplus.hook

import android.util.Log
import de.robv.android.xposed.XposedBridge
import io.github.yuxiximyxx.libraryplus.Const

internal object Lg {
    fun i(msg: String) {
        Log.i(Const.TAG, msg)
    }

    fun e(msg: String, t: Throwable? = null) {
        Log.e(Const.TAG, msg, t)
        try {
            XposedBridge.log("[${Const.TAG}] $msg" + (t?.let { ": ${Log.getStackTraceString(it)}" } ?: ""))
        } catch (_: Throwable) {
        }
    }
}

/** 执行一段安装 hook 的代码，任何异常都只记录日志。 */
internal inline fun safe(name: String, block: () -> Unit) {
    try {
        block()
        Lg.i("hook ok: $name")
    } catch (t: Throwable) {
        Lg.e("hook failed: $name", t)
    }
}
