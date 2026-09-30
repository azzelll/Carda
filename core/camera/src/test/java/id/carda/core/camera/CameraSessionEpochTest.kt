package id.carda.core.camera

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CameraSessionEpochTest {
    @Test fun stoppedSessionCallbackCannotStopRetrySession() {
        val gate = CameraSessionEpoch()
        val first = gate.begin()!!
        assertNull(gate.begin())
        assertTrue(gate.stop(first))
        val retry = gate.begin()!!
        assertFalse(gate.isCurrent(first))
        assertFalse(gate.stop(first))
        assertTrue(gate.isCurrent(retry))
    }

    @Test fun repeatedStopAndDelayedCallbackRemainInactive() {
        val gate = CameraSessionEpoch()
        val token = gate.begin()!!
        assertTrue(gate.stopCurrent() == token)
        assertNull(gate.stopCurrent())
        assertFalse(gate.isCurrent(token))
        assertFalse(gate.stop(token))
    }
}
