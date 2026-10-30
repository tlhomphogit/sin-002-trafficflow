# IntersectionWatchdogApp

## Overview

Detects missing heartbeats from the Intersection Service and raises timeout
alerts. Canonical route validation remains part of the unfinished domain REST
stages.

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

`timestamp` is Unix epoch time in milliseconds. A valid heartbeat has a
non-blank `intersectionId`, a positive timestamp, and a status of `UP`
(case-insensitive). Messages failing these checks are discarded without
refreshing heartbeat state or clearing alerts.

The payload's `UP` status represents the producer's operational state. The
watchdog's evaluated intersection state is `HEALTHY` during normal operation
and after recovery, or `ALERT_MISSED_HEARTBEAT` after a timeout.

## Build

Run the commands in the Build, Run, and Test sections from the
`intersection-watchdog/` directory. The demonstration section below uses paths
relative to the repository root instead.

```bash
mvn package
```
Builds the shaded executable JAR at `target/intersection-watchdog.jar`.

## Run

Start the ActiveMQ broker without changing the current directory:

```bash
docker compose -f ../common/docker-compose.yml up -d
```

Then start the watchdog:

```bash
java -jar target/intersection-watchdog.jar
```
Listens on port `7024`.

## Test

Run automated unit tests (no broker needed):
```bash
mvn test
```

For a clean build, unit tests, and the integration test, start the real broker
first, then run:

```bash
mvn clean verify
```

This compiles the source and tests, runs the unit tests, builds the shaded JAR,
and runs and verifies the Failsafe integration test against
`tcp://localhost:61616`. The integration test uses a separate test queue.

## Demonstration Steps (Normal Operation, Timeout, and Recovery)

Run all commands in this section from the repository root. Open separate
terminals at that root for the watchdog, producer, and endpoint checks.

Build both executable JARs if they are not already built:

```bash
mvn -f intersection-service/pom.xml package
mvn -f intersection-watchdog/pom.xml package
```

1. **Start the Broker:**
   ```bash
   docker compose -f common/docker-compose.yml up -d
   ```

2. **Start the Watchdog:**
   ```bash
   java -jar intersection-watchdog/target/intersection-watchdog.jar
   ```

3. **Start the Intersection Service:**
   ```bash
   java -jar intersection-service/target/intersection-service.jar
   ```
   Wait for `[WATCHDOG-RECEIVE]` messages in the watchdog logs. In the endpoint
   check terminal, query `/alerts`:
   ```bash
   curl -i http://localhost:7024/alerts
   # Expected: HTTP 200, monitoredCount: 3, alertCount: 0,
   # and each intersection's status: "HEALTHY".
   ```

4. **Simulate Crash / Stop Producer:**
   Press `Ctrl+C` in the producer terminal. Leave the watchdog running.
   Allow the default 5000 ms timeout and the next scheduled 1000 ms check to
   elapse. In the endpoint check terminal:
   ```bash
   sleep 7
   curl -i http://localhost:7024/alerts
   # Expected: HTTP 503, alertCount: 3, inAlert: true,
   # and intersection status: "ALERT_MISSED_HEARTBEAT".
   ```
   The watchdog logs a `[WATCHDOG-ALERT]` message for each timed-out intersection.

5. **Simulate Recovery / Restart Producer:**
   Restart `intersection-service` in the producer terminal:
   ```bash
   java -jar intersection-service/target/intersection-service.jar
   ```
   Wait for valid heartbeats and `[WATCHDOG-RECOVERY]` messages in the watchdog
   logs, then query `/alerts` from the endpoint check terminal:
   ```bash
   curl -i http://localhost:7024/alerts
   # Expected: HTTP 200, alertCount: 0, inAlert: false,
   # and each intersection's status: "HEALTHY".
   ```

The `/health` endpoint can remain `OK` while `/alerts` returns `503`: the
watchdog process is alive while a monitored intersection has missed heartbeats.