package org.readium.r2.navigator.input

import android.graphics.RectF
import org.readium.r2.shared.ExperimentalReadiumApi

@ExperimentalReadiumApi
public interface InputListener {
    /**
     * Called when the user tapped the content, but nothing handled the event internally (eg.
     * by following an internal link).
     */

    public fun onTap(event: TapEvent): Boolean = false
    public fun onCustomSelection(rect: RectF?, text: String, rawJson: String): Boolean = false
    /**
     * Called when the user dragged the content, but nothing handled the event internally.
     */
    public fun onDrag(event: DragEvent): Boolean = false

    /**
     * Called when the user pressed or released a key, but nothing handled the event internally.
     */
    public fun onKey(event: KeyEvent): Boolean = false
}

@OptIn(ExperimentalReadiumApi::class)
internal class CompositeInputListener : InputListener {
    private val listeners = mutableListOf<InputListener>()
    override fun onCustomSelection(rect: RectF?, text: String, rawJson: String): Boolean =
        listeners.any { it.onCustomSelection(rect, text, rawJson) }

    fun add(listener: InputListener) {
        listeners.add(listener)
    }

    fun remove(listener: InputListener) {
        listeners.remove(listener)
    }

    override fun onTap(event: TapEvent): Boolean =
        listeners.any { it.onTap(event) }

    override fun onDrag(event: DragEvent): Boolean =
        listeners.any { it.onDrag(event) }

    override fun onKey(event: KeyEvent): Boolean =
        listeners.any { it.onKey(event) }
}
