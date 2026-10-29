package co.wethinkcode.trafficflow;

import io.javalin.Javalin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class IntersectionWatchdogApp {
    private static final Logger logger = LoggerFactory.getLogger(IntersectionWatchdogApp.class);
    private static IntersectionWatchdogService watchdogService;
    private static Javalin app;

    public static void main(String[] args) {
        int port = Integer.parseInt(System.getProperty("server.port", System.getenv().getOrDefault("PORT", "7024")));
        app = Javalin.create(config -> {
            config.showJavalinBanner = false;
        }).start(port);

        app.get("/health", ctx -> ctx.result("OK"));

        app.get("/alerts", ctx -> {
            ctx.contentType("application/json");
            if (watchdogService != null) {
                if (watchdogService.hasAlerts()) {
                    ctx.status(503);
                } else {
                    ctx.status(200);
                }
                ctx.result(watchdogService.getStatusSummaryJson());
            } else {
                ctx.status(500).result("{\"error\":\"Watchdog service not initialized\"}");
            }
        });

        app.get("/status", ctx -> {
            ctx.contentType("application/json");
            if (watchdogService != null) {
                ctx.result(watchdogService.getStatusSummaryJson());
            } else {
                ctx.result("{\"service\":\"intersection-watchdog\",\"status\":\"DOWN\"}");
            }
        });

        // Initialize and start the ActiveMQ watchdog consumer
        watchdogService = new IntersectionWatchdogService();

        // Seed with default known intersections from legacy dataset
        watchdogService.registerKnownIntersection("INT-1001", "Downtown");
        watchdogService.registerKnownIntersection("INT-1002", "Midtown");
        watchdogService.registerKnownIntersection("INT-1005", "Downtown");

        try {
            watchdogService.start();
        } catch (Exception e) {
            logger.error("Failed to start IntersectionWatchdogService: {}", e.getMessage(), e);
        }

        Runtime.getRuntime().addShutdownHook(new Thread(IntersectionWatchdogApp::stop, "IntersectionWatchdog-ShutdownHook"));
        logger.info("IntersectionWatchdogApp started on port {}", port);
    }

    public static synchronized void stop() {
        logger.info("Stopping IntersectionWatchdogApp...");
        if (watchdogService != null) {
            try {
                watchdogService.close();
            } catch (Exception e) {
                logger.warn("Exception closing watchdog: {}", e.getMessage());
            }
            watchdogService = null;
        }
        if (app != null) {
            try {
                app.stop();
            } catch (Exception e) {
                logger.warn("Exception stopping Javalin app: {}", e.getMessage());
            }
            app = null;
        }
        logger.info("IntersectionWatchdogApp stopped.");
    }

    public static IntersectionWatchdogService getWatchdogService() {
        return watchdogService;
    }
}
