package io.github.yuxiximyxx.libraryplus

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.util.Log

/**
 * 配置提供者：
 *  - get：被 hook 的进程读取配置 JSON（配置不含敏感信息）
 *  - report / putCategories：只接受作用域内三个系统应用的上报（校验调用方包名）
 */
class ConfigProvider : ContentProvider() {
    override fun onCreate(): Boolean = true

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        val ctx = context ?: return null
        return when (method) {
            Const.METHOD_GET -> Bundle().apply { putString(Const.EXTRA_CONFIG, ConfigStore.loadJson(ctx)) }
            Const.METHOD_REPORT, Const.METHOD_PUT_CATEGORIES -> {
                val caller = try { callingPackage } catch (_: Throwable) { null }
                val allowed = if (method == Const.METHOD_PUT_CATEGORIES) setOf(Const.HIBOARD_PKG) else Const.SCOPE_PKGS.toSet()
                if (caller !in allowed) {
                    Log.w(Const.TAG, "reject $method from $caller")
                    return null
                }
                val json = extras?.getString(Const.EXTRA_JSON) ?: return null
                try {
                    if (method == Const.METHOD_REPORT) StatusStore.putStatus(ctx, org.json.JSONObject(json).put("pkg", caller).toString()) else StatusStore.putCategories(ctx, json)
                } catch (t: Throwable) {
                    Log.w(Const.TAG, "$method failed", t)
                }
                Bundle()
            }
            else -> null
        }
    }

    override fun query(uri: Uri, p: Array<out String>?, s: String?, a: Array<out String>?, o: String?): Cursor? = null
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, s: String?, a: Array<out String>?): Int = 0
    override fun update(uri: Uri, v: ContentValues?, s: String?, a: Array<out String>?): Int = 0
}
