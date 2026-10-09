package com.moigferdsrte.touy.core.recording

/** Tracks an unloaded target and an orphaned recorder; -1 disables inactivity expiry. */
class RecordingExpiry(startTick: Long) {
    var lastTouched: Long = startTick
        private set
    var inactiveTicks: Int = 0
        private set

    fun touch(tick: Long) { lastTouched = tick }

    fun expired(targetPresent: Boolean, tick: Long, timeout: Int): Boolean {
        require(timeout == -1 || timeout > 0)
        if (timeout == -1) {
            inactiveTicks = 0
            lastTouched = tick
            return false
        }
        // Saturating before increment avoids overflow even with an Int.MAX_VALUE timeout.
        inactiveTicks = if (targetPresent) 0 else if (inactiveTicks < timeout) inactiveTicks + 1 else timeout
        if (tick < lastTouched) lastTouched = tick
        return inactiveTicks >= timeout || tick - lastTouched >= timeout
    }
}
