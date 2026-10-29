package co.wethinkcode.trafficflow;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class HeartbeatProducerIT {

    @Test
    void testProducerLifecycleWithRealBroker() {
        HeartbeatProducer producer = new HeartbeatProducer(
            "tcp://localhost:61616",
            "it-heartbeat-producer-queue",
            500L,
            List.of(new HeartbeatProducer.IntersectionDemoItem("TEST-01", "ZoneA"))
        );

        assertFalse(producer.isRunning());
        assertDoesNotThrow(producer::start);
        assertTrue(producer.isRunning());

        assertDoesNotThrow(producer::sendHeartbeats);

        producer.close();
        assertFalse(producer.isRunning());
    }
}
