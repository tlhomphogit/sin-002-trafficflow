package co.wethinkcode.trafficflow;

import java.util.Objects;

/**
 * Domain message representing an intersection status heartbeat received over ActiveMQ.
 */
public class HeartbeatMessage {
    private String intersectionId;
    private String district;
    private String status;
    private long timestamp;

    public HeartbeatMessage() {
    }

    public HeartbeatMessage(String intersectionId, String district, String status, long timestamp) {
        this.intersectionId = intersectionId;
        this.district = district;
        this.status = status;
        this.timestamp = timestamp;
    }

    public String getIntersectionId() {
        return intersectionId;
    }

    public void setIntersectionId(String intersectionId) {
        this.intersectionId = intersectionId;
    }

    public String getDistrict() {
        return district;
    }

    public void setDistrict(String district) {
        this.district = district;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public boolean isValid() {
        return intersectionId != null && !intersectionId.trim().isEmpty() &&
                timestamp > 0 &&
                "UP".equalsIgnoreCase(status);
    }

    public String toJson() {
        return String.format(
            "{\"intersectionId\":\"%s\",\"district\":\"%s\",\"status\":\"%s\",\"timestamp\":%d}",
            escapeJson(intersectionId),
            escapeJson(district),
            escapeJson(status),
            timestamp
        );
    }

    public static HeartbeatMessage fromJson(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        String id = extractField(json, "intersectionId");
        String dist = extractField(json, "district");
        String stat = extractField(json, "status");
        long ts = extractLongField(json, "timestamp");
        return new HeartbeatMessage(id, dist, stat, ts);
    }

    private static String escapeJson(String s) {
        return s == null ? "" : s.replace("\"", "\\\"");
    }

    private static String extractField(String json, String field) {
        String key = "\"" + field + "\"";
        int keyIndex = json.indexOf(key);
        if (keyIndex == -1) return null;
        int colon = json.indexOf(":", keyIndex + key.length());
        if (colon == -1) return null;
        int startQuote = json.indexOf("\"", colon);
        if (startQuote == -1) return null;
        int endQuote = json.indexOf("\"", startQuote + 1);
        if (endQuote == -1) return null;
        return json.substring(startQuote + 1, endQuote);
    }

    private static long extractLongField(String json, String field) {
        String key = "\"" + field + "\"";
        int keyIndex = json.indexOf(key);
        if (keyIndex == -1) return 0L;
        int colon = json.indexOf(":", keyIndex + key.length());
        if (colon == -1) return 0L;
        int start = colon + 1;
        while (start < json.length() && Character.isWhitespace(json.charAt(start))) {
            start++;
        }
        int end = start;
        while (end < json.length() && (Character.isDigit(json.charAt(end)) || json.charAt(end) == '-')) {
            end++;
        }
        if (start < end) {
            try {
                return Long.parseLong(json.substring(start, end));
            } catch (NumberFormatException ignored) {
            }
        }
        return 0L;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof HeartbeatMessage that)) return false;
        return timestamp == that.timestamp &&
                Objects.equals(intersectionId, that.intersectionId) &&
                Objects.equals(district, that.district) &&
                Objects.equals(status, that.status);
    }

    @Override
    public int hashCode() {
        return Objects.hash(intersectionId, district, status, timestamp);
    }

    @Override
    public String toString() {
        return toJson();
    }
}
