package org.matomocamp.companion.adapters

import android.annotation.SuppressLint
import androidx.recyclerview.widget.DiffUtil

/**
 * Creates a DiffUtil.ItemCallback instance using the provided key selector to determine
 * if items are the same and using equals() to determine if item contents are the same.
 */
inline fun <T : Any, K: Any?> createSimpleItemCallback(crossinline keySelector: (T) -> K): DiffUtil.ItemCallback<T> {
    return object : DiffUtil.ItemCallback<T>() {
        override fun areItemsTheSame(oldItem: T, newItem: T): Boolean {
            return keySelector(oldItem) == keySelector(newItem)
        }

        @SuppressLint("DiffUtilEquals")
        override fun areContentsTheSame(oldItem: T, newItem: T): Boolean {
            return oldItem == newItem
        }
    }
}