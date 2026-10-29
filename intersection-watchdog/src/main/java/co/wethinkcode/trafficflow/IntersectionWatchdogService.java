package co.wethinkcode.trafficflow;

import co.wethinkcode.trafficflow.mq.MqConfig;
import org.apache.activemq.ActiveMQConnectionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jms.Connection;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageListener;
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.TextMessage;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class IntersectionWatchdogService implements MessageListener, AutoCloseable {
    private static final Logger logger = LoggerFactory.getLogger(IntersectionWatchdogService.class);

    private final String brokerUrl;
    private final String queueName;
    private final long timeoutMs;
    private final long checkIntervalMs;

    private final Map<String, IntersectionTracker> trackers = new ConcurrentHashMap<>();

    private Connection connection;
    private Session session;
    private MessageConsumer consumer;
    private ScheduledExecutorService scheduler;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public IntersectionWatchdogService() {
        this(
            System.getProperty("activemq.broker.url", System.getenv().getOrDefault("ACTIVEMQ_BROKER_URL", MqConfig.BROKER_URL)),
            MqConfig.HEARTBEAT_QUEUE,
            Long.getLong("watchdog.timeout.ms", 5000L),
            Long.getLong("watchdog.check.interval.ms", 1000L)
        );
    }

    public IntersectionWatchdogService(String brokerUrl, String queueName, long timeoutMs, long checkIntervalMs) {
        this.brokerUrl = brokerUrl;
        this.queueName = queueName;
        this.timeoutMs = timeoutMs;
        this.checkIntervalMs = checkIntervalMs;
    }

    public synchronized void start() throws JMSException {
        if (running.get()) {
            return;
        }
        logger.info("Initializing IntersectionWatchdogService (broker: {}, queue: {}, timeout: {}ms, checkInterval: {}ms)",
            brokerUrl, queueName, timeoutMs, checkIntervalMs);

        ActiveMQConnectionFactory factory = new ActiveMQConnectionFactory(brokerUrl);
        connection = factory.createConnection();
        connection.start();

        session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
        Queue queue = session.createQueue(queueName);
        consumer = session.createConsumer(queue);
        consumer.setMessageListener(this);

        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "Watchdog-TimeoutChecker");
            t.setDaemon(true);
            return t;
        });

        running.set(true);
        scheduler.scheduleAtFixedRate(this::checkTimeouts, checkIntervalMs, checkIntervalMs, TimeUnit.MILLISECONDS);
        logger.info("IntersectionWatchdogService started. Listening on queue {}...", queueName);
    }

    @Override
    public void onMessage(Message message) {
        if (!running.get()) {
            return;
        }
        try {
            if (message instanceof TextMessage textMessage) {
                String payload = textMessage.getText();
                HeartbeatMessage heartbeat = HeartbeatMessage.fromJson(payload);
                if (heartbeat != null && heartbeat.isValid()) {
                    processHeartbeat(heartbeat);
                } else {
                    logger.warn("[WATCHDOG-WARN] Discarded invalid or malformed heartbeat message: {}", payload);
                }
            } else {
                logger.warn("[WATCHDOG-WARN] Received non-text message of type: {}", message.getClass().getName());
            }
        } catch (Exception e) {
            logger.error("[WATCHDOG-ERROR] Error processing message: {}", e.getMessage(), e);
        }
    }

    public void processHeartbeat(HeartbeatMessage heartbeat) {
        if (heartbeat == null || !heartbeat.isValid()) {
            return;
        }
        String id = heartbeat.getIntersectionId();
        String district = heartbeat.getDistrict();
        long now = System.currentTimeMillis();

        IntersectionTracker tracker = trackers.computeIfAbsent(id, k -> {
            logger.info("[WATCHDOG-DISCOVERY] Discovered new intersection {} ({})", id, district);
            return new IntersectionTracker(id, district, now);
        });

        synchronized (tracker) {
            boolean wasInAlert = tracker.isInAlert();
            if (wasInAlert) {
                tracker.setInAlert(false);
                tracker.setStatus("HEALTHY");
                logger.warn("[WATCHDOG-RECOVERY] Heartbeat resumed for intersection {} ({})! State restored to HEALTHY.", id, district);
            } else {
                long lag = now - heartbeat.getTimestamp();
                logger.info("[WATCHDOG-RECEIVE] Heartbeat received for {} ({}) - latency: {}ms", id, district, lag);
            }
            tracker.updateHeartbeat(district, heartbeat.getStatus(), now);
        }
    }

    public void checkTimeouts() {
        long now = System.currentTimeMillis();
        for (IntersectionTracker tracker : trackers.values()) {
            synchronized (tracker) {
                long elapsed = now - tracker.getLastSeenTimestamp();
                if (elapsed > timeoutMs && !tracker.isInAlert()) {
                    tracker.setInAlert(true);
                    tracker.setStatus("ALERT_MISSED_HEARTBEAT");
                    logger.error("[WATCHDOG-ALERT] Missing heartbeat for intersection {} ({})! Elapsed: {}ms (timeout threshold: {}ms)",
                        tracker.getIntersectionId(), tracker.getDistrict(), elapsed, timeoutMs);
                }
            }
        }
    }

    public boolean isRunning() {
        return running.get();
    }

    public Map<String, IntersectionTracker> getTrackers() {
        return Collections.unmodifiableMap(trackers);
    }

    public void registerKnownIntersection(String id, String district) {
        trackers.putIfAbsent(id, new IntersectionTracker(id, district, System.currentTimeMillis()));
    }

    public boolean hasAlerts() {
        return trackers.values().stream().anyMatch(IntersectionTracker::isInAlert);
    }

    public String getStatusSummaryJson() {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"service\":\"intersection-watchdog\",\"status\":\"UP\",\"monitoredCount\":")
          .append(trackers.size())
          .append(",\"alertCount\":")
          .append(trackers.values().stream().filter(IntersectionTracker::isInAlert).count())
          .append(",\"intersections\":[");

        boolean first = true;
        for (IntersectionTracker tracker : trackers.values()) {
            if (!first) sb.append(",");
            sb.append(tracker.toJson());
            first = false;
        }
        sb.append("]}");
        return sb.toString();
    }

    @Override
    public synchronized void close() {
        if (!running.getAndSet(false)) {
            return;
        }
        logger.info("Stopping IntersectionWatchdogService...");
        if (scheduler != null) {
            scheduler.shutdownNow();
            try {
                if (!scheduler.awaitTermination(2, TimeUnit.SECONDS)) {
                    logger.warn("Watchdog scheduler did not terminate within 2 seconds.");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        try {
            if (consumer != null) consumer.close();
            if (session != null) session.close();
            if (connection != null) connection.close();
        } catch (JMSException e) {
            logger.warn("Error while closing JMS resources: {}", e.getMessage());
        }
        logger.info("IntersectionWatchdogService stopped cleanly.");
    }
}
