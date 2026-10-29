package co.wethinkcode.trafficflow;

/**
 * Tracks the live health and heartbeat recency of an individual intersection.
 */
public class IntersectionTracker {
    private final String intersectionId;
    private String district;
    private long lastSeenTimestamp;
    private boolean inAlert;
    private String status;

    public IntersectionTracker(String intersectionId, String district, long lastSeenTimestamp) {
        this.intersectionId = intersectionId;
        this.district = district;
        this.lastSeenTimestamp = lastSeenTimestamp;
        this.inAlert = false;
        this.status = "HEALTHY";
    }

    public String getIntersectionId() {
        return intersectionId;
    }

    public synchronized String getDistrict() {
        return district;
    }

    public synchronized void setDistrict(String district) {
        this.district = district;
    }

    public synchronized long getLastSeenTimestamp() {
        return lastSeenTimestamp;
    }

    public synchronized void setLastSeenTimestamp(long lastSeenTimestamp) {
        this.lastSeenTimestamp = lastSeenTimestamp;
    }

    public synchronized void updateHeartbeat(String district, String status, long timestamp) {
        if (district != null && !district.isBlank()) {
            this.district = district;
        }
        this.status = "HEALTHY";
        this.lastSeenTimestamp = Math.max(this.lastSeenTimestamp, timestamp);
    }

    public synchronized boolean isInAlert() {
        return inAlert;
    }

    public synchronized void setInAlert(boolean inAlert) {
        this.inAlert = inAlert;
    }

    public synchronized String getStatus() {
        return status;
    }

    public synchronized void setStatus(String status) {
        this.status = status;
    }

    public synchronized String toJson() {
        return String.format(
            "{\"intersectionId\":\"%s\",\"district\":\"%s\",\"lastSeenTimestamp\":%d,\"inAlert\":%b,\"status\":\"%s\"}",
            intersectionId,
            (district == null ? "" : district),
            lastSeenTimestamp,
            inAlert,
            status
        );
    }
}
