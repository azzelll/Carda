package id.carda.core.model

/** Nominal proposal checks; real support still depends on camera probe and signal quality. */
data class NominalDeviceAssessment(
    val api26: Boolean,
    val arm64: Boolean,
    val fourProcessors: Boolean,
    val threeGbRam: Boolean,
    val storage250Mb: Boolean,
    val analysis720p: Boolean?,
    val observed30Fps: Boolean?,
) {
    val allPassed: Boolean get() = listOf(
        api26, arm64, fourProcessors, threeGbRam, storage250Mb, analysis720p, observed30Fps,
    ).all { it == true }
}
