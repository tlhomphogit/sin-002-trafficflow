# IntersectionServiceApp

## Overview

Intended role: validate intersection/district names as the source of truth.
The current implementation provides the heartbeat producer described below;
canonical lookup/validation is still incomplete.

Part of the [TrafficFlow](../README.md) project. Independent Maven module, no
parent pom.

MQ: this service publishes to the ActiveMQ queue `intersection-heartbeat-queue` — see [`../common/`](../common). Broker URL and queue name come from the common `co.wethinkcode.trafficflow.mq.MqConfig` class alongside it in this module.

## Implementation Status (Stage 4 Queue Integration)

- **Implemented:**
  - ActiveMQ JMS Producer publishing periodic status heartbeats to `intersection-heartbeat-queue`.
  - Configurable interval (default 2000 ms), overridable via `-Dheartbeat.interval.ms=...` or `HEARTBEAT_INTERVAL_MS`.
  - Configurable ActiveMQ broker URL, defaulting to `MqConfig.BROKER_URL` (`tcp://localhost:61616`), overridable via `-Dactivemq.broker.url=...` or `ACTIVEMQ_BROKER_URL`.
  - Demo intersection dataset matching `intersections-legacy.csv` (`INT-1001` Downtown, `INT-1002` Midtown, `INT-1005` Downtown).
  - Health check endpoint `GET /health` (returns `OK`) and status endpoint `GET /status`.
  - Manual heartbeat trigger endpoint `POST /heartbeat/trigger`.
  - Clean shutdown hooks to close JMS session, producer, and connection.
  - Automated JUnit 5 unit tests for message serialization/validation and producer defaults, plus a real-broker producer integration test.

- **Incomplete / Remaining Work:**
  - Stage 1 CSV ingestion integration (`intersections-legacy.csv` parsing from ingestion service).
  - Stage 2 canonical intersection validation lookup (`GET /intersections/{id}`).

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

`timestamp` is Unix epoch time in milliseconds. The producer payload uses
`status: "UP"`; the watchdog maintains its own evaluated health state,
`HEALTHY` or `ALERT_MISSED_HEARTBEAT`.

## Build

Run the commands in the Build, Run, and Test sections from the
`intersection-service/` directory.

```bash
mvn package
```
Builds the shaded executable JAR at `target/intersection-service.jar`.

## Run

Start the ActiveMQ broker without changing the current directory:

```bash
docker compose -f ../common/docker-compose.yml up -d
```

Then start the producer:

```bash
java -jar target/intersection-service.jar
```
Listens on port `7021`.

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