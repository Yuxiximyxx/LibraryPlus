package io.github.yuxiximyxx.libraryplus.hook

import de.robv.android.xposed.XC_MethodHook

/** 回调内部异常全部吞掉，保证不会让桌面 / 资源库崩溃。 */
internal fun before(name: String, block: (XC_MethodHook.MethodHookParam) -> Unit) = object : XC_MethodHook() {
    override fun beforeHookedMethod(param: MethodHookParam) {
        try {
            block(param)
        } catch (t: Throwable) {
            Lg.e("before[$name] error", t)
        }
    }
}

internal fun after(name: String, block: (XC_MethodHook.MethodHookParam) -> Unit) = object : XC_MethodHook() {
    override fun afterHookedMethod(param: MethodHookParam) {
        try {
            block(param)
        } catch (t: Throwable) {
            Lg.e("after[$name] error", t)
        }
    }
}
