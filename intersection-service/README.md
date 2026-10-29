# IntersectionServiceApp

## Overview

Validates intersection/district names (source of truth).

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
  - Automated JUnit 5 unit tests for JSON serialization and producer lifecycle.

- **Incomplete / Remaining Work:**
  - Stage 1 CSV ingestion integration (`intersections-legacy.csv` parsing from ingestion service).
  - Stage 2 REST endpoints for full canonical intersection CRUD / validation lookup (`GET /intersections/{id}`).

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
Builds the shaded executable JAR at `target/intersection-service.jar`.

## Run

Prerequisites: ActiveMQ broker must be running (`cd ../common && docker compose up -d`).

```bash
java -jar target/intersection-service.jar
```
Listens on port `7021`.

## Test

Run automated unit tests (no broker needed):
```bash
mvn test
```

Run integration tests against a live broker (`tcp://localhost:61616`):
```bash
mvn failsafe:integration-test failsafe:verify
```
