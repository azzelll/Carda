package id.carda.core.camera

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.StatFs
import id.carda.core.model.NominalDeviceAssessment

/** Proposal-era nominal requirements, separate from actual camera and SQI acceptance. */
data class DeviceHardwareSnapshot(
    val apiLevel: Int,
    val abis: List<String>,
    val availableProcessors: Int,
    val totalRamBytes: Long,
    val freeStorageBytes: Long,
)

fun assessNominalDevice(
    hardware: DeviceHardwareSnapshot,
    width: Int?,
    height: Int?,
    observedFps: Double?,
): NominalDeviceAssessment = NominalDeviceAssessment(
    api26 = hardware.apiLevel >= 26,
    arm64 = hardware.abis.any { it == "arm64-v8a" },
    fourProcessors = hardware.availableProcessors >= 4,
    threeGbRam = hardware.totalRamBytes >= 3_000_000_000L,
    storage250Mb = hardware.freeStorageBytes >= 250_000_000L,
    analysis720p = if (width == null || height == null) null
        else maxOf(width, height) >= 1280 && minOf(width, height) >= 720,
    observed30Fps = observedFps?.let { it >= 30.0 },
)

fun readDeviceHardware(context: Context): DeviceHardwareSnapshot {
    val memory = ActivityManager.MemoryInfo()
    context.getSystemService(ActivityManager::class.java).getMemoryInfo(memory)
    val storage = StatFs(context.filesDir.absolutePath)
    return DeviceHardwareSnapshot(
        apiLevel = Build.VERSION.SDK_INT,
        abis = Build.SUPPORTED_ABIS.toList(),
        availableProcessors = Runtime.getRuntime().availableProcessors(),
        totalRamBytes = memory.totalMem,
        freeStorageBytes = storage.availableBytes,
    )
}
