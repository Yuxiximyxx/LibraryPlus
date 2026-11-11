package io.github.yuxiximyxx.libraryplus

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/** 被 hook 进程通过 ConfigProvider 上报的数据：每个进程的 hook 生效情况、资源库真实分类。 */
object StatusStore {
    data class ProcStatus(val pkg: String, val version: String, val ok: List<String>, val failed: List<String>, val time: Long)

    fun prefs(ctx: Context): SharedPreferences =
        ctx.applicationContext.getSharedPreferences(Const.PREFS_STATUS, Context.MODE_PRIVATE)

    fun putStatus(ctx: Context, json: String) {
        val o = JSONObject(json)
        val pkg = o.getString("pkg")
        prefs(ctx).edit().putString(Const.KEY_STATUS_PREFIX + pkg, json).apply()
    }

    fun status(ctx: Context, pkg: String): ProcStatus? = try {
        prefs(ctx).getString(Const.KEY_STATUS_PREFIX + pkg, null)?.let { s ->
            val o = JSONObject(s)
            fun list(a: JSONArray?) = if (a == null) emptyList() else (0 until a.length()).map { a.optString(it) }
            ProcStatus(o.getString("pkg"), o.optString("version"), list(o.optJSONArray("ok")), list(o.optJSONArray("failed")), o.optLong("time"))
        }
    } catch (_: Throwable) {
        null
    }

    /** hiboard 上报的 {pkg: 原始分类 id}，与已有数据合并 */
    fun putCategories(ctx: Context, json: String) {
        val incoming = JSONObject(json)
        val p = prefs(ctx)
        val merged = try { JSONObject(p.getString(Const.KEY_CATEGORIES, "{}") ?: "{}") } catch (_: Throwable) { JSONObject() }
        incoming.keys().forEach { k ->
            val v = incoming.optInt(k, -1)
            if (v in Const.FOLDER_IDS) merged.put(k, v)
        }
        p.edit().putString(Const.KEY_CATEGORIES, merged.toString()).putLong(Const.KEY_CATEGORIES_TS, System.currentTimeMillis()).apply()
    }

    fun categories(ctx: Context): Map<String, Int> = try {
        val o = JSONObject(prefs(ctx).getString(Const.KEY_CATEGORIES, "{}") ?: "{}")
        val m = HashMap<String, Int>()
        o.keys().forEach { k -> m[k] = o.optInt(k, -1) }
        m
    } catch (_: Throwable) {
        emptyMap()
    }

    fun categoriesTime(ctx: Context): Long = prefs(ctx).getLong(Const.KEY_CATEGORIES_TS, 0L)
}
