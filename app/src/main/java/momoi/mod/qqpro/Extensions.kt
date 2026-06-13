package momoi.mod.qqpro

import android.view.View
import android.view.ViewGroup
import android.view.ViewParent
import android.widget.LinearLayout
import androidx.core.view.forEach
import momoi.mod.qqpro.lib.FILL
import momoi.mod.qqpro.lib.create
import momoi.mod.qqpro.lib.vertical
import java.io.File
import java.lang.reflect.Method

fun View.asGroupOrNull() = this as? ViewGroup
fun ViewParent.asGroup() = this as ViewGroup
fun View.asGroup() = this as ViewGroup
fun <E> List<E>.join(block: (E)->CharSequence) = joinToString("", transform = block)
fun ViewGroup.forEachAll(block: (View) -> Unit) {
    forEach { child ->
        block(child)
        child.asGroupOrNull()?.forEachAll(block)
    }
}
fun ViewGroup.findAll(block: (View) -> Boolean): View? {
    forEach { child ->
        if (block(child)) {
            return@findAll child
        } else child.asGroupOrNull()?.findAll(block)?.let {
            return@findAll it
        }
    }
    return null
}
fun ViewGroup.anyAll(block: (View) -> Boolean) = findAll(block) != null

fun String.removeBefore(key: String) = split(key, limit = 2)[1]
fun String.removeAfter(key: String) = split(key, limit = 2)[0]

fun View.warp(): LinearLayout {
    val lp = this.layoutParams
    val warp = create<LinearLayout>(context).vertical()
    parent.asGroup().let {
        it.removeView(this)
        warp.layoutParams = lp
        this.layoutParams = LinearLayout.LayoutParams(FILL, 0, 1f)
        warp.addView(this)
        it.addView(warp)
    }
    return warp
}

fun String?.emptyUse(other: String) = if (isNullOrEmpty()) other else this

fun File.child(path: String) = File(this, path)

/**
 * Best-effort cache directory. On some ROMs (e.g. XTC watches) externalCacheDir can be
 * null when external storage isn't mounted, so fall back to internal cache then filesDir.
 */
val android.content.Context.safeCacheDir: File?
    get() = externalCacheDir ?: cacheDir ?: filesDir

fun <T> Class<T>.findMethod(name: String, args: List<Class<Any>>? = null): Method {
    return try {
        if (args == null) getDeclaredMethod(name)
        else getDeclaredMethod(name, *args.toTypedArray())
    } catch (e: Exception) {
        superclass.findMethod(name, args)
    }
}