package io.github.yuxiximyxx.libraryplus

import android.content.Context
import android.util.Log
import java.io.IOException
import java.util.concurrent.TimeUnit

/** “重启作用域”与生效状态汇总（模块 App 侧）。 */
object ScopeControl {
    val LABELS = mapOf(
        Const.LAUNCHER_PKG to "桌面",
        Const.HIBOARD_PKG to "资源库",
        Const.SEARCH_PKG to "荣耀搜索",
    )

    data class Summary(val ok: Int, val total: Int, val detail: String)

    fun summary(ctx: Context): Summary {
        var ok = 0
        var total = 0
        val parts = Const.SCOPE_PKGS.map { pkg ->
            val label = LABELS[pkg] ?: pkg
            val st = StatusStore.status(ctx, pkg)
            if (st == null || st.version != BuildConfig.VERSION_NAME) {
                "$label 未加载"
            } else {
                ok += st.ok.size
                total += st.ok.size + st.failed.size
                "$label ${st.ok.size}/${st.ok.size + st.failed.size}"
            }
        }
        return Summary(ok, total, parts.joinToString(" · "))
    }

    /** 未生效的 hook 名称（用于提示） */
    fun failed(ctx: Context): List<String> = Const.SCOPE_PKGS.flatMap { pkg ->
        val st = StatusStore.status(ctx, pkg)
        if (st == null || st.version != BuildConfig.VERSION_NAME) emptyList() else st.failed.map { "${LABELS[pkg]}：$it" }
    }

    /**
     * 在后台线程调用。返回给用户看的结果文字。
     * root：依次 am force-stop 荣耀搜索、资源库、桌面，再回到桌面；失败则改用免 root 方式。
     * 免 root：给三个进程发签名权限广播，已被 hook 的进程自行结束（桌面由系统自动拉起）。
     */
    fun restart(ctx: Context, useRoot: Boolean): String {
        var note = ""
        if (useRoot) {
            val cmd = listOf(Const.SEARCH_PKG, Const.HIBOARD_PKG, Const.LAUNCHER_PKG).joinToString("; ") { "am force-stop $it" } +
                "; am start -a android.intent.action.MAIN -c android.intent.category.HOME"
            try {
                val p = Runtime.getRuntime().exec(arrayOf("su", "-c", cmd))
                val done = p.waitFor(20, TimeUnit.SECONDS)
                if (done && p.exitValue() == 0) return "已通过 root 重启桌面、资源库和荣耀搜索"
                if (!done) p.destroy()
                note = "root 未授权或执行失败，已改用免 root 方式。"
            } catch (e: IOException) {
                note = "未检测到 root，已改用免 root 方式。"
            } catch (t: Throwable) {
                Log.w(Const.TAG, "su failed", t)
                note = "root 执行出错，已改用免 root 方式。"
            }
        }
        for (pkg in Const.SCOPE_PKGS) ConfigStore.sendSigned(ctx, Const.ACTION_RESTART, pkg)
        return note + "已通知桌面、资源库和荣耀搜索重启（未加载模块的进程不会重启）"
    }
}
