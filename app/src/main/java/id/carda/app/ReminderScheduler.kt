package id.carda.app

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import id.carda.core.auth.EncryptedSessionStore
import id.carda.core.data.ProfilePreferencesStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.ensureActive
import java.time.Duration
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

/** Optional local wellness reminder. Scheduling is approximate under Android power management. */
internal class ReminderScheduler(private val context: Context) {
    private val mutex = Mutex()

    suspend fun schedule(accountId: String, hour: Int, afterCurrentRun: Boolean = false) = withContext(Dispatchers.IO) {
        require(hour in 0..23)
        require(java.util.UUID.fromString(accountId).toString() == accountId)
        mutex.withLock {
            val workManager = WorkManager.getInstance(context)
            val tag = "reminder-config:$accountId:$hour"
            val existing = workManager.getWorkInfosForUniqueWork(WORK_NAME).get(5, TimeUnit.SECONDS)
            coroutineContext.ensureActive()
            val now = ZonedDateTime.now()
            val nextEpochMillis = (if (afterCurrentRun) nextReminderAfterRun(now, hour)
                else nextReminderTime(now, hour)).toInstant().toEpochMilli()
            if (existing.any { tag in it.tags &&
                    it.state in setOf(WorkInfo.State.ENQUEUED, WorkInfo.State.RUNNING, WorkInfo.State.BLOCKED) &&
                    shouldKeepReminderSchedule(it.nextScheduleTimeMillis, now, hour, afterCurrentRun)
                }) return@withLock
            val request = PeriodicWorkRequestBuilder<ReminderWorker>(24, TimeUnit.HOURS)
                .setNextScheduleTimeOverride(nextEpochMillis)
                .setInputData(workDataOf(ReminderWorker.ACCOUNT_ID to accountId, ReminderWorker.SELECTED_HOUR to hour))
                .addTag(tag)
                .build()
            workManager.enqueueUniquePeriodicWork(
                WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request,
            ).result.get(5, TimeUnit.SECONDS)
        }
    }

    fun cancel() {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }

    companion object { const val WORK_NAME = "carda_local_measurement_reminder" }
}

internal fun nextReminderTime(now: ZonedDateTime, hour: Int): ZonedDateTime {
    require(hour in 0..23)
    var next = now.toLocalDate().atTime(hour, 0).atZone(now.zone)
    if (!next.isAfter(now)) next = now.toLocalDate().plusDays(1).atTime(hour, 0).atZone(now.zone)
    return next
}

/** A delivered reminder must not be queued again for later on the same local date. */
internal fun nextReminderAfterRun(now: ZonedDateTime, hour: Int): ZonedDateTime {
    require(hour in 0..23)
    return now.toLocalDate().plusDays(1).atTime(hour, 0).atZone(now.zone)
}

internal fun shouldKeepReminderSchedule(existingEpochMillis: Long, now: ZonedDateTime,
    hour: Int, afterCurrentRun: Boolean): Boolean {
    val target = if (afterCurrentRun) nextReminderAfterRun(now, hour) else nextReminderTime(now, hour)
    if (existingEpochMillis <= 0) return false
    if (kotlin.math.abs(existingEpochMillis - target.toInstant().toEpochMilli()) < 60_000L) return true
    // A reminder sent slightly early may have already moved to tomorrow. Reopening the app
    // before today's selected hour must not pull it back and deliver it twice.
    val existing = runCatching { java.time.Instant.ofEpochMilli(existingEpochMillis).atZone(now.zone) }
        .getOrNull() ?: return false
    return !afterCurrentRun && target.toLocalDate() == now.toLocalDate() &&
        existing.isAfter(now) && existing.toLocalDate() == now.toLocalDate().plusDays(1) &&
        existing.hour == hour && existing.minute == 0
}

internal fun delayUntilNextHour(now: ZonedDateTime, hour: Int): Duration =
    Duration.between(now, nextReminderTime(now, hour))

/** System clock/timezone and boot changes re-evaluate the stored local opt-in. */
internal class ReminderClockChangeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in setOf(Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED,
                Intent.ACTION_BOOT_COMPLETED)) return
        WorkManager.getInstance(context).enqueueUniqueWork(
            "carda_reminder_clock_reschedule", ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<ReminderClockRescheduleWorker>().build(),
        )
    }
}

internal class ReminderClockRescheduleWorker(context: Context, parameters: WorkerParameters) :
    CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result = try {
        val account = EncryptedSessionStore(applicationContext).read()?.accountId
        val scheduler = ReminderScheduler(applicationContext)
        val hour = account?.let {
            ProfilePreferencesStore.open(applicationContext).observeReminderHour(it).first()
        }
        if (account == null || hour == null) scheduler.cancel() else scheduler.schedule(account, hour)
        Result.success()
    } catch (cancelled: CancellationException) { throw cancelled }
    catch (_: Exception) { Result.retry() }
}

internal class ReminderWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val expectedAccount = inputData.getString(ACCOUNT_ID) ?: return Result.success()
        val selectedHour = inputData.getInt(SELECTED_HOUR, -1)
        try {
            val active = EncryptedSessionStore(applicationContext).read()?.accountId
            val selected = ProfilePreferencesStore.open(applicationContext).observeReminderHour(expectedAccount).first()
            if (!reminderIsEligible(expectedAccount, selectedHour, active, selected)) return Result.success()
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { return Result.success() } // Fail closed if opt-in/session cannot be read.
        // Recalculate tomorrow from the current local calendar. A fixed 24-hour period alone
        // drifts after timezone or daylight-saving changes; this update keeps the same work ID.
        try { ReminderScheduler(applicationContext).schedule(expectedAccount, selectedHour, afterCurrentRun = true) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { /* Keep today's eligible reminder; app/clock worker can reschedule. */ }
        if (Build.VERSION.SDK_INT >= 33 && applicationContext.checkSelfPermission(
                Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
        ) return Result.success()

        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(
            CHANNEL_ID, "Pengingat pengukuran", NotificationManager.IMPORTANCE_DEFAULT,
        ))
        val intent = Intent(applicationContext, MainActivity::class.java)
        val pending = PendingIntent.getActivity(applicationContext, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = Notification.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Pengingat Carda")
            .setContentText("Jika ingin, luangkan waktu untuk pengukuran saat Anda nyaman.")
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        try { manager.notify(NOTIFICATION_ID, notification) }
        catch (_: SecurityException) { return Result.success() } // Permission may change while running.
        return Result.success()
    }

    companion object {
        const val ACCOUNT_ID = "account_id"
        const val SELECTED_HOUR = "selected_hour"
        private const val CHANNEL_ID = "carda_measurement_reminders"
        private const val NOTIFICATION_ID = 1001
    }
}

internal fun reminderIsEligible(expectedAccount: String, requestedHour: Int, activeAccount: String?, selectedHour: Int?): Boolean =
    expectedAccount == activeAccount && requestedHour in 0..23 && selectedHour == requestedHour
