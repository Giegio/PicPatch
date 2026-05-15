package com.sidekick.app.util

class FakeClock(var timeMs: Long = 1000000L) : Clock {
    override fun currentTimeMillis(): Long = timeMs
    
    fun advanceBy(ms: Long) {
        timeMs += ms
    }
}
