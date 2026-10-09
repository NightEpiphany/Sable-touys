package com.moigferdsrte.touy.core.recording

/** Multiple physics substeps consume the same frame, then one world tick advances it. */
class ReplayCursor(val frameCount: Int) {
    init { require(frameCount > 0) }
    var frame: Int = 0
        private set
    var finished: Boolean = false
        private set
    private var applied = false

    fun markApplied() {
        if (!finished) applied = true
    }

    fun advanceAfterPhysics(): Boolean {
        if (!applied || finished) return finished
        applied = false
        if (frame + 1 == frameCount) finished = true else frame++
        return finished
    }
}
