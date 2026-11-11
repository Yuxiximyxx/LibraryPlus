package io.github.yuxiximyxx.libraryplus

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Bundle

/**
 * 只读配置提供者：被 hook 的桌面 / 资源库进程通过 call("get") 读取 JSON。
 * 配置不含敏感信息，只开放读取。
 */
class ConfigProvider : ContentProvider() {
    override fun onCreate(): Boolean = true

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        if (method != Const.METHOD_GET) return null
        val ctx = context ?: return null
        return Bundle().apply { putString(Const.EXTRA_CONFIG, ConfigStore.loadJson(ctx)) }
    }

    override fun query(uri: Uri, p: Array<out String>?, s: String?, a: Array<out String>?, o: String?): Cursor? = null
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, s: String?, a: Array<out String>?): Int = 0
    override fun update(uri: Uri, v: ContentValues?, s: String?, a: Array<out String>?): Int = 0
}
