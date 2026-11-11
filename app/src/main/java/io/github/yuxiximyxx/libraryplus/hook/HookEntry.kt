package io.github.yuxiximyxx.libraryplus.hook

import android.app.Application
import android.app.Instrumentation
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import io.github.yuxiximyxx.libraryplus.Const

class HookEntry : IXposedHookLoadPackage {
    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        val pkg = lpparam.packageName
        if (pkg != Const.LAUNCHER_PKG && pkg != Const.HIBOARD_PKG) return
        // 只处理主进程（资源库 LauncherOverlayService 在 hiboard 主进程）
        if (lpparam.processName != pkg) return
        try {
            Lg.d("load $pkg (${lpparam.processName})")
            HookConfig.loadEarly()
            safe("Instrumentation.callApplicationOnCreate") {
                XposedHelpers.findAndHookMethod(
                    Instrumentation::class.java, "callApplicationOnCreate", Application::class.java,
                    after("appOnCreate") { p ->
                        val app = p.args[0] as Application
                        if (app.packageName == pkg) {
                            HookConfig.attach(app)
                            if (pkg == Const.LAUNCHER_PKG) LauncherHooks.onAppCreated(app)
                        }
                    },
                )
            }
            when (pkg) {
                Const.LAUNCHER_PKG -> LauncherHooks.install(lpparam.classLoader)
                Const.HIBOARD_PKG -> HiboardHooks.install(lpparam)
            }
            Lg.i(HookReport.summary(pkg) + " (module ${BuildInfo.VERSION})")
        } catch (t: Throwable) {
            Lg.e("handleLoadPackage failed for $pkg", t)
        }
    }
}
