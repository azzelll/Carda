package id.carda.core.measurement

import id.carda.core.model.CaptureDuration
import id.carda.core.ppg.PpgSample

/** Whole-session samples, bounded without silently discarding the start of a valid interval. */
internal class CaptureSampleBuffer(duration: CaptureDuration) {
    // N intervals need N+1 endpoints. One further observation covers nanosecond rounding.
    // This is a memory budget, not an assertion of validated support at 120 fps.
    private val capacity = duration.seconds * 120 + 2
    val values = ArrayDeque<PpgSample>()

    /** False means a resource limit, not permission to compute from a truncated capture. */
    fun offer(sample: PpgSample): Boolean {
        if (values.size == capacity) return false
        values.addLast(sample)
        return true
    }

    fun clear() = values.clear()
}
