package id.carda.core.camera

/** Suppresses callbacks and delayed cleanup from a previous capture session. */
internal class CameraSessionEpoch {
    private var sequence = 0L
    private var current: Long? = null

    @Synchronized fun begin(): Long? {
        if (current != null) return null
        sequence += 1
        return sequence.also { current = it }
    }

    @Synchronized fun isCurrent(token: Long): Boolean = current == token

    @Synchronized fun stop(token: Long): Boolean {
        if (current != token) return false
        current = null
        return true
    }

    @Synchronized fun stopCurrent(): Long? = current.also { current = null }
}
