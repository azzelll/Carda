package id.carda.core.measurement

/** Keep interim analysis bounded, but evaluate the full window on its first eligible frame. */
internal fun shouldEvaluateCapture(
    timestampNanos: Long,
    previousEvaluationNanos: Long?,
    elapsedCaptureNanos: Long,
    targetSeconds: Int,
): Boolean {
    val intervalNanos = 1_000_000_000L
    return elapsedCaptureNanos >= targetSeconds * intervalNanos ||
        previousEvaluationNanos == null ||
        timestampNanos - previousEvaluationNanos >= intervalNanos
}
