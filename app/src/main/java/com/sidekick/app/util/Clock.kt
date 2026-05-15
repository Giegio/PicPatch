package com.sidekick.app.util

interface Clock {
    fun currentTimeMillis(): Long
}

class SystemClock : Clock {
    override fun currentTimeMillis(): Long = System.currentTimeMillis()
}
