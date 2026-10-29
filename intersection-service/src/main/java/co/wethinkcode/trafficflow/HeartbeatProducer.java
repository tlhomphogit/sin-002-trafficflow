package co.wethinkcode.trafficflow;

import co.wethinkcode.trafficflow.mq.MqConfig;
import org.apache.activemq.ActiveMQConnectionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jms.Connection;
import javax.jms.DeliveryMode;
import javax.jms.JMSException;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.TextMessage;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class HeartbeatProducer implements AutoCloseable {
    private static final Logger logger = LoggerFactory.getLogger(HeartbeatProducer.class);

    private final String brokerUrl;
    private final String queueName;
    private final long intervalMs;
    private final List<IntersectionDemoItem> intersections;

    private Connection connection;
    private Session session;
    private MessageProducer producer;
    private ScheduledExecutorService scheduler;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public record IntersectionDemoItem(String id, String district) {}

    public static final List<IntersectionDemoItem> DEFAULT_INTERSECTIONS = List.of(
        new IntersectionDemoItem("INT-1001", "Downtown"),
        new IntersectionDemoItem("INT-1002", "Midtown"),
        new IntersectionDemoItem("INT-1005", "Downtown")
    );

    public HeartbeatProducer() {
        this(
            System.getProperty("activemq.broker.url", System.getenv().getOrDefault("ACTIVEMQ_BROKER_URL", MqConfig.BROKER_URL)),
            MqConfig.HEARTBEAT_QUEUE,
            Long.getLong("heartbeat.interval.ms", 2000L),
            DEFAULT_INTERSECTIONS
        );
    }

    public HeartbeatProducer(String brokerUrl, String queueName, long intervalMs, List<IntersectionDemoItem> intersections) {
        this.brokerUrl = brokerUrl;
        this.queueName = queueName;
        this.intervalMs = intervalMs;
        this.intersections = (intersections == null || intersections.isEmpty()) ? DEFAULT_INTERSECTIONS : intersections;
    }

    public synchronized void start() throws JMSException {
        if (running.get()) {
            return;
        }
        logger.info("Initializing HeartbeatProducer (broker: {}, queue: {}, interval: {}ms)", brokerUrl, queueName, intervalMs);
        ActiveMQConnectionFactory factory = new ActiveMQConnectionFactory(brokerUrl);
        connection = factory.createConnection();
        connection.start();

        session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
        Queue queue = session.createQueue(queueName);
        producer = session.createProducer(queue);
        producer.setDeliveryMode(DeliveryMode.NON_PERSISTENT);

        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "HeartbeatProducer-Scheduler");
            t.setDaemon(true);
            return t;
        });

        running.set(true);
        scheduler.scheduleAtFixedRate(this::sendHeartbeats, 0, intervalMs, TimeUnit.MILLISECONDS);
        logger.info("HeartbeatProducer started. Publishing heartbeats every {} ms for {} intersections", intervalMs, intersections.size());
    }

    public void sendHeartbeats() {
        if (!running.get() || session == null || producer == null) {
            return;
        }
        long now = System.currentTimeMillis();
        for (IntersectionDemoItem item : intersections) {
            try {
                HeartbeatMessage msg = new HeartbeatMessage(item.id(), item.district(), "UP", now);
                TextMessage textMessage = session.createTextMessage(msg.toJson());
                textMessage.setStringProperty("intersectionId", item.id());
                producer.send(textMessage);
                logger.info("[HEARTBEAT-PRODUCER] Sent heartbeat for {} ({}) - ts={}", item.id(), item.district(), now);
            } catch (Exception e) {
                logger.error("[HEARTBEAT-PRODUCER] Failed to send heartbeat for {}: {}", item.id(), e.getMessage());
            }
        }
    }

    public boolean isRunning() {
        return running.get();
    }

    public List<IntersectionDemoItem> getIntersections() {
        return Collections.unmodifiableList(intersections);
    }

    @Override
    public synchronized void close() {
        if (!running.getAndSet(false)) {
            return;
        }
        logger.info("Stopping HeartbeatProducer...");
        if (scheduler != null) {
            scheduler.shutdownNow();
            try {
                if (!scheduler.awaitTermination(2, TimeUnit.SECONDS)) {
                    logger.warn("Scheduler did not terminate within 2 seconds.");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        try {
            if (producer != null) producer.close();
            if (session != null) session.close();
            if (connection != null) connection.close();
        } catch (JMSException e) {
            logger.warn("Error while closing JMS resources: {}", e.getMessage());
        }
        logger.info("HeartbeatProducer stopped cleanly.");
    }
}
