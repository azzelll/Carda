package id.carda.core.ppg

/** Provisional 8-bit RGBA extraction parameters belonging to the recorded pipeline version. */
data class ExtractionThresholds(
    val minimumCoveredRed: Int = 60,
    val redDominance: Double = 1.15,
    val saturationLevel: Int = 250,
    val clipLow: Int = 2,
    val clipHigh: Int = 253,
) {
    init {
        require(minimumCoveredRed in 0..255)
        require(redDominance.isFinite() && redDominance >= 1.0)
        require(saturationLevel in 1..255)
        require(clipLow in 0..254 && clipHigh in (clipLow + 1)..255)
    }
}
