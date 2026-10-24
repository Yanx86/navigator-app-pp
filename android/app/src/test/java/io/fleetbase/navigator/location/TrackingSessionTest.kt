package io.fleetbase.navigator.location

import org.junit.Assert.*
import org.junit.Test

class TrackingSessionTest {
    @Test fun stoppingInvalidatesCallbacksAndDiscardsCredentials() {
        val sessions = TrackingSession()
        val first = sessions.start("https://example.test/drivers/a/track", "token-a")
        sessions.stop()
        assertFalse(sessions.owns(first))
        assertNull(sessions.current())
    }

    @Test fun replacementCannotBeMutatedByAnOldCallback() {
        val sessions = TrackingSession()
        val first = sessions.start("https://example.test/drivers/a/track", "token-a")
        val second = sessions.start("https://example.test/drivers/b/track", "token-b")
        assertFalse(sessions.owns(first))
        assertTrue(sessions.owns(second))
        assertEquals("token-b", sessions.current()?.token)
    }

    @Test fun restartingSameCredentialsStillCreatesANewGeneration() {
        val sessions = TrackingSession()
        val first = sessions.start("https://example.test/track", "token")
        sessions.stop()
        val second = sessions.start("https://example.test/track", "token")
        assertFalse(sessions.owns(first))
        assertTrue(sessions.owns(second))
    }
}
