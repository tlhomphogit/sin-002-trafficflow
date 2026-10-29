# IntersectionWatchdogApp

## Overview

Cries for help if the Intersection Service crashes, since routes can no longer be validated.

Part of the [TrafficFlow](../README.md) project — its alerting service.
Independent Maven module, no parent pom.

MQ: this service subscribes to the ActiveMQ queue `intersection-heartbeat-queue` — see [`../common/`](../common). Broker URL and queue name come from the common `co.wethinkcode.trafficflow.mq.MqConfig` class alongside it in this module. Mechanism: watch for missed heartbeats from `intersection-service` and raise an alert.

## Implementation Status (Stage 4 Queue Integration)

- **Implemented:**
  - ActiveMQ JMS Consumer listening on `intersection-heartbeat-queue` via asynchronous `MessageListener`.
  - Dynamic discovery and tracking of per-intersection heartbeat arrival and transit latency.
  - Independent periodic timeout checker running on a separate thread (default 1000 ms check interval).
  - Configurable missing heartbeat timeout threshold (default 5000 ms), overridable via `-Dwatchdog.timeout.ms=...` or `WATCHDOG_TIMEOUT_MS`.
  - Configurable broker URL, defaulting to `MqConfig.BROKER_URL` (`tcp://localhost:61616`).
  - Clear structured logging:
    - Normal receipt: `[WATCHDOG-RECEIVE] Heartbeat received for <id> (<district>) - latency: <ms>`
    - Timeout alert: `[WATCHDOG-ALERT] Missing heartbeat for intersection <id> (<district>)! Elapsed: <ms> (timeout threshold: <ms>)`
    - Recovery: `[WATCHDOG-RECOVERY] Heartbeat resumed for intersection <id> (<district>)! State restored to HEALTHY.`
  - Endpoints:
    - `GET /health` -> `200 OK`
    - `GET /status` -> Current status JSON of all monitored intersections.
    - `GET /alerts` -> Returns `200 OK` when all monitored intersections are healthy, or `503 Service Unavailable` when one or more intersections have missed heartbeats.
  - Clean shutdown hooks to close JMS consumer, session, connection, and executor.
  - Automated JUnit 5 unit and integration tests (asserting timeout detection, recovery, and JMS message round-trip).

- **Incomplete / Remaining Work:**
  - Dead-letter queue (DLQ) specific alerting policies (currently relies on heartbeat timeout detection).

## Queue Contract & Message Format

- **Queue Name:** `intersection-heartbeat-queue`
- **JMS Type:** `TextMessage` (JSON payload)
- **Message Format:**
```json
{
  "intersectionId": "INT-1001",
  "district": "Downtown",
  "status": "UP",
  "timestamp": 1793304565987
}
```

## Build

```bash
mvn package
```
Builds the shaded executable JAR at `target/intersection-watchdog.jar`.

## Run

Prerequisites: ActiveMQ broker must be running (`cd ../common && docker compose up -d`).

```bash
java -jar target/intersection-watchdog.jar
```
Listens on port `7024`.

## Test

Run automated unit tests (no broker needed):
```bash
mvn test
```

Run integration tests against a live broker (`tcp://localhost:61616`):
```bash
mvn failsafe:integration-test failsafe:verify
```

## Demonstration Steps (Normal Operation, Timeout, and Recovery)

1. **Start the Broker:**
   ```bash
   cd common && docker compose up -d
   ```

2. **Start the Watchdog:**
   ```bash
   java -jar intersection-watchdog/target/intersection-watchdog.jar
   ```

3. **Start the Intersection Service:**
   ```bash
   java -jar intersection-service/target/intersection-service.jar
   ```
   Observe in the watchdog logs:
   `[WATCHDOG-RECEIVE] Heartbeat received for INT-1001 (Downtown) - latency: 5ms`
   And query `/alerts`:
   ```bash
   curl -i http://localhost:7024/alerts
   # Returns HTTP 200 OK with alertCount: 0
   ```

4. **Simulate Crash / Stop Producer:**
   Kill the `intersection-service` process.
   Wait 5 seconds. Observe in the watchdog logs:
   `[WATCHDOG-ALERT] Missing heartbeat for intersection INT-1001 (Downtown)! Elapsed: 5918ms (timeout threshold: 5000ms)`
   And query `/alerts`:
   ```bash
   curl -i http://localhost:7024/alerts
   # Returns HTTP 503 Service Unavailable with alertCount: 3 and inAlert: true
   ```

5. **Simulate Recovery / Restart Producer:**
   Restart `intersection-service`:
   ```bash
   java -jar intersection-service/target/intersection-service.jar
   ```
   Observe in the watchdog logs:
   `[WATCHDOG-RECOVERY] Heartbeat resumed for intersection INT-1001 (Downtown)! State restored to HEALTHY.`
   And query `/alerts`:
   ```bash
   curl -i http://localhost:7024/alerts
   # Returns HTTP 200 OK with alertCount: 0 and inAlert: false
   ```
