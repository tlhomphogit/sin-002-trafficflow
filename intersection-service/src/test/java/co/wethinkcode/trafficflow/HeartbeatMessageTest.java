package co.wethinkcode.trafficflow;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HeartbeatMessageTest {

    @Test
    void testSerializationAndDeserialization() {
        HeartbeatMessage original = new HeartbeatMessage("INT-1001", "Downtown", "UP", 1730230000000L);
        String json = original.toJson();

        assertNotNull(json);
        assertTrue(json.contains("\"intersectionId\":\"INT-1001\""));
        assertTrue(json.contains("\"district\":\"Downtown\""));
        assertTrue(json.contains("\"status\":\"UP\""));
        assertTrue(json.contains("\"timestamp\":1730230000000"));

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
    void testEscapingInJson() {
        HeartbeatMessage msg = new HeartbeatMessage("INT-\"TEST\"", "Mid\"town", "UP", 100L);
        String json = msg.toJson();
        HeartbeatMessage parsed = HeartbeatMessage.fromJson(json);
        assertNotNull(parsed);
    }

    @Test
    void testValidation() {
        HeartbeatMessage valid = new HeartbeatMessage("INT-1001", "Downtown", "UP", 1000L);
        assertTrue(valid.isValid());

        // Blank/null id
        assertFalse(new HeartbeatMessage(null, "Downtown", "UP", 1000L).isValid());
        assertFalse(new HeartbeatMessage("", "Downtown", "UP", 1000L).isValid());
        assertFalse(new HeartbeatMessage("  ", "Downtown", "UP", 1000L).isValid());

        // Invalid timestamp
        assertFalse(new HeartbeatMessage("INT-1001", "Downtown", "UP", 0L).isValid());
        assertFalse(new HeartbeatMessage("INT-1001", "Downtown", "UP", -10L).isValid());

        // Non-UP status
        assertFalse(new HeartbeatMessage("INT-1001", "Downtown", null, 1000L).isValid());
        assertFalse(new HeartbeatMessage("INT-1001", "Downtown", "", 1000L).isValid());
        assertFalse(new HeartbeatMessage("INT-1001", "Downtown", "DOWN", 1000L).isValid());
    }
}
