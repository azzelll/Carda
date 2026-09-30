package id.carda.core.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import id.carda.core.model.DeviceProfile
import id.carda.core.model.MeasurementRepository
import id.carda.core.model.MeasurementState
import id.carda.core.model.MeasurementActivity
import id.carda.core.model.MeasurementSummary
import id.carda.core.model.MetricKind
import id.carda.core.model.MetricResult
import id.carda.core.model.MetricUnavailableReason
import id.carda.core.model.QualityIssue
import id.carda.core.model.QualityReport
import id.carda.core.model.StoredMeasurement
import id.carda.core.model.SupportClassification
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

@Entity(tableName = "measurement_summaries", indices = [Index(value = ["accountId", "measuredAtEpochMillis"])])
data class MeasurementEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val accountId: String,
    val measuredAtEpochMillis: Long,
    val validDurationMillis: Long,
    val qualityScore: Double,
    val qualityIssues: String,
    val evaluatedSamples: Int,
    val pipelineVersion: String,
    val manufacturer: String,
    val deviceModel: String,
    val androidApiLevel: Int,
    val abi: String,
    val appVersion: String,
    val hasRearCamera: Boolean,
    val hasTorch: Boolean,
    val analysisWidth: Int?,
    val analysisHeight: Int?,
    val observedFramesPerSecond: Double?,
    val exposureDescription: String?,
    val flashDescription: String?,
    val rejectionReasons: String,
    val lastQualityScore: Double?,
    val supportClassification: String,
    val metricStatuses: String,
    val modelVersion: String?,
    @ColumnInfo(defaultValue = "'UNSPECIFIED'")
    val activity: String = MeasurementActivity.UNSPECIFIED.name,
)

@Dao
interface MeasurementDao {
    @Insert suspend fun insert(entity: MeasurementEntity): Long

    @Query("SELECT * FROM measurement_summaries WHERE accountId = :accountId ORDER BY measuredAtEpochMillis DESC, id DESC")
    fun observe(accountId: String): Flow<List<MeasurementEntity>>

    @Query("SELECT * FROM measurement_summaries WHERE accountId = :accountId AND id = :id LIMIT 1")
    suspend fun find(accountId: String, id: Long): MeasurementEntity?

    @Query("DELETE FROM measurement_summaries WHERE accountId = :accountId")
    suspend fun deleteAll(accountId: String): Int
}

@Database(entities = [MeasurementEntity::class], version = 2, exportSchema = true)
abstract class CardaDatabase : RoomDatabase() {
    abstract fun measurements(): MeasurementDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE measurement_summaries ADD COLUMN activity TEXT NOT NULL DEFAULT 'UNSPECIFIED'")
            }
        }

        fun open(context: Context): CardaDatabase = Room.databaseBuilder(
            context.applicationContext, CardaDatabase::class.java, "carda_measurements.db",
        ).addMigrations(MIGRATION_1_2).build()
    }
}

class RoomMeasurementRepository(private val dao: MeasurementDao) : MeasurementRepository {
    override suspend fun save(accountId: String, summary: MeasurementSummary): Long {
        requireValidAccount(accountId)
        require(summary.quality.passed && summary.status == MeasurementState.COMPLETE)
        require(summary.metrics.values.any { it is MetricResult.Available<*> })
        return dao.insert(summary.toEntity(accountId))
    }

    override fun observeHistory(accountId: String): Flow<List<StoredMeasurement>> {
        requireValidAccount(accountId)
        return dao.observe(accountId).map { rows -> rows.map { it.toStored() } }
    }

    override suspend fun find(accountId: String, id: Long): StoredMeasurement? {
        requireValidAccount(accountId)
        return dao.find(accountId, id)?.toStored()
    }

    override suspend fun deleteAll(accountId: String): Int {
        requireValidAccount(accountId)
        return dao.deleteAll(accountId)
    }
}

private fun requireValidAccount(accountId: String) {
    require(UUID.fromString(accountId).toString() == accountId.lowercase())
}

private fun MeasurementSummary.toEntity(accountId: String) = MeasurementEntity(
    accountId = accountId,
    measuredAtEpochMillis = measuredAtEpochMillis,
    validDurationMillis = validDurationMillis,
    qualityScore = quality.score,
    qualityIssues = quality.issues.joinToString(",") { it.name },
    evaluatedSamples = quality.evaluatedSamples,
    pipelineVersion = quality.pipelineVersion,
    manufacturer = deviceProfile.manufacturer,
    deviceModel = deviceProfile.model,
    androidApiLevel = deviceProfile.androidApiLevel,
    abi = deviceProfile.abi,
    appVersion = deviceProfile.appVersion,
    hasRearCamera = deviceProfile.hasRearCamera,
    hasTorch = deviceProfile.hasTorch,
    analysisWidth = deviceProfile.analysisWidth,
    analysisHeight = deviceProfile.analysisHeight,
    observedFramesPerSecond = deviceProfile.observedFramesPerSecond,
    exposureDescription = deviceProfile.exposureDescription,
    flashDescription = deviceProfile.flashDescription,
    rejectionReasons = deviceProfile.rejectionReasons.joinToString(",") { it.name },
    lastQualityScore = deviceProfile.lastQualityScore,
    supportClassification = deviceProfile.supportClassification.name,
    metricStatuses = MetricStatusCodec.encode(metrics),
    modelVersion = modelVersion,
    activity = activity.name,
)

private fun MeasurementEntity.toStored(): StoredMeasurement {
    val quality = QualityReport(
        passed = true,
        score = qualityScore,
        issues = decodeIssues(qualityIssues),
        evaluatedSamples = evaluatedSamples,
        validDurationMillis = validDurationMillis,
        pipelineVersion = pipelineVersion,
    )
    val profile = DeviceProfile(
        manufacturer = manufacturer,
        model = deviceModel,
        androidApiLevel = androidApiLevel,
        abi = abi,
        appVersion = appVersion,
        pipelineVersion = pipelineVersion,
        hasRearCamera = hasRearCamera,
        hasTorch = hasTorch,
        analysisWidth = analysisWidth,
        analysisHeight = analysisHeight,
        observedFramesPerSecond = observedFramesPerSecond,
        exposureDescription = exposureDescription,
        flashDescription = flashDescription,
        rejectionReasons = decodeIssues(rejectionReasons),
        lastQualityScore = lastQualityScore,
        supportClassification = SupportClassification.valueOf(supportClassification),
    )
    return StoredMeasurement(id, MeasurementSummary(
        measuredAtEpochMillis = measuredAtEpochMillis,
        validDurationMillis = validDurationMillis,
        quality = quality,
        deviceProfile = profile,
        metrics = MetricStatusCodec.decode(metricStatuses),
        modelVersion = modelVersion,
        activity = MeasurementActivity.valueOf(activity),
    ))
}

private fun decodeIssues(value: String): Set<QualityIssue> =
    if (value.isEmpty()) emptySet() else value.split(',').map(QualityIssue::valueOf).toSet()

/** Compact, version-independent enum/name encoding; metric values never include raw PPG. */
object MetricStatusCodec {
    fun encode(metrics: Map<MetricKind, MetricResult<Double>>): String = metrics.entries
        .sortedBy { it.key.name }
        .joinToString(";") { (kind, result) ->
            val encoded = when (result) {
                is MetricResult.Available -> "V:${result.value}"
                is MetricResult.Unavailable -> "U:${result.reason.name}"
            }
            "${kind.name}=$encoded"
        }

    fun decode(value: String): Map<MetricKind, MetricResult<Double>> =
        if (value.isEmpty()) emptyMap() else value.split(';').associate { part ->
            val (kind, content) = part.split('=', limit = 2)
            val (status, payload) = content.split(':', limit = 2)
            MetricKind.valueOf(kind) to when (status) {
                "V" -> MetricResult.Available(payload.toDouble())
                "U" -> MetricResult.Unavailable(MetricUnavailableReason.valueOf(payload))
                else -> error("Unknown metric status")
            }
        }
}
