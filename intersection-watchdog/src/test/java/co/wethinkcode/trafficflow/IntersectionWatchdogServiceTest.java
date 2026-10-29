package co.wethinkcode.trafficflow;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class IntersectionWatchdogServiceTest {

    @Test
    void testTimeoutDetectionWhenNoMessagesArrive() {
        IntersectionWatchdogService service = new IntersectionWatchdogService(
            "tcp://localhost:61616", "dummy-queue", 200L, 50L
        );

        // Pre-register an intersection seen 500ms ago (exceeding 200ms timeout)
        long pastTime = System.currentTimeMillis() - 500L;
        service.registerKnownIntersection("INT-9999", "Eastside");
        service.getTrackers().get("INT-9999").setLastSeenTimestamp(pastTime);

        assertFalse(service.hasAlerts());

        // Trigger timeout check explicitly
        service.checkTimeouts();

        assertTrue(service.hasAlerts());
        IntersectionTracker updated = service.getTrackers().get("INT-9999");
        assertTrue(updated.isInAlert());
        assertEquals("ALERT_MISSED_HEARTBEAT", updated.getStatus());
    }

    @Test
    void testRecoveryWhenHeartbeatResumes() {
        IntersectionWatchdogService service = new IntersectionWatchdogService(
            "tcp://localhost:61616", "dummy-queue", 200L, 50L
        );

        // Put an intersection into alert state
        service.registerKnownIntersection("INT-8888", "Westside");
        IntersectionTracker tracker = service.getTrackers().get("INT-8888");
        tracker.setInAlert(true);
        tracker.setStatus("ALERT_MISSED_HEARTBEAT");
        assertTrue(service.hasAlerts());

        // Process a valid heartbeat (status UP)
        HeartbeatMessage msg = new HeartbeatMessage("INT-8888", "Westside", "UP", System.currentTimeMillis());
        service.processHeartbeat(msg);

        // Verify recovered to HEALTHY
        assertFalse(service.hasAlerts());
        assertFalse(tracker.isInAlert());
        assertEquals("HEALTHY", tracker.getStatus());
    }

    @Test
    void testInvalidMessageDoesNotRefreshState() {
        IntersectionWatchdogService service = new IntersectionWatchdogService(
            "tcp://localhost:61616", "dummy-queue", 200L, 50L
        );

        long initialTime = System.currentTimeMillis() - 300L;
        service.registerKnownIntersection("INT-7777", "Downtown");
        IntersectionTracker tracker = service.getTrackers().get("INT-7777");
        tracker.setLastSeenTimestamp(initialTime);
        tracker.setInAlert(true);
        tracker.setStatus("ALERT_MISSED_HEARTBEAT");

        // Attempt 1: Null heartbeat
        service.processHeartbeat(null);
        assertTrue(tracker.isInAlert());
        assertEquals(initialTime, tracker.getLastSeenTimestamp());

        // Attempt 2: Heartbeat with blank ID
        service.processHeartbeat(new HeartbeatMessage("", "Downtown", "UP", System.currentTimeMillis()));
        assertTrue(tracker.isInAlert());
        assertEquals(initialTime, tracker.getLastSeenTimestamp());

        // Attempt 3: Heartbeat with status DOWN
        service.processHeartbeat(new HeartbeatMessage("INT-7777", "Downtown", "DOWN", System.currentTimeMillis()));
        assertTrue(tracker.isInAlert());
        assertEquals(initialTime, tracker.getLastSeenTimestamp());

        // Attempt 4: Heartbeat with invalid timestamp
        service.processHeartbeat(new HeartbeatMessage("INT-7777", "Downtown", "UP", -1L));
        assertTrue(tracker.isInAlert());
        assertEquals(initialTime, tracker.getLastSeenTimestamp());
    }
}
