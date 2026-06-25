package com.f0x1d.logfox.core.ui.base.ext

import android.text.Editable
import android.text.TextWatcher
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner

/**
 * Observes user edits, attaching the watcher only while the fragment is resumed.
 *
 * Returns a setter that updates the text *without* notifying [callback]: programmatic updates (e.g.
 * rendering state back into the field) would otherwise look like user edits. Use it instead of
 * [TextView.setText] when pushing state into the field, and keep [TextView.setText] for genuine user
 * input only.
 */
fun TextView.doAfterTextChanged(
    fragment: Fragment,
    callback: (Editable?) -> Unit,
): (CharSequence?) -> Unit {
    val textWatcher = object : TextWatcher {
        override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) = Unit
        override fun onTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) = Unit
        override fun afterTextChanged(s: Editable) = callback(s)
    }

    var attached = false

    fragment.viewLifecycleOwner.lifecycle.addObserver(
        object : DefaultLifecycleObserver {
            override fun onResume(owner: LifecycleOwner) {
                addTextChangedListener(textWatcher)
                attached = true
            }

            override fun onPause(owner: LifecycleOwner) {
                removeTextChangedListener(textWatcher)
                attached = false
            }
        },
    )

    return { text ->
        // Detach around the update so the watcher doesn't fire for a programmatic change, then
        // restore whatever attachment state the lifecycle had set.
        removeTextChangedListener(textWatcher)
        setText(text)
        if (attached) addTextChangedListener(textWatcher)
    }
}
