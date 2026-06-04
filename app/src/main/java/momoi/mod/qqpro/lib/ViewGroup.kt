package momoi.mod.qqpro.lib

import android.view.View
import android.view.ViewGroup
import android.widget.TextView

fun <T : View> ViewGroup.find(id: Int) = findViewById<T>(id)
fun <T : View> ViewGroup.child(id: Int) = getChildAt(0) as T

/** Replace every occurrence of [from] with [to] in all descendant TextViews' text. */
fun View.replaceTextRecursive(from: String, to: String) {
    if (this is TextView) {
        val current = text?.toString()
        if (current != null && current.contains(from)) {
            text = current.replace(from, to)
        }
    }
    if (this is ViewGroup) {
        for (i in 0 until childCount) getChildAt(i).replaceTextRecursive(from, to)
    }
}

/**
 * Run [action] on every global layout pass. Non-inline so the SAM impl lives in
 * this package, not inside a @Mixin method body in another package.
 */
fun View.onEachLayout(action: () -> Unit) {
    viewTreeObserver.addOnGlobalLayoutListener { action() }
}