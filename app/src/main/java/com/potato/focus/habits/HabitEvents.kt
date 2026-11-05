package com.potato.focus.habits

import kotlinx.coroutines.flow.MutableStateFlow

object HabitEvents {
    val version = MutableStateFlow(0L)

    fun notifyChanged() {
        version.value = System.nanoTime()
    }
}
