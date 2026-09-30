package id.carda.app

import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.WorkInfo
import androidx.work.WorkManager
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

class ReminderScheduleInstrumentedTest {
    @Test fun reopeningSameConfigurationPreservesRequestAndChangingHourUpdatesIt() = runBlocking<Unit> {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val manager = WorkManager.getInstance(context)
        val scheduler = ReminderScheduler(context)
        val account = "00000000-0000-0000-0000-000000000017"
        fun active() = manager.getWorkInfosForUniqueWork(ReminderScheduler.WORK_NAME).get(5, TimeUnit.SECONDS)
            .filter { it.state in setOf(WorkInfo.State.ENQUEUED, WorkInfo.State.RUNNING, WorkInfo.State.BLOCKED) }
        manager.cancelUniqueWork(ReminderScheduler.WORK_NAME).result.get(5, TimeUnit.SECONDS)
        try {
            scheduler.schedule(account, 8)
            val first = active().single().id
            val expected = nextReminderTime(ZonedDateTime.now(), 8).toInstant().toEpochMilli()
            assertTrue(kotlin.math.abs(active().single().nextScheduleTimeMillis - expected) < 60_000L)
            scheduler.schedule(account, 8)
            assertEquals(first, active().single().id)
            scheduler.schedule(account, 9)
            assertEquals(first, active().single().id)
            assertTrue(active().single().tags.contains("reminder-config:$account:9"))
            val changedTarget = nextReminderTime(ZonedDateTime.now(), 9).toInstant().toEpochMilli()
            assertTrue(kotlin.math.abs(active().single().nextScheduleTimeMillis - changedTarget) < 60_000L)
            scheduler.schedule(account, 9, afterCurrentRun = true)
            assertEquals(first, active().single().id)
            val followingDay = nextReminderAfterRun(ZonedDateTime.now(), 9).toInstant().toEpochMilli()
            assertTrue(kotlin.math.abs(active().single().nextScheduleTimeMillis - followingDay) < 60_000L)
            scheduler.cancel()
            manager.cancelUniqueWork(ReminderScheduler.WORK_NAME).result.get(5, TimeUnit.SECONDS)
            assertTrue(active().isEmpty())
        } finally { manager.cancelUniqueWork(ReminderScheduler.WORK_NAME).result.get(5, TimeUnit.SECONDS) }
    }
}
