package co.wethinkcode.trafficflow;

import io.javalin.Javalin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class IntersectionServiceApp {
    private static final Logger logger = LoggerFactory.getLogger(IntersectionServiceApp.class);
    private static HeartbeatProducer producer;
    private static Javalin app;

    public static void main(String[] args) {
        int port = Integer.parseInt(System.getProperty("server.port", System.getenv().getOrDefault("PORT", "7021")));
        app = Javalin.create(config -> {
            config.showJavalinBanner = false;
        }).start(port);

        app.get("/health", ctx -> ctx.result("OK"));

        app.get("/status", ctx -> {
            boolean active = producer != null && producer.isRunning();
            int count = producer != null ? producer.getIntersections().size() : 0;
            ctx.contentType("application/json");
            ctx.result(String.format(
                "{\"service\":\"intersection-service\",\"status\":\"UP\",\"port\":%d,\"heartbeatActive\":%b,\"configuredIntersections\":%d}",
                port, active, count
            ));
        });

        app.post("/heartbeat/trigger", ctx -> {
            if (producer != null && producer.isRunning()) {
                producer.sendHeartbeats();
                ctx.result("Heartbeats sent manually.");
            } else {
                ctx.status(503).result("Heartbeat producer is not running.");
            }
        });

        // Initialize and start the ActiveMQ heartbeat producer
        producer = new HeartbeatProducer();
        try {
            producer.start();
        } catch (Exception e) {
            logger.error("Failed to start HeartbeatProducer: {}", e.getMessage(), e);
        }

        Runtime.getRuntime().addShutdownHook(new Thread(IntersectionServiceApp::stop, "IntersectionService-ShutdownHook"));
        logger.info("IntersectionServiceApp started on port {}", port);
    }

    public static synchronized void stop() {
        logger.info("Stopping IntersectionServiceApp...");
        if (producer != null) {
            try {
                producer.close();
            } catch (Exception e) {
                logger.warn("Exception closing producer: {}", e.getMessage());
            }
            producer = null;
        }
        if (app != null) {
            try {
                app.stop();
            } catch (Exception e) {
                logger.warn("Exception stopping Javalin app: {}", e.getMessage());
            }
            app = null;
        }
        logger.info("IntersectionServiceApp stopped.");
    }

    public static HeartbeatProducer getProducer() {
        return producer;
    }
}
