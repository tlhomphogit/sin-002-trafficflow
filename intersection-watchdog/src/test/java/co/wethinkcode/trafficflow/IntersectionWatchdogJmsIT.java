package co.wethinkcode.trafficflow;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.junit.jupiter.api.Test;

import javax.jms.Connection;
import javax.jms.DeliveryMode;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.TextMessage;

import static org.junit.jupiter.api.Assertions.*;

class IntersectionWatchdogJmsIT {

    @Test
    void testJmsConsumerRoundTrip() throws Exception {
        String testQueue = "it-watchdog-roundtrip-queue";
        IntersectionWatchdogService service = new IntersectionWatchdogService(
            "tcp://localhost:61616", testQueue, 5000L, 1000L
        );

        service.start();
        assertTrue(service.isRunning());

        ActiveMQConnectionFactory factory = new ActiveMQConnectionFactory("tcp://localhost:61616");
        try (Connection conn = factory.createConnection()) {
            conn.start();
            try (Session session = conn.createSession(false, Session.AUTO_ACKNOWLEDGE)) {
                Queue queue = session.createQueue(testQueue);
                try (MessageProducer producer = session.createProducer(queue)) {
                    producer.setDeliveryMode(DeliveryMode.NON_PERSISTENT);
                    HeartbeatMessage msg = new HeartbeatMessage("INT-RT-1", "Uptown", "UP", System.currentTimeMillis());
                    TextMessage textMessage = session.createTextMessage(msg.toJson());
                    producer.send(textMessage);
                }
            }
        }

        // Wait up to 3 seconds for message to be consumed by listener
        int attempts = 30;
        while (attempts-- > 0 && !service.getTrackers().containsKey("INT-RT-1")) {
            Thread.sleep(100);
        }

        assertTrue(service.getTrackers().containsKey("INT-RT-1"), "Watchdog should have received and tracked INT-RT-1");
        IntersectionTracker tracker = service.getTrackers().get("INT-RT-1");
        assertEquals("Uptown", tracker.getDistrict());
        assertFalse(tracker.isInAlert());
        assertEquals("HEALTHY", tracker.getStatus());

        service.close();
        assertFalse(service.isRunning());
    }
}
