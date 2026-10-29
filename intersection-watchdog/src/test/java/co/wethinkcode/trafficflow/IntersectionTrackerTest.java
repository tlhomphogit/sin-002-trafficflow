package co.wethinkcode.trafficflow;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class IntersectionTrackerTest {

    @Test
    void testStateTransitionsAndAlerts() {
        IntersectionTracker tracker = new IntersectionTracker("INT-1001", "Downtown", 1000L);
        assertEquals("INT-1001", tracker.getIntersectionId());
        assertEquals("Downtown", tracker.getDistrict());
        assertEquals(1000L, tracker.getLastSeenTimestamp());
        assertFalse(tracker.isInAlert());
        assertEquals("HEALTHY", tracker.getStatus());

        tracker.updateHeartbeat("Downtown", "UP", 2500L);
        assertEquals(2500L, tracker.getLastSeenTimestamp());
        assertEquals("HEALTHY", tracker.getStatus());

        tracker.setInAlert(true);
        tracker.setStatus("ALERT_MISSED_HEARTBEAT");
        assertTrue(tracker.isInAlert());
        assertEquals("ALERT_MISSED_HEARTBEAT", tracker.getStatus());

        tracker.setInAlert(false);
        tracker.setStatus("HEALTHY");
        assertFalse(tracker.isInAlert());
        assertEquals("HEALTHY", tracker.getStatus());
    }

    @Test
    void testToJsonContainsRequiredFields() {
        IntersectionTracker tracker = new IntersectionTracker("INT-1005", "Downtown", 5000L);
        String json = tracker.toJson();
        assertNotNull(json);
        assertTrue(json.contains("\"intersectionId\":\"INT-1005\""));
        assertTrue(json.contains("\"district\":\"Downtown\""));
        assertTrue(json.contains("\"inAlert\":false"));
    }
}
