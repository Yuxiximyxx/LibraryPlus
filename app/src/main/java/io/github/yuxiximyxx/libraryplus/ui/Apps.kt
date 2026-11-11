package io.github.yuxiximyxx.libraryplus.ui

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.Collator
import java.util.Locale

data class AppEntry(val pkg: String, val label: String, val system: Boolean)

object AppRepo {
    @Volatile
    private var cached: List<AppEntry>? = null
    private val icons = LruCache<String, ImageBitmap>(300)

    /** 所有带桌面图标的应用（与资源库收录范围一致）。 */
    suspend fun load(ctx: Context): List<AppEntry> = cached ?: withContext(Dispatchers.IO) {
        val pm = ctx.packageManager
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val collator = Collator.getInstance(Locale.CHINA)
        pm.queryIntentActivities(launcher, 0)
            .map { it.activityInfo.applicationInfo }
            .distinctBy { it.packageName }
            .filter { it.packageName != ctx.packageName }
            .map { ai ->
                AppEntry(
                    pkg = ai.packageName,
                    label = ai.loadLabel(pm).toString(),
                    system = (ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0,
                )
            }
            .sortedWith { a, b -> collator.compare(a.label, b.label) }
            .also { cached = it }
    }

    suspend fun icon(ctx: Context, pkg: String): ImageBitmap? {
        icons.get(pkg)?.let { return it }
        return withContext(Dispatchers.IO) {
            try {
                val d = ctx.packageManager.getApplicationIcon(pkg)
                val size = (40 * ctx.resources.displayMetrics.density).toInt().coerceAtLeast(1)
                val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
                d.setBounds(0, 0, size, size)
                d.draw(Canvas(bmp))
                bmp.asImageBitmap().also { icons.put(pkg, it) }
            } catch (_: Throwable) {
                null
            }
        }
    }
}

@Composable
fun AppIcon(pkg: String, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    var bmp by remember(pkg) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(pkg) { bmp = AppRepo.icon(ctx, pkg) }
    Box(modifier.size(40.dp)) {
        bmp?.let { Image(bitmap = it, contentDescription = null, modifier = Modifier.size(40.dp)) }
    }
}

fun List<AppEntry>.search(q: String): List<AppEntry> {
    val k = q.trim()
    if (k.isEmpty()) return this
    return filter { it.label.contains(k, ignoreCase = true) || it.pkg.contains(k, ignoreCase = true) }
}
