package id.carda.core.data

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import id.carda.core.model.DeviceProfile
import id.carda.core.model.MeasurementSummary
import id.carda.core.model.MeasurementActivity
import id.carda.core.model.MetricKind
import id.carda.core.model.MetricResult
import id.carda.core.model.QualityReport
import id.carda.core.model.SupportClassification
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MeasurementDatabaseInstrumentedTest {
    private val database = Room.inMemoryDatabaseBuilder(
        ApplicationProvider.getApplicationContext(), CardaDatabase::class.java,
    ).build()
    private val repository = RoomMeasurementRepository(database.measurements())
    private val accountA = "ec87a9d1-6035-443b-b14a-ad36d879c9d8"
    private val accountB = "43718466-854c-4c97-b2c8-50dfb369fa2a"

    @After fun close() = database.close()

    @Test fun roomPersistsOnlyWithinAccountAndDeletesOnlySelectedAccount() = runBlocking {
        val idA = repository.save(accountA, summary(72.0))
        val idB = repository.save(accountB, summary(81.0))
        assertEquals(1, repository.observeHistory(accountA).first().size)
        assertNull(repository.find(accountB, idA))
        assertEquals(81.0, ((repository.find(accountB, idB)!!.summary.metrics[MetricKind.HEART_RATE_BPM])
            as MetricResult.Available).value, 0.0)
        assertEquals(1, repository.deleteAll(accountA))
        assertNull(repository.find(accountA, idA))
        assertEquals(1, repository.observeHistory(accountB).first().size)
    }

    @Test fun summarySurvivesDatabaseReopen() = runBlocking {
        val context: android.content.Context = ApplicationProvider.getApplicationContext()
        val name = "carda-restart-test.db"
        context.deleteDatabase(name)
        val first = Room.databaseBuilder(context, CardaDatabase::class.java, name).build()
        try {
            RoomMeasurementRepository(first.measurements()).save(accountA, summary(73.0))
        } finally { first.close() }
        val reopened = Room.databaseBuilder(context, CardaDatabase::class.java, name).build()
        try {
            val records = RoomMeasurementRepository(reopened.measurements()).observeHistory(accountA).first()
            assertEquals(1, records.size)
            assertEquals(73.0, ((records.single().summary.metrics[MetricKind.HEART_RATE_BPM])
                as MetricResult.Available).value, 0.0)
        } finally {
            reopened.close()
            context.deleteDatabase(name)
        }
    }

    @Test fun versionOneHistoryMigratesWithoutLosingSummary() = runBlocking {
        val context: android.content.Context = ApplicationProvider.getApplicationContext()
        val name = "carda-v1-migration-test.db"
        context.deleteDatabase(name)
        val oldDatabase = SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(name), null)
        try {
            oldDatabase.execSQL(V1_CREATE_TABLE)
            oldDatabase.execSQL("CREATE INDEX IF NOT EXISTS `index_measurement_summaries_accountId_measuredAtEpochMillis` ON `measurement_summaries` (`accountId`, `measuredAtEpochMillis`)")
            oldDatabase.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
            oldDatabase.execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, 'cb7ec001786c9659e930a5f59ecd8aca')")
            val row = ContentValues().apply {
                put("accountId", accountA)
                put("measuredAtEpochMillis", 1_790_000_000_000L)
                put("validDurationMillis", 30_000L)
                put("qualityScore", 1.0)
                put("qualityIssues", "")
                put("evaluatedSamples", 900)
                put("pipelineVersion", "ppg-0.1")
                put("manufacturer", "Synthetic")
                put("deviceModel", "Fixture")
                put("androidApiLevel", 36)
                put("abi", "arm64-v8a")
                put("appVersion", "0.1.0")
                put("hasRearCamera", 1)
                put("hasTorch", 1)
                put("analysisWidth", 1280)
                put("analysisHeight", 720)
                put("observedFramesPerSecond", 30.0)
                put("flashDescription", "torch on")
                put("rejectionReasons", "")
                put("lastQualityScore", 1.0)
                put("supportClassification", "COMPATIBLE")
                put("metricStatuses", "HEART_RATE_BPM=V:72.0")
            }
            oldDatabase.insertOrThrow("measurement_summaries", null, row)
            oldDatabase.version = 1
        } finally { oldDatabase.close() }

        val migrated = Room.databaseBuilder(context, CardaDatabase::class.java, name)
            .addMigrations(CardaDatabase.MIGRATION_1_2).build()
        try {
            val oldSummary = RoomMeasurementRepository(migrated.measurements())
                .observeHistory(accountA).first().single().summary
            assertEquals(MeasurementActivity.UNSPECIFIED, oldSummary.activity)
            assertEquals(72.0, (oldSummary.metrics[MetricKind.HEART_RATE_BPM]
                as MetricResult.Available).value, 0.0)
            val newSummary = summary(74.0).copy(activity = MeasurementActivity.RECENT_ACTIVITY)
            RoomMeasurementRepository(migrated.measurements()).save(accountA, newSummary)
            assertEquals(MeasurementActivity.RECENT_ACTIVITY,
                RoomMeasurementRepository(migrated.measurements()).observeHistory(accountA)
                    .first().first().summary.activity)
        } finally {
            migrated.close()
            context.deleteDatabase(name)
        }
    }

    private fun summary(bpm: Double): MeasurementSummary {
        val quality = QualityReport(true, 1.0, emptySet(), 900, 30_000, "ppg-0.1")
        val profile = DeviceProfile("Example", "Phone", 36, "arm64-v8a", "0.1.0", "ppg-0.1",
            true, true, 1280, 720, 30.0, null, "torch on", emptySet(), 1.0,
            SupportClassification.COMPATIBLE)
        return MeasurementSummary(1_790_000_000_000, 30_000, quality, profile,
            mapOf(MetricKind.HEART_RATE_BPM to MetricResult.Available(bpm)))
    }

    private companion object {
        const val V1_CREATE_TABLE = "CREATE TABLE IF NOT EXISTS `measurement_summaries` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `accountId` TEXT NOT NULL, `measuredAtEpochMillis` INTEGER NOT NULL, `validDurationMillis` INTEGER NOT NULL, `qualityScore` REAL NOT NULL, `qualityIssues` TEXT NOT NULL, `evaluatedSamples` INTEGER NOT NULL, `pipelineVersion` TEXT NOT NULL, `manufacturer` TEXT NOT NULL, `deviceModel` TEXT NOT NULL, `androidApiLevel` INTEGER NOT NULL, `abi` TEXT NOT NULL, `appVersion` TEXT NOT NULL, `hasRearCamera` INTEGER NOT NULL, `hasTorch` INTEGER NOT NULL, `analysisWidth` INTEGER, `analysisHeight` INTEGER, `observedFramesPerSecond` REAL, `exposureDescription` TEXT, `flashDescription` TEXT, `rejectionReasons` TEXT NOT NULL, `lastQualityScore` REAL, `supportClassification` TEXT NOT NULL, `metricStatuses` TEXT NOT NULL, `modelVersion` TEXT)"
    }
}
