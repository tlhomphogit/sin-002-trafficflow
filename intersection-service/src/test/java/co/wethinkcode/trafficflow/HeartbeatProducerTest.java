package co.wethinkcode.trafficflow;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HeartbeatProducerTest {

    @Test
    void testProducerDefaultIntersectionsNotEmpty() {
        HeartbeatProducer producer = new HeartbeatProducer();
        assertNotNull(producer.getIntersections());
        assertFalse(producer.getIntersections().isEmpty());
        assertEquals(3, producer.getIntersections().size());
        assertEquals("INT-1001", producer.getIntersections().get(0).id());
        assertFalse(producer.isRunning());
    }
}
