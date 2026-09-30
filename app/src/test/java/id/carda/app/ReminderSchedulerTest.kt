package id.carda.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test
import java.time.Duration
import java.time.ZonedDateTime

class ReminderSchedulerTest {
    @Test fun nextReminderUsesSelectedLocalHour() {
        val now = ZonedDateTime.parse("2026-09-29T07:30:00+07:00[Asia/Jakarta]")
        assertEquals(Duration.ofMinutes(30), delayUntilNextHour(now, 8))
        assertEquals(Duration.ofHours(24), delayUntilNextHour(now.withHour(8).withMinute(0), 8))
    }

    @Test fun invalidHourIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            delayUntilNextHour(ZonedDateTime.now(), 24)
        }
    }

    @Test fun nextOccurrenceTracksLocalClockAcrossDaylightSavingChange() {
        val beforeSpringChange = ZonedDateTime.parse("2026-03-07T09:00:00-05:00[America/New_York]")
        val next = nextReminderTime(beforeSpringChange, 8)
        assertEquals(ZonedDateTime.parse("2026-03-08T08:00:00-04:00[America/New_York]"), next)
        assertEquals(Duration.ofHours(22), Duration.between(beforeSpringChange, next))
    }

    @Test fun nextOccurrenceUsesNewTimezoneAfterUserMoves() {
        val jakarta = ZonedDateTime.parse("2026-09-30T07:00:00+07:00[Asia/Jakarta]")
        val singapore = jakarta.withZoneSameInstant(java.time.ZoneId.of("Asia/Singapore"))
        assertEquals(Duration.ofHours(1), Duration.between(singapore,
            nextReminderTime(singapore, 9)))
    }

    @Test fun completedReminderAlwaysTargetsFollowingLocalCalendarDay() {
        val slightlyEarly = ZonedDateTime.parse("2026-09-30T07:59:59+07:00[Asia/Jakarta]")
        assertEquals(ZonedDateTime.parse("2026-10-01T08:00:00+07:00[Asia/Jakarta]"),
            nextReminderAfterRun(slightlyEarly, 8))
        val afterSpringChange = ZonedDateTime.parse("2026-03-08T07:59:59-04:00[America/New_York]")
        assertEquals(ZonedDateTime.parse("2026-03-09T08:00:00-04:00[America/New_York]"),
            nextReminderAfterRun(afterSpringChange, 8))
    }

    @Test fun reopeningAfterEarlyDeliveryDoesNotPullReminderBackToSameDay() {
        val now = ZonedDateTime.parse("2026-09-30T07:59:59+07:00[Asia/Jakarta]")
        val nextDay = nextReminderAfterRun(now, 8).toInstant().toEpochMilli()
        assertTrue(shouldKeepReminderSchedule(nextDay, now, 8, afterCurrentRun = false))
        val today = nextReminderTime(now, 8).toInstant().toEpochMilli()
        assertFalse(shouldKeepReminderSchedule(today, now, 8, afterCurrentRun = true))
        val wrongLocalHour = nextReminderAfterRun(now, 9).toInstant().toEpochMilli()
        assertFalse(shouldKeepReminderSchedule(wrongLocalHour, now, 8, afterCurrentRun = false))
        assertFalse(shouldKeepReminderSchedule(Long.MAX_VALUE, now, 8, afterCurrentRun = false))
    }

    @Test fun queuedReminderCannotOverrideOptOutOrAccountChange() {
        assertTrue(reminderIsEligible("a", 8, "a", 8))
        assertFalse(reminderIsEligible("a", 8, "a", null))
        assertFalse(reminderIsEligible("a", 8, "b", 8))
        assertFalse(reminderIsEligible("a", 8, null, 8))
        assertFalse(reminderIsEligible("a", 8, "a", 9))
        assertFalse(reminderIsEligible("a", -1, "a", -1))
    }
}
