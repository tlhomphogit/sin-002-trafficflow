package co.wethinkcode.trafficflow;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HeartbeatMessageTest {

    @Test
    void testSerializationAndDeserialization() {
        HeartbeatMessage original = new HeartbeatMessage("INT-1002", "Midtown", "UP", 1730230005000L);
        String json = original.toJson();

        assertNotNull(json);
        assertTrue(json.contains("\"intersectionId\":\"INT-1002\""));

        HeartbeatMessage deserialized = HeartbeatMessage.fromJson(json);
        assertNotNull(deserialized);
        assertEquals(original.getIntersectionId(), deserialized.getIntersectionId());
        assertEquals(original.getDistrict(), deserialized.getDistrict());
        assertEquals(original.getStatus(), deserialized.getStatus());
        assertEquals(original.getTimestamp(), deserialized.getTimestamp());
        assertEquals(original, deserialized);
    }

    @Test
    void testEmptyAndNullHandling() {
        assertNull(HeartbeatMessage.fromJson(null));
        assertNull(HeartbeatMessage.fromJson("   "));

        HeartbeatMessage msg = HeartbeatMessage.fromJson("{}");
        assertNotNull(msg);
        assertNull(msg.getIntersectionId());
        assertEquals(0L, msg.getTimestamp());
    }

    @Test
    void testValidation() {
        HeartbeatMessage valid = new HeartbeatMessage("INT-1002", "Midtown", "UP", 1000L);
        assertTrue(valid.isValid());

        assertFalse(new HeartbeatMessage(null, "Midtown", "UP", 1000L).isValid());
        assertFalse(new HeartbeatMessage("  ", "Midtown", "UP", 1000L).isValid());
        assertFalse(new HeartbeatMessage("INT-1002", "Midtown", "UP", 0L).isValid());
        assertFalse(new HeartbeatMessage("INT-1002", "Midtown", "DOWN", 1000L).isValid());
    }
}
